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
 * Трансформацию ViewModel кладём сразу после ванильного matrices.push(), до сдвигов и
 * анимаций руки. Один и тот же MatrixStack используется для обеих рук, поэтому применять
 * трансформацию снаружи push нельзя: она осталась бы в матрице и наложилась второй раз
 * на отрисовку другой руки. Внутри push/pop у каждой руки своя независимая трансформация.
 *
 * Аргументы метода берём по типу через @Local(argsOnly): так миксину не нужна точная
 * сигнатура, которая в 1.21.x менялась из-за переработки рендера.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @Inject(
            method = "renderFirstPersonItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/util/math/MatrixStack;push()V",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            )
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
