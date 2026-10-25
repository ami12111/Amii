package ami.ami.client.velocity;

import net.minecraft.client.network.ClientPlayerEntity;

import java.util.concurrent.ThreadLocalRandom;

/** Логика jump-on-hit. Миксины только вызывают эти методы. */
public final class VelocityModule {
    private static final int NEVER = 1000;

    /** Сколько тиков осталось до прыжка, -1 = прыжок не запланирован. */
    private static int countdown = -1;
    /** Надо ли на следующем тике проверить, что прыжок реально произошёл. */
    private static boolean verify = false;
    /** Тиков прошло с последнего прыжка модуля. */
    private static int sinceJump = NEVER;

    private VelocityModule() {
    }

    /** Вызывается, когда пришёл пакет velocity для локального игрока. */
    public static void onKnockback(ClientPlayerEntity player) {
        VelocityConfig config = VelocityConfig.get();
        if (!config.enabled) {
            return;
        }

        // Реагируем только на свежий удар. Velocity-пакеты приходят и от других
        // источников (взрывы, удочка и т.п.), на них прыгать не нужно.
        // Yarn: hurtTime / maxHurtTime (аналог Mojang hurtDuration)
        if (player.hurtTime <= 0 || player.maxHurtTime - player.hurtTime > 2) {
            return;
        }

        // Прыжок уже запланирован: серия ударов не должна перезапускать и плодить прыжки.
        if (countdown >= 0) {
            return;
        }

        if (ThreadLocalRandom.current().nextInt(100) >= config.chance) {
            VelocityStats.record(VelocityStats.Result.SKIPPED);
            return;
        }
        countdown = config.delayTicks;
    }

    /**
     * Вызывается после применения ввода в tickMovement() локального игрока.
     *
     * @param vanillaJumpDelay текущее значение ванильной задержки между прыжками (jumpingCooldown)
     * @return true, если в этом тике нужно прыгнуть
     */
    public static boolean onPlayerTick(ClientPlayerEntity player, int vanillaJumpDelay) {
        if (sinceJump < NEVER) {
            sinceJump++;
        }

        // Проверка результата прыжка из прошлого тика.
        if (verify) {
            verify = false;
            VelocityStats.record(!player.isOnGround() ? VelocityStats.Result.JUMPED : VelocityStats.Result.FAILED);
        }

        if (countdown < 0) {
            return false;
        }
        if (countdown > 0) {
            countdown--;
            return false;
        }
        countdown = -1;

        // В этих состояниях прыжок не имеет смысла, план просто отбрасываем.
        if (player.hasVehicle() || player.getAbilities().flying
                || player.isTouchingWater() || player.isInLava()) {
            return false;
        }

        VelocityConfig config = VelocityConfig.get();

        // Не прыгаем чаще, чем позволяет ванильная механика прыжка.
        boolean cannotJump = (config.onlyOnGround && !player.isOnGround())
                || vanillaJumpDelay > 0
                || sinceJump < config.cooldownTicks;
        if (cannotJump) {
            VelocityStats.record(VelocityStats.Result.MISSED);
            return false;
        }

        sinceJump = 0;
        verify = true;
        return true;
    }

    public static void reset() {
        countdown = -1;
        verify = false;
        sinceJump = NEVER;
    }
}
