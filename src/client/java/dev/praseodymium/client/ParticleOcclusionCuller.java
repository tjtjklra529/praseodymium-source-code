package dev.praseodymium.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class ParticleOcclusionCuller {
	private static final long CACHE_TTL_TICKS = 40L;

	private final Map<Long, CacheEntry> cache = new HashMap<>();
	private long lastPruneTick;

	public boolean shouldCull(ClientLevel level, Camera camera, double x, double y, double z) {
		Entity cameraEntity = camera.entity();
		if (cameraEntity == null) {
			return false;
		}

		Vec3 cameraPosition = camera.position();
		Vec3 particlePosition = new Vec3(x, y, z);
		if (cameraPosition.distanceToSqr(particlePosition) < 2.25D) {
			return false;
		}

		long gameTime = level.getGameTime();
		BlockPos cameraBlock = camera.blockPosition();
		BlockPos particleBlock = BlockPos.containing(particlePosition);
		long key = makeKey(cameraBlock, particleBlock);

		CacheEntry cached = cache.get(key);
		if (cached != null && cached.expireTick > gameTime) {
			cached.lastTouchedTick = gameTime;
			return cached.culled;
		}

		HitResult hitResult = level.clip(new ClipContext(cameraPosition, particlePosition, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, cameraEntity));
		boolean culled = hitResult.getType() != HitResult.Type.MISS
			&& cameraPosition.distanceToSqr(hitResult.getLocation()) + 0.04D < cameraPosition.distanceToSqr(particlePosition);

		cache.put(key, new CacheEntry(culled, gameTime + 5L, gameTime));
		prune(gameTime);
		return culled;
	}

	private void prune(long gameTime) {
		if (gameTime - lastPruneTick < 20L) {
			return;
		}

		lastPruneTick = gameTime;
		Iterator<Map.Entry<Long, CacheEntry>> iterator = cache.entrySet().iterator();
		while (iterator.hasNext()) {
			if (gameTime - iterator.next().getValue().lastTouchedTick > CACHE_TTL_TICKS) {
				iterator.remove();
			}
		}
	}

	private static long makeKey(BlockPos camera, BlockPos particle) {
		long a = camera.asLong();
		long b = particle.asLong();
		return a * 31L + b;
	}

	private static final class CacheEntry {
		private final boolean culled;
		private final long expireTick;
		private long lastTouchedTick;

		private CacheEntry(boolean culled, long expireTick, long lastTouchedTick) {
			this.culled = culled;
			this.expireTick = expireTick;
			this.lastTouchedTick = lastTouchedTick;
		}
	}
}
