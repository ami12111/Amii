package ami.ami.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.concurrent.ThreadLocalRandom;

/**
 * HitParticle - шейдерные частицы при ударе по цели.
 *
 * <p>Один удар превращается в короткий «взрыв» из семи видов частиц:
 * <ul>
 *   <li>вспышка в точке попадания: бликующее ядро, анаморфный луч, острые лучи;</li>
 *   <li>призматическая ударная волна с тонкими энергетическими линиями;</li>
 *   <li>искры-кометы: вытянутые по скорости, с мерцающим хвостом и бусинами;</li>
 *   <li>граненые кристаллические осколки: кувыркаются, бликуют и сгорают;</li>
 *   <li>тлеющие огоньки с линзовым бликом;</li>
 *   <li>клубы энергетического дыма;</li>
 *   <li>для критов и добиваний - вращающаяся звезда с пунктирным ореолом.</li>
 * </ul>
 *
 * <p>Как это устроено. Весь внешний вид частиц живёт в
 * {@code assets/ami/shaders/core/hit_particle.fsh}, а Java только считает их движение
 * и рисует по одному билборду на частицу, передавая в шейдер прогресс жизни, цвет и
 * случайное зерно через атрибуты вершин. Позиция каждой частицы выражена в замкнутой
 * форме (сопротивление + гравитация), поэтому движение плавное при любом FPS.
 *
 * <p>Удар ловится через {@link AttackEntityCallback}, а попадание подтверждается по
 * {@code hurtTime} цели (или по её смерти): так частицы не появляются на ударах,
 * которые не нанесли урона из-за неуязвимости после предыдущего попадания.
 *
 * <p>Подключение: один раз вызвать {@link #init()} из ClientModInitializer.
 */
public final class HitParticle {

    /** Должен совпадать с папкой в assets/ (id мода). */
    public static final String MOD_ID = "ami";

    public static final int DEFAULT_PRIMARY = 0xFFC26B;
    public static final int DEFAULT_ACCENT = 0xB06CFF;
    public static final int DEFAULT_CRIT = 0xFF5C8A;

    // ---------------------------------------------------------------- settings
    public static boolean enabled = true;
    public static boolean targetPlayers = true;
    public static boolean targetMonsters = true;
    /** Животные, жители, големы и прочие мирные существа. */
    public static boolean targetAnimals = false;
    /** Криты и добивающие удары получают усиленный взрыв со звездой. */
    public static boolean critBurst = true;
    public static boolean shockwave = true;
    public static boolean shards = true;
    public static boolean rainbow = false;
    /** 0xRRGGBB: вспышка, волна и основная часть искр. */
    public static int primaryColor = DEFAULT_PRIMARY;
    /** 0xRRGGBB: осколки, дым и часть искр. */
    public static int accentColor = DEFAULT_ACCENT;
    /** 0xRRGGBB: звезда и волна при крите. */
    public static int critColor = DEFAULT_CRIT;
    /** Множитель количества частиц. */
    public static float density = 1.0f;
    /** Множитель размера. */
    public static float scale = 1.0f;
    /** Множитель длительности. */
    public static float duration = 1.0f;

    // ------------------------------------------------------------------ consts
    private static final int KIND_SPARK = 0;
    private static final int KIND_SHARD = 1;
    private static final int KIND_EMBER = 2;
    private static final int KIND_WISP = 3;
    private static final int KIND_FLASH = 4;
    private static final int KIND_RING = 5;
    private static final int KIND_STAR = 6;

    /** Сколько разных «зёрен» понимает шейдер (по ним он варьирует форму частиц). */
    private static final int SEEDS = 64;
    /** Сколько тиков ждём подтверждения урона после удара (ping + серверный тик). */
    private static final int PENDING_TICKS = 20;
    private static final int MAX_PENDING = 16;
    private static final int MAX_BURSTS = 24;
    /** 2400 квадов * 96 байт < 256 КиБ, размер буфера ниже. */
    private static final int MAX_QUADS = 2400;
    private static final float MAX_DISTANCE_SQ = 96f * 96f;
    private static final float TAU = (float) (Math.PI * 2.0);

