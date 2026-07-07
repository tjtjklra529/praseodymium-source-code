package dev.praseodymium.client;

import dev.praseodymium.PraseodymiumMod;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class AdaptivePerformanceController {
	private static final long ONE_SECOND_NANOS = 1_000_000_000L;
	private static final long CACHE_PRUNE_INTERVAL_TICKS = 200L;
	private static final long CACHE_ENTRY_TTL_TICKS = 600L;

	private static final double PERFORMANCE_TARGET_FPS = 175.0D;
	private static final double DEFENSE_TARGET_FPS = 35.0D;
	private static final double EMERGENCY_FLOOR_FPS = 20.0D;
	private static final double EMERGENCY_FRAME_TIME_MS = 50.0D;
	private static final double DEFENSE_FRAME_TIME_MS = 1000.0D / DEFENSE_TARGET_FPS;
	private static final double TARGET_FRAME_TIME_MS = 1000.0D / PERFORMANCE_TARGET_FPS;

	private static final int STAGE_COUNT = 4;
	private static final int HIGH_PARTICLE_BUDGET = 4096;
	private static final int MEDIUM_PARTICLE_BUDGET = 2048;
	private static final int LOW_PARTICLE_BUDGET = 1024;
	private static final int EMERGENCY_PARTICLE_BUDGET = 384;
	private static final double BLOCK_ENTITY_ANIMATION_DISTANCE = 16.0D;
	private static final double THROTTLE_DISTANCE_STAGE_ONE = 24.0D;
	private static final double THROTTLE_DISTANCE_STAGE_TWO = 40.0D;
	private static final double MINOR_ENTITY_CULL_DISTANCE = 18.0D;
	private static final int COMPILATION_FREEZE_TICKS = 5;

	private final PraseodymiumConfig config;
	private final Minecraft minecraft;
	private final Map<Integer, VisibilityCacheEntry> visibilityCache = new HashMap<>();

	private boolean occlusionCullingEnabled;

	private long lastFrameTime = -1L;
	private long lastAdjustmentTime = 0L;
	private long lastCachePruneTick = 0L;
	private long frameCounter = 0L;
	private double smoothedFrameTimeMs = TARGET_FRAME_TIME_MS;
	private double smoothedFps = PERFORMANCE_TARGET_FPS;
	private int recoverySeconds = 0;
	private int geometryCompilationFreezeTicks = 0;
	private int stage = 0;

	private ParticleStatus baselineParticles;
	private CloudStatus baselineClouds;
	private boolean baselineAmbientOcclusion;
	private boolean baselineEntityShadows;
	private int baselineBiomeBlendRadius;
	private int baselineMipmapLevels;
	private double baselineEntityDistanceScaling;
	private PrioritizeChunkUpdates baselineChunkUpdates;

	public AdaptivePerformanceController(PraseodymiumConfig config, Minecraft minecraft) {
		this.config = config;
		this.minecraft = minecraft;
		this.occlusionCullingEnabled = config.occlusionCullingEnabled;
		captureBaseline();
	}

	public void onFrameRendered() {
		frameCounter++;

		long now = System.nanoTime();
		if (lastFrameTime < 0L) {
			lastFrameTime = now;
			return;
		}

		long delta = now - lastFrameTime;
		lastFrameTime = now;

		if (delta <= 0L || delta > ONE_SECOND_NANOS) {
			return;
		}

		double frameTimeMs = delta / 1_000_000.0D;
		smoothedFrameTimeMs = (smoothedFrameTimeMs * 0.88D) + (frameTimeMs * 0.12D);
		smoothedFps = 1000.0D / smoothedFrameTimeMs;

		if (frameTimeMs > EMERGENCY_FRAME_TIME_MS) {
			geometryCompilationFreezeTicks = COMPILATION_FREEZE_TICKS;
		}
	}

	public void onClientTick() {
		if (minecraft.player == null || minecraft.level == null) {
			captureBaseline();
			stage = 0;
			geometryCompilationFreezeTicks = 0;
			return;
		}

		if (geometryCompilationFreezeTicks > 0) {
			geometryCompilationFreezeTicks--;
		}

		long now = System.nanoTime();
		long cooldown = config.adjustmentCooldownSeconds * ONE_SECOND_NANOS;
		if ((now - lastAdjustmentTime) < cooldown) {
			return;
		}

		lastAdjustmentTime = now;

		int desiredStage = desiredStageForCurrentFramePacing();
		if (desiredStage > stage) {
			recoverySeconds = 0;
			stage = desiredStage;
			applyStage(stage);
			PraseodymiumMod.LOGGER.info("Praseodymium entered optimization stage {} ({} FPS, {:.1f} ms).", stage, Math.round(smoothedFps), smoothedFrameTimeMs);
			return;
		}

		if (desiredStage < stage && smoothedFps > DEFENSE_TARGET_FPS) {
			recoverySeconds += config.adjustmentCooldownSeconds;
			if (recoverySeconds >= config.recoveryHoldSeconds) {
				recoverySeconds = 0;
				stage = Math.max(desiredStage, stage - 1);
				applyStage(stage);
				PraseodymiumMod.LOGGER.info("Praseodymium relaxed to stage {} ({} FPS, {:.1f} ms).", stage, Math.round(smoothedFps), smoothedFrameTimeMs);
			}
			return;
		}

		recoverySeconds = 0;
	}

	public boolean shouldRenderEntity(Entity entity, boolean vanillaDecision) {
		if (!vanillaDecision || minecraft.level == null || minecraft.player == null) {
			return vanillaDecision;
		}

		if (shouldCullMinorEntity(entity)) {
			return false;
		}

		if (!occlusionCullingEnabled) {
			return vanillaDecision;
		}

		Entity cameraEntity = minecraft.getCameraEntity();
		if (cameraEntity == null || entity == cameraEntity || entity.isCurrentlyGlowing()) {
			return vanillaDecision;
		}

		if (entity.isRemoved() || entity.tickCount < 5) {
			return vanillaDecision;
		}

		Vec3 cameraPosition = minecraft.gameRenderer.mainCamera().position();
		double distanceSquared = entity.distanceToSqr(cameraPosition);
		double minimumDistanceSquared = config.occlusionMinDistance * config.occlusionMinDistance;
		double maximumDistanceSquared = config.occlusionMaxDistance * config.occlusionMaxDistance;
		if (distanceSquared < minimumDistanceSquared || distanceSquared > maximumDistanceSquared) {
			return vanillaDecision;
		}

		long gameTime = minecraft.level.getGameTime();
		VisibilityCacheEntry cached = visibilityCache.get(entity.getId());
		if (cached != null && cached.nextEvaluationTick > gameTime) {
			cached.lastTouchedTick = gameTime;
			return cached.visible;
		}

		boolean visible = hasVisibleSample(cameraPosition, cameraEntity, entity);
		int recheckTicks = visible ? config.visibleEntityRecheckTicks : config.hiddenEntityRecheckTicks;
		visibilityCache.put(entity.getId(), new VisibilityCacheEntry(visible, gameTime + recheckTicks, gameTime));
		pruneVisibilityCache(gameTime);
		return visible;
	}

	public boolean shouldPauseBlockEntityAnimation(double distanceSquaredToPlayer) {
		return stage >= 2 && distanceSquaredToPlayer > (BLOCK_ENTITY_ANIMATION_DISTANCE * BLOCK_ENTITY_ANIMATION_DISTANCE);
	}

	public boolean shouldSkipEntityRenderUpdate(Entity entity) {
		if (minecraft.player == null || stage <= 0) {
			return false;
		}

		double distanceSquared = entity.distanceToSqr(minecraft.player);
		if (stage >= 3 && distanceSquared > (THROTTLE_DISTANCE_STAGE_TWO * THROTTLE_DISTANCE_STAGE_TWO)) {
			return (frameCounter % 3L) != 0L;
		}

		if (stage >= 1 && distanceSquared > (THROTTLE_DISTANCE_STAGE_ONE * THROTTLE_DISTANCE_STAGE_ONE)) {
			return (frameCounter % 2L) != 0L;
		}

		return false;
	}

	public int particleBudget() {
		return switch (stage) {
			case 0 -> HIGH_PARTICLE_BUDGET;
			case 1 -> MEDIUM_PARTICLE_BUDGET;
			case 2 -> LOW_PARTICLE_BUDGET;
			default -> EMERGENCY_PARTICLE_BUDGET;
		};
	}

	public boolean shouldFreezeChunkCompilation() {
		return geometryCompilationFreezeTicks > 0;
	}

	public boolean emergencyModeActive() {
		return smoothedFps <= EMERGENCY_FLOOR_FPS || smoothedFrameTimeMs >= EMERGENCY_FRAME_TIME_MS;
	}

	public double smoothedFps() {
		return smoothedFps;
	}

	public int currentStage() {
		return stage;
	}

	public boolean occlusionCullingEnabled() {
		return occlusionCullingEnabled;
	}

	public void setOcclusionCullingEnabled(boolean occlusionCullingEnabled) {
		this.occlusionCullingEnabled = occlusionCullingEnabled;
	}

	private int desiredStageForCurrentFramePacing() {
		if (smoothedFps <= EMERGENCY_FLOOR_FPS || smoothedFrameTimeMs >= EMERGENCY_FRAME_TIME_MS) {
			return 4;
		}

		if (smoothedFps < DEFENSE_TARGET_FPS) {
			return 3;
		}

		if (smoothedFrameTimeMs > 24.0D) {
			return 2;
		}

		if (smoothedFrameTimeMs > 14.0D) {
			return 1;
		}

		return 0;
	}

	private boolean shouldCullMinorEntity(Entity entity) {
		if (stage < 3 || minecraft.player == null) {
			return false;
		}

		boolean isMinorEntity = entity instanceof ItemEntity || entity instanceof ItemFrame || entity instanceof GlowItemFrame || entity instanceof ArmorStand;
		if (!isMinorEntity) {
			return false;
		}

		return entity.distanceToSqr(minecraft.player) > (MINOR_ENTITY_CULL_DISTANCE * MINOR_ENTITY_CULL_DISTANCE);
	}

	private void applyStage(int nextStage) {
		restoreBaselineValues();

		if (nextStage >= 1) {
			minecraft.options.particles().set(ParticleStatus.MINIMAL);
			minecraft.options.prioritizeChunkUpdates().set(PrioritizeChunkUpdates.NEARBY);
		}

		if (nextStage >= 2) {
			minecraft.options.entityShadows().set(false);
			minecraft.options.cloudStatus().set(CloudStatus.OFF);
			minecraft.options.ambientOcclusion().set(false);
		}

		if (nextStage >= 3) {
			minecraft.options.biomeBlendRadius().set(0);
			minecraft.options.mipmapLevels().set(0);
			minecraft.options.entityDistanceScaling().set(Math.max(0.55D, baselineEntityDistanceScaling * 0.72D));
		}

		if (nextStage >= 4) {
			minecraft.options.biomeBlendRadius().set(0);
			minecraft.options.mipmapLevels().set(0);
			minecraft.options.entityDistanceScaling().set(Math.max(0.4D, baselineEntityDistanceScaling * 0.55D));
			minecraft.options.prioritizeChunkUpdates().set(PrioritizeChunkUpdates.NEARBY);
		}
	}

	private void restoreBaselineValues() {
		minecraft.options.particles().set(baselineParticles);
		minecraft.options.cloudStatus().set(baselineClouds);
		minecraft.options.ambientOcclusion().set(baselineAmbientOcclusion);
		minecraft.options.entityShadows().set(baselineEntityShadows);
		minecraft.options.biomeBlendRadius().set(baselineBiomeBlendRadius);
		minecraft.options.mipmapLevels().set(baselineMipmapLevels);
		minecraft.options.entityDistanceScaling().set(baselineEntityDistanceScaling);
		minecraft.options.prioritizeChunkUpdates().set(baselineChunkUpdates);
	}

	private void captureBaseline() {
		baselineParticles = minecraft.options.particles().get();
		baselineClouds = minecraft.options.cloudStatus().get();
		baselineAmbientOcclusion = minecraft.options.ambientOcclusion().get();
		baselineEntityShadows = minecraft.options.entityShadows().get();
		baselineBiomeBlendRadius = minecraft.options.biomeBlendRadius().get();
		baselineMipmapLevels = minecraft.options.mipmapLevels().get();
		baselineEntityDistanceScaling = minecraft.options.entityDistanceScaling().get();
		baselineChunkUpdates = minecraft.options.prioritizeChunkUpdates().get();
	}

	private boolean hasVisibleSample(Vec3 cameraPosition, Entity cameraEntity, Entity target) {
		AABB bounds = target.getBoundingBox();
		Vec3 center = bounds.getCenter();
		double safeTop = Math.max(bounds.minY, bounds.maxY - 0.1D);
		double safeBottom = Math.min(bounds.maxY, bounds.minY + 0.25D);
		Vec3 upper = new Vec3(center.x, safeTop, center.z);
		Vec3 lower = new Vec3(center.x, safeBottom, center.z);

		return hasClearLine(cameraPosition, center, cameraEntity)
			|| hasClearLine(cameraPosition, upper, cameraEntity)
			|| hasClearLine(cameraPosition, lower, cameraEntity);
	}

	private boolean hasClearLine(Vec3 from, Vec3 to, Entity cameraEntity) {
		HitResult hitResult = minecraft.level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, cameraEntity));
		if (hitResult.getType() == HitResult.Type.MISS) {
			return true;
		}

		double targetDistanceSquared = from.distanceToSqr(to);
		double hitDistanceSquared = from.distanceToSqr(hitResult.getLocation());
		return hitDistanceSquared + 0.04D >= targetDistanceSquared;
	}

	private void pruneVisibilityCache(long gameTime) {
		if ((gameTime - lastCachePruneTick) < CACHE_PRUNE_INTERVAL_TICKS) {
			return;
		}

		lastCachePruneTick = gameTime;
		Iterator<Map.Entry<Integer, VisibilityCacheEntry>> iterator = visibilityCache.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Integer, VisibilityCacheEntry> entry = iterator.next();
			if ((gameTime - entry.getValue().lastTouchedTick) > CACHE_ENTRY_TTL_TICKS) {
				iterator.remove();
			}
		}
	}

	private static final class VisibilityCacheEntry {
		private final boolean visible;
		private final long nextEvaluationTick;
		private long lastTouchedTick;

		private VisibilityCacheEntry(boolean visible, long nextEvaluationTick, long lastTouchedTick) {
			this.visible = visible;
			this.nextEvaluationTick = nextEvaluationTick;
			this.lastTouchedTick = lastTouchedTick;
		}
	}
}
