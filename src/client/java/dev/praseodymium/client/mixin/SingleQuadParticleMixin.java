package dev.praseodymium.client.mixin;

import dev.praseodymium.client.ParticleOcclusionCuller;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.CampfireSmokeParticle;
import net.minecraft.client.particle.LargeSmokeParticle;
import net.minecraft.client.particle.NoxiousGasCloudParticle;
import net.minecraft.client.particle.PlayerCloudParticle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SmokeParticle;
import net.minecraft.client.particle.WhiteSmokeParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SingleQuadParticle.class)
public abstract class SingleQuadParticleMixin {
	@Unique
	private static final ParticleOcclusionCuller PRASEODYMIUM$PARTICLE_CULLER = new ParticleOcclusionCuller();

	@Shadow
	protected ClientLevel level;

	@Shadow
	protected double x;

	@Shadow
	protected double y;

	@Shadow
	protected double z;

	@Inject(method = "extract", at = @At("HEAD"), cancellable = true)
	private void praseodymium$hideBlockedCloudParticles(QuadParticleRenderState state, Camera camera, float partialTick, CallbackInfo ci) {
		Object self = this;
		if (!(self instanceof PlayerCloudParticle
			|| self instanceof SmokeParticle
			|| self instanceof LargeSmokeParticle
			|| self instanceof WhiteSmokeParticle
			|| self instanceof CampfireSmokeParticle
			|| self instanceof NoxiousGasCloudParticle)) {
			return;
		}

		if (PRASEODYMIUM$PARTICLE_CULLER.shouldCull(level, camera, x, y, z)) {
			ci.cancel();
		}
	}
}
