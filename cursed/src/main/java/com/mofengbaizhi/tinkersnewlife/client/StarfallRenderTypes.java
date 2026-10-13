package com.mofengbaizhi.tinkersnewlife.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/** TNL879 \u661f\u9668\u5c3e\u8ff9\u7684 RenderType\uff08\u2605\u7167\u642c\u53c2\u8003\u5b9e\u73b0 \u2713\uff09 */
public final class StarfallRenderTypes {

    private StarfallRenderTypes() {
    }

    public static final RenderType ARROW_TRAIL = RenderType.create(
            "tinkersnewlife:starfall_trail",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            1024,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateAccess.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateAccess.LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(RenderStateAccess.LEQUAL_DEPTH_TEST)
                    .setCullState(RenderStateAccess.NO_CULL)
                    .setWriteMaskState(RenderStateAccess.COLOR_WRITE)
                    .createCompositeState(false)
    );
}
