package dev.praseodymium.client;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.world.level.block.Blocks;

public final class ConnectedGlassModelPlugin implements ModelLoadingPlugin {
	@Override
	public void initialize(Context context) {
		context.modifyBlockModelAfterBake().register((model, ctx) -> {
			if (ConnectedGlassBlockModel.isConnectedGlass(ctx.state())) {
				return new ConnectedGlassBlockModel(model);
			}

			return model;
		});
	}
}
