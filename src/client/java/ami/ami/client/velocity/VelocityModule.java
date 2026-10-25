package ami.ami.client.velocity;

import net.minecraft.client.network.ClientPlayerEntity;

import java.lang.ref.WeakReference;
import java.util.concurrent.ThreadLocalRandom;

/** Логика jump-on-hit. Миксины только вызывают эти методы. */
public final class VelocityModule {
    private static final int NEVER = 1000;

    /** Маркер «удар ещё не обрабатывался». Половина диапазона, чтобы вычитание не переполнялось. */
    private static final long NO_HIT = Long.MIN_VALUE / 2;

    /** Насколько «свежим» должен быть урон (тиков с его начала), чтобы считать velocity-пакет отдачей. */
    private static final int FRESH_HURT_TICKS = 2;

    /**
     * Сколько тиков ждём анимацию урона, если velocity-пакет пришёл РАНЬШЕ пакета урона
     * (порядок зависит от сервера/прокси). Без этого такой удар молча терялся.
     */
    private static final int HIT_CONFIRM_TICKS = 2;

    /** Локальный игрок, которому принадлежит состояние. Слабая ссылка, чтобы не держать мир в памяти. */
    private static WeakReference<ClientPlayerEntity> owner = new WeakReference<>(null);

    /** Счётчик тиков модуля (растёт в каждом тике игрока). */
    private static long now = 0;
    /** Тик, на котором начался последний обработанный удар. Защита от дублей velocity-пакета. */
    private static long lastHitStart = NO_HIT;
    /** Сколько тиков ещё ждём подтверждение удара (hurtTime), 0 = не ждём. */
    private static int awaitingHit = 0;
    /** Сколько тиков осталось до прыжка, -1 = прыжок не запланирован. */
    private static int countdown = -1;
    /** Модуль в этом тике выставил jumping и ждёт проверки результата в конце тика. */
    private static boolean jumpRequested = false;
    /** Тиков прошло с последнего ПОДТВЕРЖДЁННОГО прыжка модуля. */
    private static int sinceJump = NEVER;

    private VelocityModule() {
    }

    /** Вызывается, когда пришёл пакет velocity для локального игрока. */
    public static void onKnockback(ClientPlayerEntity player) {
        syncOwner(player);

        VelocityConfig config = VelocityConfig.get();
        if (!config.enabled || !player.isAlive()) {
            return;
        }

        // Уже есть план или ждём подтверждения удара: повторный пакет не должен плодить прыжки.
        if (countdown >= 0 || awaitingHit > 0) {
            return;
        }

        // Реагируем только на свежий удар (velocity приходит и от других источников).
        // Yarn: hurtTime / maxHurtTime (аналог Mojang hurtDuration)
        if (isFreshHurt(player)) {
            if (!isDuplicate(player)) {
                startPlan(player, config);
            }
        } else {
            // Пакет урона мог ещё не дойти: даём ему пару тиков.
            awaitingHit = HIT_CONFIRM_TICKS;
        }
    }

    /**
     * Вызывается после применения ввода в tickMovement() локального игрока.
     *
     * @param vanillaJumpDelay текущее значение ванильной задержки между прыжками (jumpingCooldown)
     * @return true, если в этом тике нужно прыгнуть
     */
    public static boolean onPlayerTick(ClientPlayerEntity player, int vanillaJumpDelay) {
        syncOwner(player);
        now++;
        if (sinceJump < NEVER) {
            sinceJump++;
        }

        VelocityConfig config = VelocityConfig.get();

        // Модуль выключили или игрок умер: чужой план не должен выстрелить позже.
        if (!config.enabled || !player.isAlive()) {
            clearPlan();
            return false;
        }

        // Velocity пришёл раньше урона: ждём, пока hurtTime станет свежим.
        if (awaitingHit > 0) {
            if (isFreshHurt(player)) {
                awaitingHit = 0;
                if (!isDuplicate(player)) {
                    startPlan(player, config);
                }
            } else {
                awaitingHit--;
            }
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
                || player.isTouchingWater() || player.isInLava()
                || player.isSpectator() || player.isClimbing()) {
            return false;
        }

        // Не прыгаем чаще, чем позволяет ванильная механика прыжка.
        boolean cannotJump = (config.onlyOnGround && !player.isOnGround())
                || vanillaJumpDelay > 0
                || sinceJump < config.cooldownTicks;
        if (cannotJump) {
            VelocityStats.record(VelocityStats.Result.MISSED);
            return false;
        }

        // sinceJump здесь НЕ трогаем: кулдаун начинается только после подтверждённого прыжка.
        jumpRequested = true;
        return true;
    }

    /**
     * Вызывается в конце tickMovement(), когда ванильная секция прыжка уже отработала.
     * Перед запросом задержка прыжка была 0, а после выполненного jump() ванила ставит её в 10.
     * Значит, значение больше нуля = прыжок реально произошёл именно в этом тике.
     */
    public static void afterPlayerTick(int vanillaJumpDelay) {
        if (!jumpRequested) {
            return;
        }
        jumpRequested = false;
        if (vanillaJumpDelay > 0) {
            sinceJump = 0;
            VelocityStats.record(VelocityStats.Result.JUMPED);
        } else {
            VelocityStats.record(VelocityStats.Result.FAILED);
        }
    }

    public static void reset() {
        clearPlan();
        jumpRequested = false;
        sinceJump = NEVER;
        lastHitStart = NO_HIT;
    }

    private static void clearPlan() {
        countdown = -1;
        awaitingHit = 0;
    }

    /** Новый экземпляр ClientPlayerEntity (вход на сервер, смена мира, респавн) = чистое состояние. */
    private static void syncOwner(ClientPlayerEntity player) {
        if (owner.get() != player) {
            reset();
            owner = new WeakReference<>(player);
        }
    }

    private static boolean isFreshHurt(ClientPlayerEntity player) {
        return player.hurtTime > 0 && player.maxHurtTime - player.hurtTime <= FRESH_HURT_TICKS;
    }

    /** Тик модуля, на котором начался текущий урон. */
    private static long hitStart(ClientPlayerEntity player) {
        return now - (player.maxHurtTime - player.hurtTime);
    }

    /** Тот же удар, что уже обработан (сервер прислал velocity дважды). Допуск ±1 тик на порядок пакетов. */
    private static boolean isDuplicate(ClientPlayerEntity player) {
        return Math.abs(hitStart(player) - lastHitStart) <= 1;
    }

    private static void startPlan(ClientPlayerEntity player, VelocityConfig config) {
        // Удар помечаем обработанным ДО броска шанса, иначе дубль пакета бросил бы кубик второй раз.
        lastHitStart = hitStart(player);
        if (ThreadLocalRandom.current().nextInt(100) >= config.chance) {
            VelocityStats.record(VelocityStats.Result.SKIPPED);
            return;
        }
        countdown = config.delayTicks;
    }
}
