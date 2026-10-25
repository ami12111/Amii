package ami.ami.mixin.client;

import ami.ami.client.triggerbot.TriggerBotModule;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * В конце тика crosshairTarget уже обновлён (updateTargetedEntity вызывается в gameRenderer),
 * поэтому читаем актуальную цель.
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void ami$triggerbotTick(CallbackInfo ci) {
        TriggerBotModule.tick((MinecraftClient) (Object) this);
    }
}
