package ami.ami.mixin.client;

import ami.ami.client.velocity.VelocityModule;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * В 1.21.11 ввод применяется через ClientPlayerEntity.tickMovementInput(),
 * который вызывается изнутри LivingEntity.tickMovement(), поэтому инжект
 * ставится сразу ПОСЛЕ этого вызова: флаг jumping не будет перезаписан
 * вводом и будет применён в этом же тике секцией "jump".
 * Ванильную задержку между прыжками (jumpingCooldown) мы больше не обнуляем.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    // Yarn: jumping
    @Shadow
    protected boolean jumping;

    // Mojang: noJumpDelay
    @Shadow
    private int jumpingCooldown;

    // Mojang: aiStep()
    @Inject(
        method = "tickMovement",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/LivingEntity;tickMovementInput()V",
            shift = At.Shift.AFTER
        )
    )
    private void velocity$jumpOnHit(CallbackInfo ci) {
        if (!((Object) this instanceof ClientPlayerEntity player)) {
            return;
        }

        if (VelocityModule.onPlayerTick(player, this.jumpingCooldown)) {
            this.jumping = true; // прыжок в этом же тике
        }
    }
}
