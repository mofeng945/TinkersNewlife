package com.mofengbaizhi.tinkersnewlife.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/** TNL879 星陨尾迹的 RenderType（★照搬参考实现 ✓） */
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
