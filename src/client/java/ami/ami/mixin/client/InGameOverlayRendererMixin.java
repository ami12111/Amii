package ami.ami.mixin.client;

import ami.ami.client.render.NoRenderModule;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NoRender: огонь на экране и анимация тотема бессмертия. */
@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {

    /** Огонь на экране, пока игрок горит. */
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void ami$noRenderFireOverlay(CallbackInfo ci) {
        if (NoRenderModule.hideFireOverlay()) {
            ci.cancel();
        }
    }

    /** Всплывающая анимация тотема бессмертия поверх экрана. */
    @Inject(method = "renderFloatingItem", at = @At("HEAD"), cancellable = true)
    private void ami$noRenderTotem(CallbackInfo ci) {
        if (NoRenderModule.hideTotemAnimation()) {
            ci.cancel();
        }
    }
}
