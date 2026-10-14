package com.mofengbaizhi.tinkersnewlife.client.portal;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/** TNL999 \u2605\u2605 \u95e8\u9762\u7528\u7684\u81ea\u5b9a\u4e49 RenderType\uff08\u2605\u4e0d\u7ed1\u8d34\u56fe \u2713\uff0c\u2605Sampler0 \u7531\u6211\u4eec\u624b\u52a8\u7ed1 FBO \u2713\uff09 */
public final class PortalViewRenderType {

    private static RenderType type;

    private PortalViewRenderType() {
    }

    public static RenderType get() {
        if (type == null) {
            type = RenderType.create("tnl_white_space_portal",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(new RenderStateShard.ShaderStateShard(
                                    () -> net.minecraft.client.renderer.ShaderInstance.class.cast(
                                            com.mofengbaizhi.tinkersnewlife.client.portal.PortalViewShaders.portalView())))
                            .createCompositeState(true));
        }
        return type;
    }
}
