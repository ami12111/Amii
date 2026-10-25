package ami.ami.mixin.client;

import ami.ami.client.movement.AutoSprintModule;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AutoSprint: ввод уже прочитан из клавиш (Input.tick), но ванильная проверка старта
 * спринта ещё не выполнена — правим флаг здесь, чтобы дальше всё решала ваниль.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {

    @Inject(
            method = "tickMovement",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/input/Input;tick()V",
                    shift = At.Shift.AFTER
            )
    )
    private void ami$autoSprint(CallbackInfo ci) {
        AutoSprintModule.onTickMovement((ClientPlayerEntity) (Object) this);
    }
}
