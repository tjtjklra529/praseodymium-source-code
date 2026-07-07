package dev.praseodymium.client;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.Shapes;

public final class EnclosedSpaceChecker {
	private static final int SCAN_RADIUS = 16;
	private static final int MAX_VISITED_BLOCKS = 4096;
	private static final int BLUE_COLOR = 0xFF3A7BFF;
	private static final int RED_COLOR = 0xFFFF4040;

	private final Minecraft minecraft;
	private final Set<BlockPos> wallBlocks = new HashSet<>();
	private final Set<BlockPos> gapBlocks = new HashSet<>();

	private boolean active;
	private boolean fullyEnclosed;
	private String statusText = "Space check inactive";

	public EnclosedSpaceChecker(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	public void toggle() {
		active = !active;
		if (!active) {
			wallBlocks.clear();
			gapBlocks.clear();
			statusText = "Space check inactive";
			return;
		}

		scanCurrentTarget();
	}

	public void tick() {
		if (!active) {
			return;
		}

		if (minecraft.level == null || minecraft.player == null) {
			statusText = "Space check unavailable";
			wallBlocks.clear();
			gapBlocks.clear();
			return;
		}

		if (minecraft.level.getGameTime() % 5L == 0L) {
			scanCurrentTarget();
		}
	}

	public void render(LevelRenderContext context) {
		if (!active) {
			return;
		}

		for (BlockPos wallBlock : wallBlocks) {
			context.submitNodeCollector().submitShapeOutline(
				context.poseStack(),
				Shapes.block().move(wallBlock),
				RenderTypes.linesTranslucent(),
				BLUE_COLOR,
				2.0F,
				false
			);
		}

		for (BlockPos gapBlock : gapBlocks) {
			context.submitNodeCollector().submitShapeOutline(
				context.poseStack(),
				Shapes.block().move(gapBlock),
				RenderTypes.linesTranslucent(),
				RED_COLOR,
				2.5F,
				false
			);
		}
	}

	public boolean active() {
		return active;
	}

	public boolean fullyEnclosed() {
		return fullyEnclosed;
	}

	public Component statusComponent() {
		if (!active) {
			return Component.empty();
		}

		int color = fullyEnclosed ? 0x6CFF8F : 0xFF6C6C;
		return Component.literal(statusText).withColor(color);
	}

	private void scanCurrentTarget() {
		wallBlocks.clear();
		gapBlocks.clear();

		if (!(minecraft.hitResult instanceof BlockHitResult blockHitResult) || minecraft.level == null) {
			statusText = "Look at a wall or room edge";
			fullyEnclosed = false;
			return;
		}

		BlockPos seed = findSeedSpace(blockHitResult);
		if (seed == null) {
			statusText = "No open space to check";
			fullyEnclosed = false;
			return;
		}

		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		Set<BlockPos> visited = new HashSet<>();
		queue.add(seed);
		visited.add(seed);

		boolean escaped = false;

		while (!queue.isEmpty() && visited.size() <= MAX_VISITED_BLOCKS) {
			BlockPos current = queue.removeFirst();
			if (isOutsideRadius(seed, current)) {
				escaped = true;
				gapBlocks.add(current.immutable());
				continue;
			}

			for (Direction direction : Direction.values()) {
				BlockPos next = current.relative(direction);
				BlockState nextState = minecraft.level.getBlockState(next);

				if (isEnclosingBlock(nextState, next)) {
					wallBlocks.add(next.immutable());
					continue;
				}

				if (visited.add(next.immutable())) {
					queue.add(next.immutable());
				}
			}
		}

		if (visited.size() > MAX_VISITED_BLOCKS) {
			escaped = true;
		}

		fullyEnclosed = !escaped;
		statusText = fullyEnclosed ? "Space is fully enclosed" : "Space is not fully enclosed";
	}

	private BlockPos findSeedSpace(BlockHitResult hitResult) {
		BlockPos hit = hitResult.getBlockPos();
		BlockPos nearSide = hit.relative(hitResult.getDirection().getOpposite());
		if (isOpenSpace(nearSide)) {
			return nearSide;
		}

		BlockPos farSide = hit.relative(hitResult.getDirection());
		if (isOpenSpace(farSide)) {
			return farSide;
		}

		if (isOpenSpace(hit)) {
			return hit;
		}

		return null;
	}

	private boolean isOpenSpace(BlockPos blockPos) {
		BlockState state = minecraft.level.getBlockState(blockPos);
		return !isEnclosingBlock(state, blockPos);
	}

	private boolean isEnclosingBlock(BlockState state, BlockPos pos) {
		return !state.isAir() && state.canOcclude() && state.blocksMotion() && !state.getCollisionShape(minecraft.level, pos).isEmpty();
	}

	private static boolean isOutsideRadius(BlockPos seed, BlockPos current) {
		return Math.abs(current.getX() - seed.getX()) > SCAN_RADIUS
			|| Math.abs(current.getY() - seed.getY()) > SCAN_RADIUS
			|| Math.abs(current.getZ() - seed.getZ()) > SCAN_RADIUS;
	}
}
