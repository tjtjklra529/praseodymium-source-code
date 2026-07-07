package dev.praseodymium.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.shapes.Shapes;

public final class ScanBlockHighlighter {
	private static final int BLUE_COLOR = 0xFF3A7BFF;
	private static final int HOLD_TICKS_REQUIRED = 60;
	private static final int MAX_OUTLINED_BLOCKS = 20000;

	private final Minecraft minecraft;
	private final Set<BlockPos> highlightedBlocks = new HashSet<>();

	private int cHoldTicks;
	private boolean scanTriggeredThisHold;
	private String statusText = "";

	public ScanBlockHighlighter(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	public void tick(boolean altMenuActive) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			resetHold();
			return;
		}

		boolean creative = player.hasInfiniteMaterials();
		boolean cDown = InputConstants.isKeyDown(minecraft.getWindow(), org.lwjgl.glfw.GLFW.GLFW_KEY_C);

		if (!creative || altMenuActive) {
			resetHold();
			return;
		}

		ItemStack heldStack = player.getInventory().getSelectedItem();
		if (!(heldStack.getItem() instanceof BlockItem blockItem)) {
			if (cDown) {
				statusText = "Hold a block item to scan";
			}
			resetHoldStateOnly();
			return;
		}

		if (!cDown) {
			resetHold();
			return;
		}

		if (scanTriggeredThisHold) {
			return;
		}

		cHoldTicks++;
		if (cHoldTicks >= HOLD_TICKS_REQUIRED) {
			runScan(blockItem.getBlock());
			scanTriggeredThisHold = true;
		}
	}

	public void render(LevelRenderContext context) {
		for (BlockPos blockPos : highlightedBlocks) {
			context.submitNodeCollector().submitShapeOutline(
				context.poseStack(),
				Shapes.block().move(blockPos),
				RenderTypes.secondaryBlockOutline(),
				BLUE_COLOR,
				2.5F,
				true
			);
		}
	}

	public Component statusComponent() {
		if (statusText.isEmpty()) {
			return Component.empty();
		}

		return Component.literal(statusText).withColor(0x6CB8FF);
	}

	private void runScan(Block targetBlock) {
		highlightedBlocks.clear();

		LocalPlayer player = minecraft.player;
		ClientChunkCache chunkCache = minecraft.level.getChunkSource();
		int effectiveChunkRadius = Math.min(
			minecraft.options.simulationDistance().get(),
			minecraft.options.renderDistance().get()
		);

		int playerChunkX = player.chunkPosition().x();
		int playerChunkZ = player.chunkPosition().z();
		int minY = minecraft.level.getMinY();
		int maxY = minecraft.level.getMaxY();

		for (int chunkX = playerChunkX - effectiveChunkRadius; chunkX <= playerChunkX + effectiveChunkRadius; chunkX++) {
			for (int chunkZ = playerChunkZ - effectiveChunkRadius; chunkZ <= playerChunkZ + effectiveChunkRadius; chunkZ++) {
				LevelChunk chunk = chunkCache.getChunk(chunkX, chunkZ, null, false);
				if (chunk == null || chunk.isEmpty()) {
					continue;
				}

				int startX = chunkX << 4;
				int startZ = chunkZ << 4;
				for (int y = minY; y < maxY; y++) {
					for (int localX = 0; localX < 16; localX++) {
						for (int localZ = 0; localZ < 16; localZ++) {
							BlockPos blockPos = new BlockPos(startX + localX, y, startZ + localZ);
							if (chunk.getBlockState(blockPos).getBlock() == targetBlock) {
								highlightedBlocks.add(blockPos.immutable());
								if (highlightedBlocks.size() >= MAX_OUTLINED_BLOCKS) {
									statusText = "Scan capped at " + MAX_OUTLINED_BLOCKS + " blocks";
									return;
								}
							}
						}
					}
				}
			}
		}

		statusText = "Scan found " + highlightedBlocks.size() + " matching blocks";
	}

	private void resetHold() {
		resetHoldStateOnly();
		if (!scanTriggeredThisHold) {
			statusText = "";
		}
	}

	private void resetHoldStateOnly() {
		cHoldTicks = 0;
		scanTriggeredThisHold = false;
	}
}
