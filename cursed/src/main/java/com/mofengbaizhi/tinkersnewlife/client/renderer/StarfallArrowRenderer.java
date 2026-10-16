package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.entity.StarfallArrow;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * TNL878 星陨之箭渲染器 ✓
 *
 * <p>★用户反馈客户端崩溃：{@code EntityRenderDispatcher}
 * 抛 ★{@code NullPointerException: Cannot invoke "EntityRenderer.shouldRender"}，
 * ★即"某个实体**没有渲染器**" ✓ 。
 *
 * <p>★旧写法直接用 {@code ArrowRenderer::new} 当方法引用 ✗，
 * ★参考实现（用户提供的「星落织影」）则是
 * **自己继承一个具体子类** ✓，★现在照同✓。
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
