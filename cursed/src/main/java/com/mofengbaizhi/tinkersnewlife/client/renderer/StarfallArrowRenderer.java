package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.entity.StarfallArrow;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * TNL878 \u661f\u9668\u4e4b\u7bad\u6e32\u67d3\u5668 \u2713
 *
 * <p>\u2605\u7528\u6237\u53cd\u9988\u5ba2\u6237\u7aef\u5d29\u6e83\uff1a{@code EntityRenderDispatcher}
 * \u629b \u2605{@code NullPointerException: Cannot invoke "EntityRenderer.shouldRender"}\uff0c
 * \u2605\u5373"\u67d0\u4e2a\u5b9e\u4f53**\u6ca1\u6709\u6e32\u67d3\u5668**" \u2713 \u3002
 *
 * <p>\u2605\u65e7\u5199\u6cd5\u76f4\u63a5\u7528 {@code ArrowRenderer::new} \u5f53\u65b9\u6cd5\u5f15\u7528 \u2717\uff0c
 * \u2605\u53c2\u8003\u5b9e\u73b0\uff08\u7528\u6237\u63d0\u4f9b\u7684\u300c\u661f\u843d\u7ec7\u5f71\u300d\uff09\u5219\u662f
 * **\u81ea\u5df1\u7ee7\u627f\u4e00\u4e2a\u5177\u4f53\u5b50\u7c7b** \u2713\uff0c\u2605\u73b0\u5728\u7167\u540c\u2713\u3002
 */
public class StarfallArrowRenderer extends ArrowRenderer<StarfallArrow> {

    public static final ResourceLocation TEXTURE =
            new ResourceLocation("tinkersnewlife", "textures/entity/starfall_arrow.png");

    public StarfallArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(StarfallArrow entity) {
        return TEXTURE;
    }
}