    // ---------------------------------------------------------------- pipeline
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of(MOD_ID, "pipeline/hit_particle"))
                    .withVertexShader(Identifier.of(MOD_ID, "core/hit_particle"))
                    .withFragmentShader(Identifier.of(MOD_ID, "core/hit_particle"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .withBlend(BlendFunction.ADDITIVE)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final BufferAllocator ALLOCATOR = new BufferAllocator(1 << 18);
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    // ------------------------------------------------------------------- state
    // Всё живёт в главном потоке клиента (тик и отрисовка мира), поэтому обычные списки.
    private static final ArrayList<Burst> BURSTS = new ArrayList<>();
    private static final ArrayList<Pending> PENDING = new ArrayList<>();
    private static boolean initialized;
    private static boolean previewCrit;

    // оси камеры в мировых координатах, обновляются раз за кадр
    private static float rX, rY, rZ;
    private static float uX, uY, uZ;
    private static float bX, bY, bZ;

    private HitParticle() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            onAttack(player, entity);
            return ActionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(HitParticle::onTick);
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(HitParticle::onWorldRender);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ALLOCATOR.close());
    }

    public static void toggle() {
        enabled = !enabled;
        if (!enabled) {
            BURSTS.clear();
            PENDING.clear();
        }
    }

    /** Показывает эффект перед игроком; каждый второй вызов - вариант с критом. */
    public static void spawnPreview() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return;
        }
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f);
        previewCrit = !previewCrit;
        spawnBurst(eye.x + look.x * 3.4, eye.y + look.y * 3.4 - 0.15, eye.z + look.z * 3.4,
                (float) -look.x, (float) -look.y, (float) -look.z,
                (float) look.x, (float) look.y, (float) look.z,
                previewCrit, false);
    }

    // ------------------------------------------------------------- hit tracking
    /** Запоминает удар. Эффект появится, когда придёт подтверждение урона. */
    private static void onAttack(PlayerEntity player, Entity target) {
        if (!enabled || !(player instanceof ClientPlayerEntity)
                || !(target instanceof LivingEntity living) || !living.isAlive() || !isEnemy(living)) {
            return;
        }

        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d hit = surfaceHit(eye, look, living);

        // нормаль - от точки попадания к атакующему: искры летят обратно в лицо
        double nx = eye.x - hit.x;
        double ny = eye.y - hit.y;
        double nz = eye.z - hit.z;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0e-4) {
            nx = -look.x;
            ny = -look.y;
            nz = -look.z;
        } else {
            nx /= len;
            ny /= len;
            nz /= len;
        }

        // условия ванильного крита: падение, не на земле, не в воде/на лестнице/в транспорте, не в спринте
        boolean crit = player.fallDistance > 0.0f && !player.isOnGround() && !player.isClimbing()
                && !player.isTouchingWater() && !player.hasVehicle() && !player.isSprinting()
                && player.getAttackCooldownProgress(0.5f) > 0.9f;

        PENDING.removeIf(p -> p.entity == living);
        while (PENDING.size() >= MAX_PENDING) {
            PENDING.remove(0);
        }
        PENDING.add(new Pending(living,
                hit.x - living.getX(), hit.y - living.getY(), hit.z - living.getZ(),
                (float) nx, (float) ny, (float) nz,
                (float) look.x, (float) look.y, (float) look.z,
                crit, living.hurtTime));
    }

    private static void onTick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) {
            PENDING.clear();
            BURSTS.clear();
            return;
        }
        if (!enabled) {
            PENDING.clear();
            return;
        }

        for (int i = PENDING.size() - 1; i >= 0; i--) {
            Pending p = PENDING.get(i);
            p.age++;
            LivingEntity e = p.entity;

            // hurtTime подскакивает до максимума в тот тик, когда пришёл урон,
            // а потом каждый тик убывает: рост значения - это свежее попадание
            int hurt = e.hurtTime;
            boolean fresh = hurt > p.lastHurt;
            boolean killed = e.getHealth() <= 0.0f;
            p.lastHurt = hurt;

            // цель пропала из мира, не умерев (выгрузилась, ушла из зоны прорисовки)
            if (e.isRemoved() && !killed) {
                PENDING.remove(i);
                continue;
            }

            if (fresh || killed) {
                PENDING.remove(i);
                spawnBurst(e.getX() + p.dx, e.getY() + p.dy, e.getZ() + p.dz,
                        p.nx, p.ny, p.nz, p.fx, p.fy, p.fz, p.crit, killed);
            } else if (p.age > PENDING_TICKS) {
                PENDING.remove(i);
            }
        }
    }

    private static boolean isEnemy(LivingEntity entity) {
        if (entity instanceof PlayerEntity) {
            return targetPlayers && !entity.isSpectator();
        }
        if (entity instanceof Monster) {
            return targetMonsters;
        }
        return targetAnimals;
    }

    /** Точка на хитбоксе, куда пришёлся удар: пересечение взгляда с боксом, иначе ближайшая точка. */
    private static Vec3d surfaceHit(Vec3d eye, Vec3d look, LivingEntity target) {
        Box box = target.getBoundingBox().expand(target.getTargetingMargin());
        if (!box.contains(eye)) {
            Optional<Vec3d> hit = box.raycast(eye, eye.add(look.multiply(8.0)));
            if (hit.isPresent()) {
                return hit.get();
            }
        }
        double cx = Math.max(box.minX, Math.min(box.maxX, eye.x));
        double cy = Math.max(box.minY, Math.min(box.maxY, eye.y));
        double cz = Math.max(box.minZ, Math.min(box.maxZ, eye.z));
        if (box.contains(eye)) {
            // вплотную или внутри: берём середину тела
            cx = (box.minX + box.maxX) * 0.5;
            cy = (box.minY + box.maxY) * 0.5;
            cz = (box.minZ + box.maxZ) * 0.5;
        }
        return new Vec3d(cx, cy, cz);
    }

    // ------------------------------------------------------------------ spawning
    /**
     * Создаёт «взрыв».
     *
     * @param n нормаль от точки удара к атакующему (единичная)
     * @param f направление удара, от атакующего в цель (единичное)
     */
    private static void spawnBurst(double x, double y, double z,
                                   float nx, float ny, float nz,
                                   float fx, float fy, float fz,
                                   boolean crit, boolean kill) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        float[] n = {nx, ny, nz};
        float[] f = {fx, fy, fz};

        boolean big = critBurst && (crit || kill);
        float power = big ? 1.35f : 1.0f;
        float dens = Math.max(0.1f, density);
        float sz = Math.max(0.1f, scale);
        float spd = (float) Math.sqrt(sz);
        float dur = Math.max(0.2f, duration);

        // палитра: три цвета, в радужном режиме - один плавно плывущий оттенок
        double hue = (System.nanoTime() / 2_500_000_000.0) % 1.0;
        float[] c1 = rainbow ? hsvToRgb(hue, 0.62f, 1.0f) : unpack(primaryColor);
        float[] c2 = rainbow ? hsvToRgb(hue + 0.16, 0.62f, 1.0f) : unpack(accentColor);
        float[] cc = rainbow ? hsvToRgb(hue + 0.50, 0.55f, 1.0f) : unpack(critColor);

        ArrayList<Particle> list = new ArrayList<>(96);

        // --- вспышка в точке удара: чуть выдвинута к камере, чтобы тело цели её не перекрывало
        Particle flash = new Particle(KIND_FLASH, rnd.nextInt(SEEDS));
        flash.size = 0.95f * power * sz;
        flash.growth = 0.25f;
        flash.life = 0.32f * dur;
        flash.pull = 0.32f;
        flash.setColor(big ? mix(c1, cc, 0.45f) : c1);
        list.add(flash);

        // --- ударная волна
        if (shockwave) {
            Particle ring = new Particle(KIND_RING, rnd.nextInt(SEEDS));
            ring.size = 1.05f * power * sz;
            ring.life = 0.44f * dur;
            ring.pull = 0.22f;
            ring.spin0 = rnd.nextFloat() * TAU;
            ring.setColor(big ? cc : c1);
            list.add(ring);

            if (big) {
                Particle outer = new Particle(KIND_RING, rnd.nextInt(SEEDS));
                outer.size = 1.55f * sz;
                outer.life = 0.52f * dur;
                outer.delay = 0.07f * dur;
                outer.pull = 0.20f;
                outer.spin0 = rnd.nextFloat() * TAU;
                outer.setColor(c2);
                list.add(outer);
            }
        }

        // --- звезда при крите и добивании
        if (big) {
            Particle star = new Particle(KIND_STAR, rnd.nextInt(SEEDS));
            star.size = 1.40f * sz;
            star.life = 0.62f * dur;
            star.pull = 0.36f;
            star.setColor(crit ? cc : c2);
            list.add(star);
        }

        // --- энергетический дым
        int wisps = count(3.0f * dens);
        for (int i = 0; i < wisps; i++) {
            Particle p = new Particle(KIND_WISP, rnd.nextInt(SEEDS));
            float[] d = cone(n, 1.1f, rnd);
            float v = (0.5f + 0.8f * rnd.nextFloat()) * spd;
            p.setVelocity(d[0] * v, d[1] * v, d[2] * v);
            p.drag = 2.2f;
            p.gravity = -0.3f;
            p.size = (0.28f + 0.17f * rnd.nextFloat()) * sz;
            p.growth = 1.4f;
            p.life = (0.55f + 0.30f * rnd.nextFloat()) * dur;
            p.delay = rnd.nextFloat() * 0.04f;
            p.spin0 = rnd.nextFloat() * TAU;
            p.spin = (rnd.nextFloat() - 0.5f) * 2.4f;
            p.setColor(gradient(c1, c2, 0.4f + 0.6f * rnd.nextFloat(), rainbow, hue));
            list.add(p);
        }

        // --- тлеющие огоньки
        int embers = count(11.0f * power * dens);
        for (int i = 0; i < embers; i++) {
            Particle p = new Particle(KIND_EMBER, rnd.nextInt(SEEDS));
            float[] d = cone(n, 1.7f, rnd);
            float v = (0.8f + 2.4f * rnd.nextFloat()) * spd * power;
            p.setVelocity(d[0] * v, d[1] * v, d[2] * v);
            p.setOffset(rnd, 0.05f);
            p.drag = 2.6f;
            p.gravity = (rnd.nextFloat() - 0.6f) * 1.2f;
            p.size = (0.09f + 0.07f * rnd.nextFloat()) * sz;
            p.life = (0.70f + 0.60f * rnd.nextFloat()) * dur;
            p.delay = rnd.nextFloat() * 0.08f;
            p.setColor(gradient(c1, c2, rnd.nextFloat(), rainbow, hue));
            list.add(p);
        }

        // --- кристаллические осколки
        int pieces = shards ? count(7.0f * power * dens) : 0;
        for (int i = 0; i < pieces; i++) {
            Particle p = new Particle(KIND_SHARD, rnd.nextInt(SEEDS));
            float[] d = rnd.nextFloat() < 0.3f ? cone(f, 0.8f, rnd) : cone(n, 1.35f, rnd);
            float v = (1.8f + 3.2f * rnd.nextFloat()) * spd * power;
            p.setVelocity(d[0] * v, d[1] * v + 0.8f, d[2] * v);
            p.setOffset(rnd, 0.06f);
            p.drag = 1.2f + 0.8f * rnd.nextFloat();
            p.gravity = 11.0f + 5.0f * rnd.nextFloat();
            p.size = (0.05f + 0.035f * rnd.nextFloat()) * sz;
            p.life = (0.55f + 0.45f * rnd.nextFloat()) * dur;
            p.delay = rnd.nextFloat() * 0.03f;
            p.spin0 = rnd.nextFloat() * TAU;
            p.spin = (rnd.nextBoolean() ? 1f : -1f) * (5f + 9f * rnd.nextFloat());
            p.setColor(gradient(c1, c2, 0.55f + 0.45f * rnd.nextFloat(), rainbow, hue));
            list.add(p);
        }

        // --- искры-кометы
        int sparks = count(22.0f * power * dens);
        for (int i = 0; i < sparks; i++) {
            Particle p = new Particle(KIND_SPARK, rnd.nextInt(SEEDS));
            float[] d = rnd.nextFloat() < 0.22f ? cone(f, 0.55f, rnd) : cone(n, 1.1f, rnd);
            float v = (3.2f + 7.0f * (float) Math.pow(rnd.nextFloat(), 1.4)) * spd * power;
            p.setVelocity(d[0] * v, d[1] * v, d[2] * v);
            p.drag = 2.4f + 1.6f * rnd.nextFloat();
            p.gravity = 4.5f + 3.0f * rnd.nextFloat();
            p.size = (0.095f + 0.060f * rnd.nextFloat()) * sz;
            p.life = (0.34f + 0.46f * rnd.nextFloat()) * dur;
            p.delay = rnd.nextFloat() * 0.03f;
            if (big && rnd.nextFloat() < 0.3f) {
                p.setColor(cc);
            } else {
                float u = rnd.nextFloat();
                p.setColor(gradient(c1, c2, u * u, rainbow, hue));
            }
            list.add(p);
        }

        float end = 0f;
        for (Particle p : list) {
            end = Math.max(end, p.delay + p.life);
        }

        while (BURSTS.size() >= MAX_BURSTS) {
            BURSTS.remove(0);
        }
        BURSTS.add(new Burst(x, y, z, System.nanoTime(), list.toArray(new Particle[0]), end));
    }

    private static int count(float v) {
        return Math.max(0, Math.round(v));
    }

    // ---------------------------------------------------------------- rendering
    private static void onWorldRender(WorldRenderContext context) {
        if (!enabled || BURSTS.isEmpty()) {
            return;
        }

        long now = System.nanoTime();
        BURSTS.removeIf(bu -> secondsSince(bu, now) >= bu.end);
        if (BURSTS.isEmpty()) {
            return;
        }

        // матрица на стеке несёт поворот камеры, поэтому её строки - это оси камеры
        // в мире: вправо, вверх и «назад» (к глазу). По ним строим билборды.
        Matrix4fc view = context.matrices().peek().getPositionMatrix();
        Vec3d cam = context.worldState().cameraRenderState.pos;
        rX = view.m00();
        rY = view.m10();
        rZ = view.m20();
        uX = view.m01();
        uY = view.m11();
        uZ = view.m21();
        bX = view.m02();
        bY = view.m12();
        bZ = view.m22();

        // пустой билдер завершить нельзя, поэтому заранее убеждаемся, что рисовать есть что
        if (countVisible(now, cam) == 0) {
            return;
        }

        BufferBuilder buffer = new BufferBuilder(ALLOCATOR, PIPELINE.getVertexFormatMode(), PIPELINE.getVertexFormat());
        int quads = 0;
        // новые взрывы важнее: если упёрлись в лимит, режем самые старые
        for (int i = BURSTS.size() - 1; i >= 0 && quads < MAX_QUADS; i--) {
            quads += emitBurst(buffer, view, BURSTS.get(i), now, cam, MAX_QUADS - quads);
        }

        BuiltBuffer built = buffer.end();
        draw(MinecraftClient.getInstance(), built);
    }

    private static int countVisible(long now, Vec3d cam) {
        int visible = 0;
        for (Burst bu : BURSTS) {
            if (tooFar(bu, cam)) {
                continue;
            }
            float age = secondsSince(bu, now);
            for (Particle p : bu.particles) {
                float s = age - p.delay;
                if (s >= 0f && s < p.life) {
                    visible++;
                }
            }
        }
        return visible;
    }

    private static boolean tooFar(Burst bu, Vec3d cam) {
        double dx = bu.x - cam.x;
        double dy = bu.y - cam.y;
        double dz = bu.z - cam.z;
        return dx * dx + dy * dy + dz * dz > MAX_DISTANCE_SQ;
    }

    private static int emitBurst(BufferBuilder b, Matrix4fc m, Burst bu, long now, Vec3d cam, int budget) {
        if (tooFar(bu, cam)) {
            return 0;
        }
        // координаты частиц считаем относительно камеры: так float не теряет точность вдали от нуля
        float ox = (float) (bu.x - cam.x);
        float oy = (float) (bu.y - cam.y);
        float oz = (float) (bu.z - cam.z);
        float age = secondsSince(bu, now);

        int quads = 0;
        for (Particle p : bu.particles) {
            if (quads >= budget) {
                break;
            }
            float s = age - p.delay;
            if (s < 0f || s >= p.life) {
                continue;
            }
            float t = s / p.life;

            // x(s) = x0 + v0 * (1 - e^-ks) / k + (g / k) * (s - (1 - e^-ks) / k): сопротивление и гравитация
            float k = p.drag;
            float ek = (float) Math.exp(-k * s);
            float f = (1f - ek) / k;
            float gk = p.gravity / k;
            float cx = ox + p.ox + p.vx * f;
            float cy = oy + p.oy + p.vy * f - gk * (s - f);
            float cz = oz + p.oz + p.vz * f;

            switch (p.kind) {
                case KIND_SPARK -> {
                    // скорость в плоскости экрана задаёт направление и длину хвоста
                    float vx = p.vx * ek;
                    float vy = p.vy * ek - gk * (1f - ek);
                    float vz = p.vz * ek;
                    float sx = vx * rX + vy * rY + vz * rZ;
                    float sy = vx * uX + vy * uY + vz * uZ;
                    float sp = (float) Math.sqrt(sx * sx + sy * sy);
                    float ax = 1f;
                    float ay = 0f;
                    if (sp > 1.0e-3f) {
                        ax = sx / sp;
                        ay = sy / sp;
                    }
                    float hw = p.size * (1f - 0.45f * t);
                    float hl = Math.min(0.6f, hw * 1.7f + sp * 0.022f);

                    // единичная ось вдоль движения и перпендикуляр к ней, обе в плоскости экрана
                    float ex = rX * ax + uX * ay;
                    float ey = rY * ax + uY * ay;
                    float ez = rZ * ax + uZ * ay;
                    float px = -rX * ay + uX * ax;
                    float py = -rY * ay + uY * ax;
                    float pz = -rZ * ay + uZ * ax;

                    // голова кометы (в шейдере это 0.72 по длине) должна лежать ровно на частице
                    quad(b, m, cx - ex * hl * 0.72f, cy - ey * hl * 0.72f, cz - ez * hl * 0.72f,
                            ex * hl, ey * hl, ez * hl, px * hw, py * hw, pz * hw, p, t);
                }
                case KIND_SHARD -> plane(b, m, cx, cy, cz, p.spin0 + p.spin * s,
                        p.size * (1f - 0.35f * t * t), p, t);
                case KIND_EMBER -> plane(b, m, cx, cy, cz, 0f,
                        p.size * (1f + 0.12f * (float) Math.sin(s * 30f + p.seed)), p, t);
                case KIND_WISP -> plane(b, m, cx, cy, cz, p.spin0 + p.spin * s,
                        p.size * (1f + p.growth * easeOutCubic(t)), p, t);
                default -> plane(b, m, cx + bX * p.pull, cy + bY * p.pull, cz + bZ * p.pull,
                        p.spin0, p.size * (1f + p.growth * t), p, t);
            }
            quads++;
        }
        return quads;
    }

    /** Квад, повёрнутый в плоскости экрана на ang радиан. */
    private static void plane(BufferBuilder b, Matrix4fc m, float cx, float cy, float cz,
                              float ang, float size, Particle p, float t) {
        float c = (float) Math.cos(ang) * size;
        float s = (float) Math.sin(ang) * size;
        quad(b, m, cx, cy, cz,
                rX * c + uX * s, rY * c + uY * s, rZ * c + uZ * s,
                -rX * s + uX * c, -rY * s + uY * c, -rZ * s + uZ * c, p, t);
    }

    /**
     * Квад с центром в (cx, cy, cz) и полуосями a и b. UV - локальные координаты [-1..1],
     * сдвинутые на целое число ячеек по 4 единицы: X = зерно частицы, Y = вид (читает шейдер).
     */
    private static void quad(BufferBuilder b, Matrix4fc m, float cx, float cy, float cz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             Particle p, float t) {
        float ou = 4f * p.seed;
        float ov = 4f * p.kind;
        vert(b, m, cx - ax - bx, cy - ay - by, cz - az - bz, -1f + ou, -1f + ov, p, t);
        vert(b, m, cx - ax + bx, cy - ay + by, cz - az + bz, -1f + ou, 1f + ov, p, t);
        vert(b, m, cx + ax + bx, cy + ay + by, cz + az + bz, 1f + ou, 1f + ov, p, t);
        vert(b, m, cx + ax - bx, cy + ay - by, cz + az - bz, 1f + ou, -1f + ov, p, t);
    }

    private static void vert(BufferBuilder b, Matrix4fc m, float x, float y, float z,
                             float u, float v, Particle p, float t) {
        b.vertex(m, x, y, z).texture(u, v).color(p.r, p.g, p.b, t);
    }

    private static void draw(MinecraftClient mc, BuiltBuffer built) {
        BuiltBuffer.DrawParameters params = built.getDrawParameters();

        GpuBuffer vertices = PIPELINE.getVertexFormat().uploadImmediateVertexBuffer(built.getBuffer());
        RenderSystem.ShapeIndexBuffer shape = RenderSystem.getSequentialBuffer(params.mode());
        GpuBuffer indices = shape.getIndexBuffer(params.indexCount());
        VertexFormat.IndexType indexType = shape.getIndexType();

        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        Framebuffer fb = mc.getFramebuffer();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> MOD_ID + " hit particles",
                fb.getColorAttachmentView(), OptionalInt.empty(),
                fb.getDepthAttachmentView(), OptionalDouble.empty())) {
            pass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indices, indexType);
            pass.drawIndexed(0, 0, params.indexCount(), 1);
        }

        built.close();
    }

    // ------------------------------------------------------------------ helpers
    private static float secondsSince(Burst bu, long now) {
        return (now - bu.startNs) / 1.0e9f;
    }

    private static float easeOutCubic(float x) {
        float y = 1f - (x < 0f ? 0f : Math.min(x, 1f));
        return 1f - y * y * y;
    }

    /** Случайное направление в конусе вокруг оси axis; spread - тангенс половины угла раскрытия. */
    private static float[] cone(float[] axis, float spread, ThreadLocalRandom rnd) {
        // любой вектор, не параллельный оси, даёт пару перпендикуляров
        float hx = Math.abs(axis[1]) > 0.92f ? 1f : 0f;
        float hy = hx == 1f ? 0f : 1f;
        float ux = axis[1] * 0f - axis[2] * hy;
        float uy = axis[2] * hx - axis[0] * 0f;
        float uz = axis[0] * hy - axis[1] * hx;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (ul < 1.0e-5f) {
            return new float[]{axis[0], axis[1], axis[2]};
        }
        ux /= ul;
        uy /= ul;
        uz /= ul;
        float wx = axis[1] * uz - axis[2] * uy;
        float wy = axis[2] * ux - axis[0] * uz;
        float wz = axis[0] * uy - axis[1] * ux;

        float phi = rnd.nextFloat() * TAU;
        float r = (float) Math.pow(rnd.nextFloat(), 0.6) * spread;
        float c = (float) Math.cos(phi) * r;
        float s = (float) Math.sin(phi) * r;
        float x = axis[0] + ux * c + wx * s;
        float y = axis[1] + uy * c + wy * s;
        float z = axis[2] + uz * c + wz * s;
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        if (l < 1.0e-5f) {
            return new float[]{axis[0], axis[1], axis[2]};
        }
        return new float[]{x / l, y / l, z / l};
    }

    private static float[] unpack(int rgb) {
        return new float[]{((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f};
    }

    /** Оттенок цвета в градусах [0..360); нужен ползункам в ClickGUI. */
    public static double hueOf(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float delta = max - Math.min(r, Math.min(g, b));
        if (delta < 1.0e-6f) {
            return 0.0;
        }
        float h;
        if (max == r) {
            h = ((g - b) / delta + 6f) % 6f;
        } else if (max == g) {
            h = (b - r) / delta + 2f;
        } else {
            h = (r - g) / delta + 4f;
        }
        return h * 60.0;
    }

    /** Насыщенный яркий цвет заданного оттенка (градусы) в виде 0xRRGGBB. */
    public static int colorFromHue(double degrees) {
        float[] c = hsvToRgb(degrees / 360.0, 0.60f, 1.0f);
        return Math.round(c[0] * 255f) << 16 | Math.round(c[1] * 255f) << 8 | Math.round(c[2] * 255f);
    }

    private static float[] mix(float[] a, float[] b, float t) {
        return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    /** Точка на градиенте основной -> акцентный цвет; в радуге - соседние оттенки одного цвета. */
    private static float[] gradient(float[] a, float[] b, float t, boolean rainbowMode, double hue) {
        if (rainbowMode) {
            return hsvToRgb(hue + t * 0.20, 0.62f, 1.0f);
        }
        return mix(a, b, t);
    }

    private static float[] hsvToRgb(double h, float s, float v) {
        double hh = (h - Math.floor(h)) * 6.0;
        int i = (int) hh;
        float f = (float) (hh - i);
        float p = v * (1f - s);
        float q = v * (1f - s * f);
        float t = v * (1f - s * (1f - f));
        return switch (i % 6) {
            case 0 -> new float[]{v, t, p};
            case 1 -> new float[]{q, v, p};
            case 2 -> new float[]{p, v, t};
            case 3 -> new float[]{p, q, v};
            case 4 -> new float[]{t, p, v};
            default -> new float[]{v, p, q};
        };
    }

    // -------------------------------------------------------------------- types
    /** Удар, который ждёт подтверждения урона от сервера. */
    private static final class Pending {
        final LivingEntity entity;
        /** Точка удара относительно ног цели: за время ожидания цель успевает сдвинуться. */
        final double dx, dy, dz;
        final float nx, ny, nz;
        final float fx, fy, fz;
        final boolean crit;
        int lastHurt;
        int age;

        Pending(LivingEntity entity, double dx, double dy, double dz,
                float nx, float ny, float nz, float fx, float fy, float fz,
                boolean crit, int lastHurt) {
            this.entity = entity;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
            this.fx = fx;
            this.fy = fy;
            this.fz = fz;
            this.crit = crit;
            this.lastHurt = lastHurt;
        }
    }

    /** Одна частица: стартовые условия, по ним позиция считается в любой момент жизни. */
    private static final class Particle {
        final int kind;
        final int seed;
        /** Стартовая скорость, блоков в секунду. */
        float vx, vy, vz;
        /** Смещение старта от точки удара. */
        float ox, oy, oz;
        /** Коэффициент сопротивления, 1/с. */
        float drag = 2.5f;
        /** Ускорение вниз, блоков/с^2; отрицательное - всплывает. */
        float gravity;
        /** Половина стороны квада в блоках (для искры - полуширина). */
        float size = 0.1f;
        /** Во сколько раз вырастает размер за жизнь (минус 1). */
        float growth;
        /** Выдвижение к камере, чтобы тело цели не перекрывало вспышку. */
        float pull;
        float delay;
        float life = 0.5f;
        float spin0;
        float spin;
        float r = 1f, g = 1f, b = 1f;

        Particle(int kind, int seed) {
            this.kind = kind;
            this.seed = seed;
        }

        void setVelocity(float x, float y, float z) {
            vx = x;
            vy = y;
            vz = z;
        }

        void setOffset(ThreadLocalRandom rnd, float radius) {
            ox = (rnd.nextFloat() - 0.5f) * 2f * radius;
            oy = (rnd.nextFloat() - 0.5f) * 2f * radius;
            oz = (rnd.nextFloat() - 0.5f) * 2f * radius;
        }

        void setColor(float[] rgb) {
            r = rgb[0];
            g = rgb[1];
            b = rgb[2];
        }
    }

    private static final class Burst {
        final double x, y, z;
        final long startNs;
        final Particle[] particles;
        /** Секунды до конца жизни последней частицы. */
        final float end;

        Burst(double x, double y, double z, long startNs, Particle[] particles, float end) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.startNs = startNs;
            this.particles = particles;
            this.end = end;
        }
    }
}
