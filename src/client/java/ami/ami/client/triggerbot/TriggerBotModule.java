package ami.ami.client.triggerbot;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

/**
 * Логика triggerbot под механику боя 1.9+: бьёт игрока под прицелом.
 * Миксины только вызывают tick() и onKnockback().
 *
 * Что важно для крита в 1.9+ (PlayerEntity.attack):
 *  - заряд атаки строго больше 0.9;
 *  - fallDistance > 0 и игрок не на земле;
 *  - игрок НЕ бежит (спринт-удар крит не даёт) - поэтому нужен сброс спринта;
 *  - не в воде, не на лестнице, нет слепоты, не на транспорте.
 */
public final class TriggerBotModule {
    /** Включён ли модуль. */
    public static boolean enabled = false;

    /** Минимальный заряд атаки (0.0 - 1.0) для обычного удара. 0.95+ = полный урон. */
    public static float minCooldown = 0.95f;

    /**
     * true  - бить ТОЛЬКО критами (в падении), на земле не бьёт.
     * false - на земле бьёт обычно (при полном кулдауне), в воздухе ждёт падения для крита.
     */
    public static boolean onlyCrits = false;

    /**
     * Максимальная дистанция атаки: от глаз до ближайшей точки хитбокса (как в ваниле).
     * Ваниль: 3.0. Ванильный сервер всё равно режет удары дальше ~4.0 до хитбокса.
     */
    public static double reach = 3.0;

    /**
     * Сброс спринта: перед критом снимает спринт (сервер иначе не засчитает крит),
     * после удара возвращает его, чтобы следующий удар снова был со спринт-отбросом.
     */
    public static boolean sprintReset = true;

    /**
     * Крит от отдачи: после удара по нам удар удерживается, пока нас подбрасывает,
     * и выпускается в первый тик падения.
     */
    public static boolean recoilCrits = true;

    /**
     * Порог заряда для крита. Крит в ванили требует заряд > 0.9, так что ждать
     * 95% нет смысла: берём минимальное значение с запасом на рассинхрон клиента и сервера.
     */
    private static final float CRIT_CHARGE = 0.91f;

    /** Сколько тиков максимум держим удар, пока подброс от отдачи не начался/не закончился. */
    private static final int RECOIL_HOLD_TICKS = 12;

    /** Сколько тиков ещё действует окно отдачи. */
    private static int recoilTicks = 0;

    private TriggerBotModule() {
    }

    /** Вызывается из ClientPacketListenerMixin, когда пришёл velocity-пакет для локального игрока. */
    public static void onKnockback(ClientPlayerEntity player) {
        if (!enabled || !recoilCrits) {
            return;
        }
        // Нас подбросило вверх: впереди дуга падения, в ней можно ударить критом.
        if (player.getVelocity().y > 0.0) {
            recoilTicks = RECOIL_HOLD_TICKS;
        }
    }

    /** Вызывается из миксина в конце MinecraftClient.tick(). */
    public static void tick(MinecraftClient mc) {
        if (recoilTicks > 0) {
            recoilTicks--;
        }
        if (!enabled) {
            return;
        }

        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }
        if (mc.currentScreen != null) {
            return;
        }
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }

        // Свежий рейкаст в этом же тике от текущей позиции глаз до хитбокса.
        // mc.crosshairTarget тут отстаёт на тик и режет дистанцию, поэтому он не используется.
        AbstractClientPlayerEntity target = findTarget(mc, player);
        if (target == null) {
            return;
        }

        float charge = player.getAttackCooldownProgress(0.5f);
        boolean critBlocked = critBlocked(player);
        boolean airborne = !player.isOnGround() && !critBlocked;
        boolean critReady = airborne && player.fallDistance > 0.0f;

        // Отдача: нас только что подбросило, но мы ещё на земле/поднимаемся.
        // Обычный удар сейчас сжёг бы заряд, а через пару тиков будет крит - ждём.
        boolean launchPending = recoilCrits && recoilTicks > 0 && !critBlocked
                && player.getVelocity().y > 0.0;

        if (critReady) {
            if (charge < CRIT_CHARGE) {
                return;
            }
            attack(mc, player, target, true);
            return;
        }

        if (onlyCrits) {
            return;
        }
        if (airborne || launchPending) {
            // Ждём падения: в воздухе обычный удар не нужен, крит будет через несколько тиков.
            return;
        }
        if (charge < minCooldown) {
            return;
        }
        attack(mc, player, target, false);
    }

    /** Ближайший игрок, в которого упирается взгляд в пределах reach. */
    private static AbstractClientPlayerEntity findTarget(MinecraftClient mc, ClientPlayerEntity player) {
        Vec3d eye = player.getEyePos();
        Vec3d end = eye.add(player.getRotationVec(1.0f).multiply(reach));

        AbstractClientPlayerEntity best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (AbstractClientPlayerEntity other : mc.world.getPlayers()) {
            if (other == player || !other.isAlive() || other.isSpectator()) {
                continue;
            }
            Box box = other.getBoundingBox().expand(other.getTargetingMargin());
            double distSq;
            if (box.contains(eye)) {
                distSq = 0.0;
            } else {
                Optional<Vec3d> hit = box.raycast(eye, end);
                if (hit.isEmpty()) {
                    continue;
                }
                distSq = eye.squaredDistanceTo(hit.get());
            }
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = other;
            }
        }
        if (best == null) {
            return null;
        }

        // Блок между нами и целью перекрывает удар.
        HitResult blockHit = player.raycast(reach, 1.0f, false);
        if (blockHit.getType() == HitResult.Type.BLOCK && eye.squaredDistanceTo(blockHit.getPos()) < bestDistSq) {
            return null;
        }
        return best;
    }

    private static void attack(MinecraftClient mc, ClientPlayerEntity player,
                               AbstractClientPlayerEntity target, boolean crit) {
        boolean wasSprinting = player.isSprinting();

        if (sprintReset && crit && wasSprinting) {
            // Сервер считает крит по СВОЕМУ состоянию спринта, поэтому пакет STOP должен уйти
            // раньше пакета атаки, а не на следующем тике.
            player.networkHandler.sendPacket(
                    new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            player.setSprinting(false);
        }

        mc.interactionManager.attackEntity(player, target);
        player.swingHand(Hand.MAIN_HAND);

        if (sprintReset && wasSprinting) {
            restoreSprint(mc, player);
        }
    }

    /** Возвращает спринт после удара, если игрок продолжает бежать вперёд. */
    private static void restoreSprint(MinecraftClient mc, ClientPlayerEntity player) {
        boolean hungerOk = player.getHungerManager().getFoodLevel() > 6 || player.getAbilities().allowFlying;
        boolean canSprint = mc.options.forwardKey.isPressed()
                && hungerOk
                && !player.isSneaking()
                && !player.hasVehicle()
                && !player.hasStatusEffect(StatusEffects.BLINDNESS)
                && !player.isTouchingWater();
        if (!canSprint) {
            return;
        }
        player.networkHandler.sendPacket(
                new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_SPRINTING));
        player.setSprinting(true);
    }

    /** Состояния, в которых ванильная логика PlayerEntity.attack() крит не даёт. */
    private static boolean critBlocked(ClientPlayerEntity p) {
        return p.isClimbing()
                || p.isTouchingWater()
                || p.hasStatusEffect(StatusEffects.BLINDNESS)
                || p.hasVehicle()
                || p.getAbilities().flying;
    }
}
