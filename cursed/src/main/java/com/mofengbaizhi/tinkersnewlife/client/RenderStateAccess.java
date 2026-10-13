package com.mofengbaizhi.tinkersnewlife.client;

import net.minecraft.client.renderer.RenderStateShard;

/** TNL879 \u2605\u53d6\u51fa RenderStateShard \u7684 protected \u5e38\u91cf\uff08\u2605\u7167\u642c\u53c2\u8003\u5b9e\u73b0 AccessUtils \u2713\uff09 */
public final class RenderStateAccess {

    public static final RenderStateShard.ShaderStateShard POSITION_COLOR_SHADER = Shard.POSITION_COLOR_SHADER;
    public static final RenderStateShard.TransparencyStateShard LIGHTNING_TRANSPARENCY = Shard.LIGHTNING_TRANSPARENCY;
    public static final RenderStateShard.DepthTestStateShard LEQUAL_DEPTH_TEST = Shard.LEQUAL_DEPTH_TEST;
    public static final RenderStateShard.CullStateShard NO_CULL = Shard.NO_CULL;
    public static final RenderStateShard.WriteMaskStateShard COLOR_WRITE = Shard.COLOR_WRITE;

    private RenderStateAccess() {
    }

    private static final class Shard extends RenderStateShard {
        private static final ShaderStateShard POSITION_COLOR_SHADER = RenderStateShard.POSITION_COLOR_SHADER;
        private static final TransparencyStateShard LIGHTNING_TRANSPARENCY = RenderStateShard.LIGHTNING_TRANSPARENCY;
        private static final DepthTestStateShard LEQUAL_DEPTH_TEST = RenderStateShard.LEQUAL_DEPTH_TEST;
        private static final CullStateShard NO_CULL = RenderStateShard.NO_CULL;
        private static final WriteMaskStateShard COLOR_WRITE = RenderStateShard.COLOR_WRITE;

        private Shard(String name, Runnable setup, Runnable clear) {
            super(name, setup, clear);
            throw new AssertionError();
        }
    }
}
