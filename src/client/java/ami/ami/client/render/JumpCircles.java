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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * JumpCircles - shader driven magic circles on every jump.
 *
 * <ul>
 *   <li>takeoff: an ornate rune circle or a spiral variant, a prismatic shockwave, sparks
 *       and three halo rings that climb up the player's body;</li>
 *   <li>landing: a shockwave with cracks, a starburst or a rune-fracture variant, scaled by
 *       fall height.</li>
 * </ul>
 *
 * Every effect has its own colour ({@link #takeoffColor}, {@link #landingColor},
 * {@link #haloColor}); {@link #rainbow} overrides them with a cycling hue.
 *
 * All visuals live in {@code assets/ami/shaders/core/jump_circles.fsh}. Java only spawns
 * quads and feeds them progress / tint / seed through the vertex attributes.
 *
 * Hook-up: call {@link #init()} once from your ClientModInitializer.
 */
public final class JumpCircles {

    /** Must match the namespace folder under assets/ (your mod id). */
    public static final String MOD_ID = "ami";

    // ---------------------------------------------------------------- settings
    public static boolean enabled = true;
    public static boolean showForOthers = false;
    public static boolean landingBurst = true;
    public static boolean risingHalos = true;
    /** Randomly pick one of the extra styles for takeoff and landing. */
    public static boolean randomStyles = true;
    public static boolean rainbow = false;
    /** 0xRRGGBB colours per effect, used when rainbow is off. */
    public static int takeoffColor = 0xB06CFF;
    public static int landingColor = 0x6C9BFF;
    public static int haloColor = 0xE08CFF;
    /** Global size multiplier. */
    public static float scale = 1.0f;

    // ------------------------------------------------------------------ consts
    private static final int KIND_TAKEOFF = 0;
    private static final int KIND_LANDING = 1;
    private static final int KIND_HALO = 2;
    private static final int KIND_TAKEOFF_SPIRAL = 3;
    private static final int KIND_LANDING_STAR = 4;
    private static final int KIND_LANDING_RUNES = 5;

    private static final float TAKEOFF_SECONDS = 1.4f;
    private static final float LANDING_SECONDS = 1.0f;
    private static final float GROUND_LIFT = 0.03f;
    private static final int MAX_CIRCLES = 24;

    // ---------------------------------------------------------------- pipeline
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of(MOD_ID, "pipeline/jump_circles"))
                    .withVertexShader(Identifier.of(MOD_ID, "core/jump_circles"))
                    .withFragmentShader(Identifier.of(MOD_ID, "core/jump_circles"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .withBlend(BlendFunction.ADDITIVE)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final BufferAllocator ALLOCATOR = new BufferAllocator(1 << 16);
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    // ------------------------------------------------------------------- state
    private static final CopyOnWriteArrayList<Circle> CIRCLES = new CopyOnWriteArrayList<>();
    private static final Map<UUID, PlayerState> STATES = new ConcurrentHashMap<>();
    private static boolean initialized;

    private JumpCircles() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ClientTickEvents.END_CLIENT_TICK.register(JumpCircles::onTick);
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(JumpCircles::onWorldRender);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ALLOCATOR.close());
    }

    public static void toggle() {
        enabled = !enabled;
        if (!enabled) {
            CIRCLES.clear();
        }
    }

    /** Debug helper: plays both effects in front of the local player. */
    public static void spawnPreview() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return;
        }
        double yawRad = Math.toRadians(mc.player.getYaw());
        double fx = -Math.sin(yawRad) * 4.0;
        double fz = Math.cos(yawRad) * 4.0;
        spawn(KIND_TAKEOFF, mc.player.getX() + fx, mc.player.getY(), mc.player.getZ() + fz, 1.0f);
        spawn(KIND_LANDING, mc.player.getX() - fx, mc.player.getY(), mc.player.getZ() - fz, 1.0f);
    }

    // ------------------------------------------------------------ jump tracking
    private static void onTick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) {
            STATES.clear();
            CIRCLES.clear();
            return;
        }
        if (!enabled) {
            return;
        }

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p != mc.player && !showForOthers) {
                continue;
            }

            PlayerState s = STATES.computeIfAbsent(p.getUuid(), id -> new PlayerState());
            boolean ground = p.isOnGround();
            double y = p.getY();

            if (!s.init) {
                s.init = true;
                s.wasGround = ground;
                s.groundY = y;
                s.peakY = y;
                continue;
            }

            boolean busy = p.isSpectator() || p.hasVehicle() || p.isTouchingWater()
                    || p.isGliding() || p.isClimbing();

            // takeoff: was on the ground, now is not, and we actually went up
            if (s.wasGround && !ground && !busy && y - s.groundY > 0.1) {
                int kind = KIND_TAKEOFF;
                if (randomStyles && ThreadLocalRandom.current().nextBoolean()) {
                    kind = KIND_TAKEOFF_SPIRAL;
                }
                spawn(kind, p.getX(), s.groundY, p.getZ(), 1.0f);
            }

            // landing: scale with the height we fell from
            if (!s.wasGround && ground && !busy && landingBurst) {
                double drop = s.peakY - y;
                if (drop > 1.2) {
                    float intensity = (float) Math.min(drop / 6.0, 1.0);
                    int kind = KIND_LANDING;
                    if (randomStyles) {
                        int roll = ThreadLocalRandom.current().nextInt(3);
                        if (roll == 1) {
                            kind = KIND_LANDING_STAR;
                        } else if (roll == 2) {
                            kind = KIND_LANDING_RUNES;
                        }
                    }
                    spawn(kind, p.getX(), y, p.getZ(), 0.55f + intensity * 0.9f);
                }
            }

            if (ground) {
                s.groundY = y;
                s.peakY = y;
            } else {
                s.peakY = Math.max(s.peakY, y);
            }
            s.wasGround = ground;
        }
    }

    private static void spawn(int kind, double x, double y, double z, float sizeFactor) {
        while (CIRCLES.size() >= MAX_CIRCLES) {
            CIRCLES.remove(0);
        }
        int base = switch (kind) {
            case KIND_TAKEOFF, KIND_TAKEOFF_SPIRAL -> takeoffColor;
            case KIND_LANDING, KIND_LANDING_STAR, KIND_LANDING_RUNES -> landingColor;
            default -> haloColor;
        };
        float[] rgb = rainbow
                ? hsvToRgb((System.nanoTime() / 2_500_000_000.0) % 1.0, 0.65f, 1.0f)
                : unpack(base);
        float[] halo = rainbow
                ? hsvToRgb(((System.nanoTime() + 600_000_000L) / 2_500_000_000.0) % 1.0, 0.60f, 1.0f)
                : unpack(haloColor);
        float seconds = kind == KIND_TAKEOFF || kind == KIND_TAKEOFF_SPIRAL
                ? TAKEOFF_SECONDS : LANDING_SECONDS;
        CIRCLES.add(new Circle(kind, x, y, z, sizeFactor * scale, rgb[0], rgb[1], rgb[2],
                halo[0], halo[1], halo[2],
                ThreadLocalRandom.current().nextInt(16), System.nanoTime(), seconds));
    }

    // ---------------------------------------------------------------- rendering
    private static void onWorldRender(WorldRenderContext context) {
        if (!enabled || CIRCLES.isEmpty()) {
            return;
        }

        long now = System.nanoTime();
        CIRCLES.removeIf(c -> c.progress(now) >= 1.0f);
        if (CIRCLES.isEmpty()) {
            return;
        }

        // the matrix on the stack carries the camera rotation, so we feed it
        // camera-relative coordinates (keeps float precision far from the origin)
        Matrix4fc view = context.matrices().peek().getPositionMatrix();
        Vec3d cam = context.worldState().cameraRenderState.pos;

        BufferBuilder buffer = new BufferBuilder(ALLOCATOR, PIPELINE.getVertexFormatMode(), PIPELINE.getVertexFormat());
        for (Circle c : CIRCLES) {
            emit(buffer, view, c, now, cam);
        }

        BuiltBuffer built = buffer.end();
        draw(MinecraftClient.getInstance(), built);
    }

    private static void emit(BufferBuilder b, Matrix4fc m, Circle c, long now, Vec3d cam) {
        float t = c.progress(now);
        float rx = (float) (c.x - cam.x);
        float ry = (float) (c.y - cam.y) + GROUND_LIFT;
        float rz = (float) (c.z - cam.z);

        if (c.kind == KIND_TAKEOFF || c.kind == KIND_TAKEOFF_SPIRAL) {
            quad(b, m, rx, ry, rz, 2.3f * c.size, c.kind, c.seed, c.r, c.g, c.b, t);

            if (risingHalos) {
                for (int i = 0; i < 3; i++) {
                    float ti = clamp01((t - 0.06f - i * 0.10f) / 0.62f);
                    if (ti > 0f && ti < 1f) {
                        float lift = 0.15f + easeOutCubic(ti) * 2.1f;
                        float half = 0.95f * c.size * (1.0f - 0.2f * ti);
                        quad(b, m, rx, ry + lift, rz, half, KIND_HALO, i, c.hr, c.hg, c.hb, ti);
                    }
                }
            }
        } else {
            float half = switch (c.kind) {
                case KIND_LANDING_STAR -> 2.7f * c.size;
                case KIND_LANDING_RUNES -> 2.0f * c.size;
                default -> (1.2f + 0.5f * c.size) * c.size;
            };
            quad(b, m, rx, ry, rz, half, c.kind, c.seed, c.r, c.g, c.b, t);
        }
    }

    /**
     * Flat quad on the XZ plane. UVs are local [-1..1] coords shifted by a whole
     * number of 4-unit cells: X cell = seed, Y cell = effect kind (decoded in the shader).
     */
    private static void quad(BufferBuilder b, Matrix4fc m, float x, float y, float z, float half,
                             int kind, int seed, float r, float g, float bl, float t) {
        float ox = 4f * seed;
        float oy = 4f * kind;
        vert(b, m, x - half, y, z - half, -1f + ox, -1f + oy, r, g, bl, t);
        vert(b, m, x - half, y, z + half, -1f + ox, 1f + oy, r, g, bl, t);
        vert(b, m, x + half, y, z + half, 1f + ox, 1f + oy, r, g, bl, t);
        vert(b, m, x + half, y, z - half, 1f + ox, -1f + oy, r, g, bl, t);
    }

    private static void vert(BufferBuilder b, Matrix4fc m, float x, float y, float z,
                             float u, float v, float r, float g, float bl, float a) {
        b.vertex(m, x, y, z).texture(u, v).color(r, g, bl, a);
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
                () -> MOD_ID + " jump circles",
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
    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    private static float easeOutCubic(float x) {
        float y = 1f - clamp01(x);
        return 1f - y * y * y;
    }

    private static float[] unpack(int rgb) {
        return new float[]{((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f};
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
    private static final class PlayerState {
        boolean init;
        boolean wasGround;
        double groundY;
        double peakY;
    }

    private static final class Circle {
        final int kind;
        final double x, y, z;
        final float size;
        final float r, g, b;
        final float hr, hg, hb;
        final int seed;
        final long startNs;
        final float seconds;

        Circle(int kind, double x, double y, double z, float size, float r, float g, float b,
               float hr, float hg, float hb, int seed, long startNs, float seconds) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.z = z;
            this.size = size;
            this.r = r;
            this.g = g;
            this.b = b;
            this.hr = hr;
            this.hg = hg;
            this.hb = hb;
            this.seed = seed;
            this.startNs = startNs;
            this.seconds = seconds;
        }

        float progress(long nowNs) {
            return (nowNs - startNs) / (seconds * 1_000_000_000f);
        }
    }
}
