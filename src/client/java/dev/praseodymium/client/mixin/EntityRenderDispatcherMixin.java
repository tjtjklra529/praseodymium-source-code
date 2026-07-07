package dev.praseodymium.client.mixin;

import dev.praseodymium.client.AdaptivePerformanceController;
import dev.praseodymium.client.PraseodymiumClient;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
	@Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
	private <E extends Entity> void praseodymium$skipHiddenEntities(E entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		AdaptivePerformanceController controller = PraseodymiumClient.controller();
		if (controller == null) {
			return;
		}

		cir.setReturnValue(controller.shouldRenderEntity(entity, cir.getReturnValue()));
	}
}
