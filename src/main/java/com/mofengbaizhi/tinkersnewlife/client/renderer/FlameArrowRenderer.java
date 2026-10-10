package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.entity.FlameArrowEntity;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 灶·开 火焰箭渲染：复用原版箭矢模型/纹理
 * （无着火贴图，火焰感由实体尾迹粒子表现）
 */
public class FlameArrowRenderer extends ArrowRenderer<FlameArrowEntity> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("textures/entity/projectile/arrow.png");

    public FlameArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(FlameArrowEntity entity) {
        return TEXTURE;
    }
}
