package ami.ami.mixin.client;

import ami.ami.client.render.NoRenderModule;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NoRender: не создаём новые частицы, когда модуль включён. */
@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {

    @Inject(
            method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ami$noRenderParticle(ParticleEffect parameters, double x, double y, double z,
                                      double velocityX, double velocityY, double velocityZ,
                                      CallbackInfoReturnable<Particle> cir) {
        if (NoRenderModule.hideParticles()) {
            cir.setReturnValue(null);
        }
    }

    @Inject(
            method = "addEmitter(Lnet/minecraft/entity/Entity;Lnet/minecraft/particle/ParticleEffect;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ami$noRenderEmitter(Entity entity, ParticleEffect parameters, CallbackInfo ci) {
        if (NoRenderModule.hideParticles()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "addEmitter(Lnet/minecraft/entity/Entity;Lnet/minecraft/particle/ParticleEffect;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ami$noRenderEmitterTimed(Entity entity, ParticleEffect parameters, int maxAge,
                                          CallbackInfo ci) {
        if (NoRenderModule.hideParticles()) {
            ci.cancel();
        }
    }
}
