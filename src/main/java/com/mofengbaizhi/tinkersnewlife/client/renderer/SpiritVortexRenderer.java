package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.entity.SpiritVortexEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 黑色漩涡弹渲染器：不绘制模型（视觉由旋转黑烟粒子构成）。
 */
public class SpiritVortexRenderer extends EntityRenderer<SpiritVortexEntity> {

    public SpiritVortexRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SpiritVortexEntity entity) {
        return new ResourceLocation("minecraft:textures/particle/smoke.png");
    }
}
