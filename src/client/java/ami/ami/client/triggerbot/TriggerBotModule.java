package ami.ami.client.triggerbot;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Логика triggerbot: бьёт игрока, на которого наведён прицел.
 * Миксин (MinecraftClientMixin) только вызывает tick().
 */
public final class TriggerBotModule {
    /** Включён ли модуль. */
    public static boolean enabled = false;

    /** Минимальный заряд атаки (0.0 - 1.0). 0.95+ = полный урон. */
    public static float minCooldown = 0.95f;

    /**
     * true  - бить ТОЛЬКО критами (в падении), на земле не бьёт.
     * false - на земле бьёт обычно (при полном кулдауне), в воздухе ждёт падения для крита.
     */
    public static boolean onlyCrits = false;

    /** Максимальная дистанция атаки (ваниль: 3.0 блока). */
    public static double reach = 3.0;

    private TriggerBotModule() {
    }

    /** Вызывается из миксина в конце MinecraftClient.tick(). */
    public static void tick(MinecraftClient mc) {
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

        // Прицел должен смотреть на сущность
        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof EntityHitResult entityHit)) {
            return;
        }

        Entity target = entityHit.getEntity();
        if (!(target instanceof PlayerEntity targetPlayer)) {
            return;
        }
        if (!targetPlayer.isAlive() || targetPlayer.isSpectator()) {
            return;
        }
        if (player.squaredDistanceTo(target) > reach * reach) {
            return;
        }

        // Кулдаун 1.9: ждём полной зарядки атаки
        if (player.getAttackCooldownProgress(0.5f) < minCooldown) {
            return;
        }

        boolean canCrit = canCrit(player);

        if (onlyCrits) {
            // Только криты: нужно падение
            if (!canCrit) {
                return;
            }
        } else {
            // На земле бьём сразу при полном кулдауне.
            // В воздухе (прыжок) ждём момента падения, чтобы получился крит.
            if (!player.isOnGround() && !canCrit) {
                return;
            }
        }

        mc.interactionManager.attackEntity(player, target);
        player.swingHand(Hand.MAIN_HAND);
    }

    /** Условия крита из ванильной логики PlayerEntity.attack(). */
    private static boolean canCrit(ClientPlayerEntity p) {
        return p.fallDistance > 0.0f
                && !p.isOnGround()
                && !p.isClimbing()
                && !p.isTouchingWater()
                && !p.hasStatusEffect(StatusEffects.BLINDNESS)
                && !p.hasVehicle();
    }
}
