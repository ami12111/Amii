package ami.ami.mixin.client;

import ami.ami.client.velocity.VelocityModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ловит момент нокбэка: пакет velocity для локального игрока. Yarn mappings. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPacketListenerMixin {

    // Mojang: handleSetEntityMotion(ClientboundSetEntityMotionPacket)
    @Inject(method = "onEntityVelocityUpdate", at = @At("TAIL"))
    private void velocity$onKnockback(EntityVelocityUpdateS2CPacket packet, CallbackInfo ci) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }

        // Mojang: packet.id()
        if (packet.getEntityId() != player.getId()) {
            return;
        }

        VelocityModule.onKnockback(player);
    }
}
