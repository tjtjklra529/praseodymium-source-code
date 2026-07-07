package dev.praseodymium.client;

import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.Predicate;

public final class ConnectedGlassBlockModel extends WrapperBlockStateModel {
	private static final float PIXEL = 1.0F / 16.0F;
	private static final float INNER_MIN = PIXEL;
	private static final float INNER_MAX = 1.0F - PIXEL;
	private static final int WHITE = -1;

	public ConnectedGlassBlockModel(BlockStateModel wrapped) {
		super(wrapped);
	}

	@Override
	public void emitQuads(QuadEmitter emitter, BlockAndTintGetter blockView, BlockPos pos, BlockState state, RandomSource random, Predicate<Direction> cullTest) {
		Material.Baked material = wrapped.particleMaterial(blockView, pos, state);

		for (Direction face : Direction.values()) {
			if (!cullTest.test(face)) {
				continue;
			}

			float depth = faceDepth(face);
			emitQuad(emitter, material, face, INNER_MIN, INNER_MIN, INNER_MAX, INNER_MAX, depth, INNER_MIN, INNER_MIN, INNER_MAX, INNER_MAX);

			if (!isSameGlass(blockView, pos.relative(facePlaneLeft(face)), state)) {
				emitQuad(emitter, material, face, 0.0F, INNER_MIN, INNER_MIN, INNER_MAX, depth, 0.0F, INNER_MIN, INNER_MIN, INNER_MAX);
			}

			if (!isSameGlass(blockView, pos.relative(facePlaneRight(face)), state)) {
				emitQuad(emitter, material, face, INNER_MAX, INNER_MIN, 1.0F, INNER_MAX, depth, INNER_MAX, INNER_MIN, 1.0F, INNER_MAX);
			}

			if (!isSameGlass(blockView, pos.relative(facePlaneUp(face)), state)) {
				emitQuad(emitter, material, face, INNER_MIN, INNER_MAX, INNER_MAX, 1.0F, depth, INNER_MIN, INNER_MAX, INNER_MAX, 1.0F);
			}

			if (!isSameGlass(blockView, pos.relative(facePlaneDown(face)), state)) {
				emitQuad(emitter, material, face, INNER_MIN, 0.0F, INNER_MAX, INNER_MIN, depth, INNER_MIN, 0.0F, INNER_MAX, INNER_MIN);
			}
		}
	}

	static boolean isConnectedGlass(BlockState state) {
		Block block = state.getBlock();
		return block == Blocks.GLASS
			|| block == Blocks.TINTED_GLASS
			|| isStainedGlass(block);
	}

	private static boolean isSameGlass(BlockAndTintGetter blockView, BlockPos pos, BlockState state) {
		return blockView.getBlockState(pos).getBlock() == state.getBlock();
	}

	private static boolean isStainedGlass(Block block) {
		for (Block stained : Blocks.STAINED_GLASS.asList()) {
			if (stained == block) {
				return true;
			}
		}

		return false;
	}

	private static void emitQuad(
		QuadEmitter emitter,
		Material.Baked material,
		Direction face,
		float left,
		float bottom,
		float right,
		float top,
		float depth,
		float uMin,
		float vMin,
		float uMax,
		float vMax
	) {
		emitter.clear()
			.square(face, left, bottom, right, top, depth)
			.nominalFace(face)
			.cullFace(face)
			.chunkLayer(ChunkSectionLayer.TRANSLUCENT)
			.color(0, WHITE, WHITE, WHITE)
			.uv(0, uMin, vMin)
			.uv(1, uMin, vMax)
			.uv(2, uMax, vMax)
			.uv(3, uMax, vMin)
			.materialBake(material, MutableQuadView.BAKE_NORMALIZED)
			.emit();
	}

	private static float faceDepth(Direction face) {
		return switch (face) {
			case DOWN, NORTH, WEST -> 0.0F;
			case UP, SOUTH, EAST -> 1.0F;
		};
	}

	private static Direction facePlaneLeft(Direction face) {
		return switch (face) {
			case NORTH -> Direction.WEST;
			case SOUTH -> Direction.EAST;
			case EAST -> Direction.SOUTH;
			case WEST -> Direction.NORTH;
			case UP, DOWN -> Direction.WEST;
		};
	}

	private static Direction facePlaneRight(Direction face) {
		return switch (face) {
			case NORTH -> Direction.EAST;
			case SOUTH -> Direction.WEST;
			case EAST -> Direction.NORTH;
			case WEST -> Direction.SOUTH;
			case UP, DOWN -> Direction.EAST;
		};
	}

	private static Direction facePlaneUp(Direction face) {
		return switch (face) {
			case UP -> Direction.NORTH;
			case DOWN -> Direction.SOUTH;
			default -> Direction.UP;
		};
	}

	private static Direction facePlaneDown(Direction face) {
		return switch (face) {
			case UP -> Direction.SOUTH;
			case DOWN -> Direction.NORTH;
			default -> Direction.DOWN;
		};
	}
}
