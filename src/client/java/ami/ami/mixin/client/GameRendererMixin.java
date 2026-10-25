package ami.ami.mixin.client;

import ami.ami.client.render.NoHurtCamModule;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * В 1.21.11 тряска камеры при получении урона (damage tilt) применяется
 * в GameRenderer.tiltViewWhenHurt(), поэтому отменяем его целиком.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(
        method = "tiltViewWhenHurt",
        at = @At("HEAD"),
        cancellable = true
    )
    private void ami$cancelDamageTilt(CallbackInfo ci) {
        if (NoHurtCamModule.enabled) {
            ci.cancel();
        }
    }
}
