package dev.praseodymium.client;

import dev.praseodymium.PraseodymiumMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class PraseodymiumClient implements ClientModInitializer {
	private static AdaptivePerformanceController controller;
	private static HotbarSwapperOverlay hotbarSwapperOverlay;
	private static EnclosedSpaceChecker enclosedSpaceChecker;
	private static ScanBlockHighlighter scanBlockHighlighter;
	private static KeyMapping enclosedSpaceKey;

	public static AdaptivePerformanceController controller() {
		return controller;
	}

	public static EnclosedSpaceChecker enclosedSpaceChecker() {
		return enclosedSpaceChecker;
	}

	public static ScanBlockHighlighter scanBlockHighlighter() {
		return scanBlockHighlighter;
	}

	@Override
	public void onInitializeClient() {
		ModelLoadingPlugin.register(new ConnectedGlassModelPlugin());
		PraseodymiumConfig config = PraseodymiumConfig.load();
		Minecraft minecraft = Minecraft.getInstance();
		controller = new AdaptivePerformanceController(config, minecraft);
		enclosedSpaceChecker = new EnclosedSpaceChecker(minecraft);
		scanBlockHighlighter = new ScanBlockHighlighter(minecraft);
		hotbarSwapperOverlay = new HotbarSwapperOverlay(minecraft);
		enclosedSpaceKey = KeyMappingHelper.registerKeyMapping(
			new KeyMapping(
				"key.praseodymium.enclosed_space_check",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_H,
				KeyMapping.Category.MISC
			)
		);

		LevelRenderEvents.END_MAIN.register(context -> controller.onFrameRendered());
		LevelRenderEvents.END_MAIN.register(context -> enclosedSpaceChecker.render(context));
		LevelRenderEvents.END_MAIN.register(context -> scanBlockHighlighter.render(context));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			controller.onClientTick();
			while (enclosedSpaceKey.consumeClick()) {
				enclosedSpaceChecker.toggle();
			}
			enclosedSpaceChecker.tick();
			hotbarSwapperOverlay.tick();
			scanBlockHighlighter.tick(hotbarSwapperOverlay.active());
		});
		HudElementRegistry.addLast(PraseodymiumMod.id("hotbar_swapper"), hotbarSwapperOverlay);

		PraseodymiumMod.LOGGER.info(
			"Praseodymium armed with {} FPS target, render {}-{}, simulation {}-{}, occlusion culling {}.",
			config.targetMinFps,
			config.minimumRenderDistance,
			config.maximumRenderDistance,
			config.minimumSimulationDistance,
			config.maximumSimulationDistance,
			config.occlusionCullingEnabled ? "enabled" : "disabled"
		);
	}
}
