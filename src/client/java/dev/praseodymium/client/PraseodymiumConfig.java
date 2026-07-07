package dev.praseodymium.client;

import dev.praseodymium.PraseodymiumMod;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

public final class PraseodymiumConfig {
	private static final String FILE_NAME = "praseodymium.properties";

	public final int targetMinFps;
	public final int recoveryFps;
	public final int minimumRenderDistance;
	public final int maximumRenderDistance;
	public final int minimumSimulationDistance;
	public final int maximumSimulationDistance;
	public final int adjustmentCooldownSeconds;
	public final int recoveryHoldSeconds;
	public final double minimumEntityDistanceScaling;
	public final boolean occlusionCullingEnabled;
	public final int occlusionMinDistance;
	public final int occlusionMaxDistance;
	public final int visibleEntityRecheckTicks;
	public final int hiddenEntityRecheckTicks;

	private PraseodymiumConfig(
		int targetMinFps,
		int recoveryFps,
		int minimumRenderDistance,
		int maximumRenderDistance,
		int minimumSimulationDistance,
		int maximumSimulationDistance,
		int adjustmentCooldownSeconds,
		int recoveryHoldSeconds,
		double minimumEntityDistanceScaling,
		boolean occlusionCullingEnabled,
		int occlusionMinDistance,
		int occlusionMaxDistance,
		int visibleEntityRecheckTicks,
		int hiddenEntityRecheckTicks
	) {
		this.targetMinFps = targetMinFps;
		this.recoveryFps = recoveryFps;
		this.minimumRenderDistance = minimumRenderDistance;
		this.maximumRenderDistance = maximumRenderDistance;
		this.minimumSimulationDistance = minimumSimulationDistance;
		this.maximumSimulationDistance = maximumSimulationDistance;
		this.adjustmentCooldownSeconds = adjustmentCooldownSeconds;
		this.recoveryHoldSeconds = recoveryHoldSeconds;
		this.minimumEntityDistanceScaling = minimumEntityDistanceScaling;
		this.occlusionCullingEnabled = occlusionCullingEnabled;
		this.occlusionMinDistance = occlusionMinDistance;
		this.occlusionMaxDistance = occlusionMaxDistance;
		this.visibleEntityRecheckTicks = visibleEntityRecheckTicks;
		this.hiddenEntityRecheckTicks = hiddenEntityRecheckTicks;
	}

	public static PraseodymiumConfig load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		Properties properties = new Properties();

		if (Files.exists(path)) {
			try (InputStream input = Files.newInputStream(path)) {
				properties.load(input);
			} catch (IOException exception) {
				PraseodymiumMod.LOGGER.warn("Failed to read {}, using defaults.", path, exception);
			}
		}

		PraseodymiumConfig config = new PraseodymiumConfig(
			readInt(properties, "target_min_fps", 50, 5, 240),
			readInt(properties, "recovery_fps", 60, 10, 360),
			readInt(properties, "minimum_render_distance", 4, 2, 32),
			readInt(properties, "maximum_render_distance", 16, 2, 64),
			readInt(properties, "minimum_simulation_distance", 4, 2, 32),
			readInt(properties, "maximum_simulation_distance", 12, 2, 64),
			readInt(properties, "adjustment_cooldown_seconds", 3, 1, 30),
			readInt(properties, "recovery_hold_seconds", 12, 2, 120),
			readDouble(properties, "minimum_entity_distance_scaling", 0.5D, 0.1D, 5.0D),
			readBoolean(properties, "occlusion_culling_enabled", true),
			readInt(properties, "occlusion_min_distance", 6, 1, 64),
			readInt(properties, "occlusion_max_distance", 96, 8, 512),
			readInt(properties, "visible_entity_recheck_ticks", 5, 1, 60),
			readInt(properties, "hidden_entity_recheck_ticks", 15, 1, 120)
		);

		writeDefaults(path, config);
		return config;
	}

	private static void writeDefaults(Path path, PraseodymiumConfig config) {
		Properties properties = new Properties();
		properties.setProperty("target_min_fps", Integer.toString(config.targetMinFps));
		properties.setProperty("recovery_fps", Integer.toString(config.recoveryFps));
		properties.setProperty("minimum_render_distance", Integer.toString(config.minimumRenderDistance));
		properties.setProperty("maximum_render_distance", Integer.toString(config.maximumRenderDistance));
		properties.setProperty("minimum_simulation_distance", Integer.toString(config.minimumSimulationDistance));
		properties.setProperty("maximum_simulation_distance", Integer.toString(config.maximumSimulationDistance));
		properties.setProperty("adjustment_cooldown_seconds", Integer.toString(config.adjustmentCooldownSeconds));
		properties.setProperty("recovery_hold_seconds", Integer.toString(config.recoveryHoldSeconds));
		properties.setProperty("minimum_entity_distance_scaling", Double.toString(config.minimumEntityDistanceScaling));
		properties.setProperty("occlusion_culling_enabled", Boolean.toString(config.occlusionCullingEnabled));
		properties.setProperty("occlusion_min_distance", Integer.toString(config.occlusionMinDistance));
		properties.setProperty("occlusion_max_distance", Integer.toString(config.occlusionMaxDistance));
		properties.setProperty("visible_entity_recheck_ticks", Integer.toString(config.visibleEntityRecheckTicks));
		properties.setProperty("hidden_entity_recheck_ticks", Integer.toString(config.hiddenEntityRecheckTicks));

		try {
			Files.createDirectories(path.getParent());
			try (OutputStream output = Files.newOutputStream(path)) {
				properties.store(output, "Praseodymium adaptive performance config");
			}
		} catch (IOException exception) {
			PraseodymiumMod.LOGGER.warn("Failed to write config defaults to {}", path, exception);
		}
	}

	private static int readInt(Properties properties, String key, int fallback, int min, int max) {
		String rawValue = properties.getProperty(key);
		if (rawValue == null) {
			return fallback;
		}

		try {
			int parsed = Integer.parseInt(rawValue.trim());
			return Math.max(min, Math.min(max, parsed));
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	private static double readDouble(Properties properties, String key, double fallback, double min, double max) {
		String rawValue = properties.getProperty(key);
		if (rawValue == null) {
			return fallback;
		}

		try {
			double parsed = Double.parseDouble(rawValue.trim());
			return Math.max(min, Math.min(max, parsed));
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	private static boolean readBoolean(Properties properties, String key, boolean fallback) {
		String rawValue = properties.getProperty(key);
		if (rawValue == null) {
			return fallback;
		}

		return Boolean.parseBoolean(rawValue.trim());
	}
}
