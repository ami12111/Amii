package ami.ami.mixin.client;

import ami.ami.client.render.ViewModelModule;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Предмет и рука от первого лица рисуются в HeldItemRenderer.renderFirstPersonItem().
 * Трансформацию ViewModel кладём в самое начало метода, до ванильных сдвигов и анимаций,
 * поэтому замах и подъём предмета работают как обычно, уже внутри изменённой матрицы.
 *
 * Аргументы метода берём по типу через @Local(argsOnly): так миксину не нужна точная
 * сигнатура, которая в 1.21.x менялась из-за переработки рендера.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @Inject(
        method = "renderFirstPersonItem",
        at = @At("HEAD")
    )
    private void ami$applyViewModel(
            CallbackInfo ci,
            @Local(argsOnly = true) AbstractClientPlayerEntity player,
            @Local(argsOnly = true) Hand hand,
            @Local(argsOnly = true) MatrixStack matrices
    ) {
        if (!ViewModelModule.enabled) {
            return;
        }
        Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        ViewModelModule.apply(matrices, arm == Arm.RIGHT);
    }
}
