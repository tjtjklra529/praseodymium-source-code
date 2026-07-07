package dev.praseodymium.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.praseodymium.PraseodymiumMod;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.HotbarManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.inventory.Hotbar;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public final class HotbarSwapperOverlay implements HudElement {
	private static final int HOTBAR_COUNT = 9;
	private static final int SLOT_COUNT = 9;
	private static final int SLOT_SIZE = 20;
	private static final int PADDING = 6;
	private static final int HEADER_HEIGHT = 16;
	private static final int ROW_HEIGHT = SLOT_SIZE + 4;
	private static final int PANEL_WIDTH = SLOT_COUNT * SLOT_SIZE + 44;
	private static final int PANEL_HEIGHT = HEADER_HEIGHT + PADDING + HOTBAR_COUNT * ROW_HEIGHT + PADDING;
	private static final float[] FLIGHT_SPEED_PRESETS = new float[] {0.05F, 0.1F, 0.2F, 0.4F};
	private static final double[] GAMMA_PRESETS = new double[] {0.0D, 0.5D, 1.0D};

	private final Minecraft minecraft;

	private boolean active = false;
	private int originalSelectedSlot = 0;
	private int previewHotbarIndex = 0;
	private int lastScrollSelection = 0;
	private boolean zPressedLastTick;
	private boolean xPressedLastTick;
	private boolean vPressedLastTick;
	private boolean nPressedLastTick;

	public HotbarSwapperOverlay(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	public void tick() {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			active = false;
			return;
		}

		boolean altDown = isAltDown();
		if (!player.hasInfiniteMaterials()) {
			active = false;
			return;
		}

		if (altDown && !active) {
			active = true;
			originalSelectedSlot = player.getInventory().getSelectedSlot();
			previewHotbarIndex = originalSelectedSlot;
			lastScrollSelection = originalSelectedSlot;
			return;
		}

		if (!altDown && active) {
			applyHotbar(player);
			active = false;
			return;
		}

		if (active) {
			handleAltMenuShortcuts(player);
			handleVanillaHotbarKeys();
			handleScrollSelection(player);
		}
	}

	public boolean active() {
		return active;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		renderSpaceStatus(graphics);

		if (!active || minecraft.player == null || minecraft.level == null) {
			return;
		}

		int left = (graphics.guiWidth() - PANEL_WIDTH) / 2;
		int top = (graphics.guiHeight() - PANEL_HEIGHT) / 2;
		Font font = minecraft.font;

		graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xC0101010);
		graphics.outline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFF78D0A0);
		graphics.centeredText(font, Component.literal("Praseodymium Hotbars"), left + PANEL_WIDTH / 2, top + 4, 0xFFFFFF);

		HotbarManager hotbarManager = minecraft.getHotbarManager();
		for (int row = 0; row < HOTBAR_COUNT; row++) {
			int rowTop = top + HEADER_HEIGHT + PADDING + row * ROW_HEIGHT;
			int rowColor = row == previewHotbarIndex ? 0xAA2C5B46 : 0x88303030;
			graphics.fill(left + PADDING, rowTop, left + PANEL_WIDTH - PADDING, rowTop + SLOT_SIZE, rowColor);
			graphics.outline(left + PADDING, rowTop, PANEL_WIDTH - PADDING * 2, SLOT_SIZE, 0xFF404040);
			graphics.text(font, Integer.toString(row + 1), left + 10, rowTop + 6, 0xFFFFFF);

			Hotbar hotbar = hotbarManager.get(row);
			List<ItemStack> items = hotbar.load(minecraft.level.registryAccess());
			for (int slot = 0; slot < SLOT_COUNT; slot++) {
				int slotLeft = left + 28 + slot * SLOT_SIZE;
				int slotTop = rowTop + 2;
				graphics.fill(slotLeft - 1, slotTop - 1, slotLeft + 17, slotTop + 17, 0x88383838);

				ItemStack stack = items.get(slot);
				if (!stack.isEmpty()) {
					graphics.item(stack, slotLeft, slotTop);
					graphics.itemDecorations(font, stack, slotLeft, slotTop);
				}
			}
		}

		graphics.centeredText(
			font,
			Component.literal("Hold Alt, use 1-9 or scroll, release Alt to load"),
			left + PANEL_WIDTH / 2,
			top + PANEL_HEIGHT - 12,
			0xD0D0D0
		);

		int infoLeft = left + PANEL_WIDTH + 12;
		graphics.fill(infoLeft, top, infoLeft + 180, top + 112, 0xB0101010);
		graphics.outline(infoLeft, top, 180, 112, 0xFF78D0A0);
		graphics.text(font, "Axiom-style Utility", infoLeft + 8, top + 6, 0xFFFFFF);
		graphics.text(font, shortcutLabel("Z", "Occlusion", PraseodymiumClient.controller().occlusionCullingEnabled() ? "ON" : "OFF"), infoLeft + 8, top + 24, 0xD0D0D0);
		graphics.text(font, shortcutLabel("X", "Brightness", gammaPercent() + "%"), infoLeft + 8, top + 38, 0xD0D0D0);
		graphics.text(font, shortcutLabel("V", "Flight", flightPercent() + "%"), infoLeft + 8, top + 52, 0xD0D0D0);
		graphics.text(font, shortcutLabel("H", "Space Check", PraseodymiumClient.enclosedSpaceChecker().active() ? "ON" : "OFF"), infoLeft + 8, top + 66, 0xD0D0D0);
		graphics.text(font, shortcutLabel("N", "Save row", Integer.toString(previewHotbarIndex + 1)), infoLeft + 8, top + 80, 0xD0D0D0);
		graphics.text(font, "Release Alt to load row", infoLeft + 8, top + 94, 0xA0E0C0);
	}

	private void applyHotbar(LocalPlayer player) {
		int chosenHotbar = previewHotbarIndex;
		CreativeModeInventoryScreen.handleHotbarLoadOrSave(minecraft, chosenHotbar, true, false);
		player.getInventory().setSelectedSlot(originalSelectedSlot);
		PraseodymiumMod.LOGGER.info("Praseodymium loaded saved hotbar {}.", chosenHotbar + 1);
	}

	private boolean isAltDown() {
		return InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_LEFT_ALT)
			|| InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_RIGHT_ALT);
	}

	private void handleAltMenuShortcuts(LocalPlayer player) {
		boolean zPressed = InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_Z);
		boolean xPressed = InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_X);
		boolean vPressed = InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_V);
		boolean nPressed = InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_N);

		if (zPressed && !zPressedLastTick) {
			boolean next = !PraseodymiumClient.controller().occlusionCullingEnabled();
			PraseodymiumClient.controller().setOcclusionCullingEnabled(next);
		}

		if (xPressed && !xPressedLastTick) {
			cycleGamma();
		}

		if (vPressed && !vPressedLastTick) {
			cycleFlightSpeed(player);
		}

		if (nPressed && !nPressedLastTick) {
			CreativeModeInventoryScreen.handleHotbarLoadOrSave(minecraft, previewHotbarIndex, false, true);
			PraseodymiumMod.LOGGER.info("Praseodymium saved current hotbar to row {}.", previewHotbarIndex + 1);
		}

		zPressedLastTick = zPressed;
		xPressedLastTick = xPressed;
		vPressedLastTick = vPressed;
		nPressedLastTick = nPressed;
	}

	private void handleVanillaHotbarKeys() {
		for (int i = 0; i < minecraft.options.keyHotbarSlots.length; i++) {
			if (minecraft.options.keyHotbarSlots[i].consumeClick()) {
				previewHotbarIndex = i;
				lastScrollSelection = i;
			}
		}
	}

	private void handleScrollSelection(LocalPlayer player) {
		int currentSelection = clampHotbarIndex(player.getInventory().getSelectedSlot());
		if (currentSelection != lastScrollSelection) {
			previewHotbarIndex = currentSelection;
			lastScrollSelection = currentSelection;
			player.getInventory().setSelectedSlot(originalSelectedSlot);
		}
	}

	private void cycleGamma() {
		double current = minecraft.options.gamma().get();
		int nextIndex = 0;
		for (int i = 0; i < GAMMA_PRESETS.length; i++) {
			if (current < GAMMA_PRESETS[i] + 0.001D) {
				nextIndex = (i + 1) % GAMMA_PRESETS.length;
				minecraft.options.gamma().set(GAMMA_PRESETS[nextIndex]);
				return;
			}
		}

		minecraft.options.gamma().set(GAMMA_PRESETS[0]);
	}

	private void cycleFlightSpeed(LocalPlayer player) {
		float current = player.getAbilities().getFlyingSpeed();
		int nextIndex = 0;
		for (int i = 0; i < FLIGHT_SPEED_PRESETS.length; i++) {
			if (current <= FLIGHT_SPEED_PRESETS[i] + 0.0001F) {
				nextIndex = (i + 1) % FLIGHT_SPEED_PRESETS.length;
				player.getAbilities().setFlyingSpeed(FLIGHT_SPEED_PRESETS[nextIndex]);
				player.onUpdateAbilities();
				return;
			}
		}

		player.getAbilities().setFlyingSpeed(FLIGHT_SPEED_PRESETS[0]);
		player.onUpdateAbilities();
	}

	private void renderSpaceStatus(GuiGraphicsExtractor graphics) {
		Component status = PraseodymiumClient.enclosedSpaceChecker().statusComponent();
		if (status.getString().isEmpty()) {
			status = PraseodymiumClient.scanBlockHighlighter().statusComponent();
			if (status.getString().isEmpty()) {
				return;
			}
		}

		int y = graphics.guiHeight() - 72;
		graphics.centeredText(minecraft.font, status, graphics.guiWidth() / 2, y, 0xFFFFFF);
	}

	private int gammaPercent() {
		return (int) Math.round(minecraft.options.gamma().get() * 100.0D);
	}

	private int flightPercent() {
		if (minecraft.player == null) {
			return 100;
		}

		return Math.round((minecraft.player.getAbilities().getFlyingSpeed() / 0.05F) * 100.0F);
	}

	private static String shortcutLabel(String key, String label, String value) {
		return "[" + key + "] " + label + ": " + value;
	}

	private static int clampHotbarIndex(int index) {
		return Math.max(0, Math.min(HOTBAR_COUNT - 1, index));
	}
}
