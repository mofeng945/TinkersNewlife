package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.entity.SkyUsoraBoltEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 宇守罗弹渲染器：不绘制模型（视觉由飞行雪晶尾迹 + 命中碎冰粒子构成）。
 */
public class SkyUsoraBoltRenderer extends EntityRenderer<SkyUsoraBoltEntity> {

    public SkyUsoraBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SkyUsoraBoltEntity entity) {
        return new ResourceLocation("minecraft:textures/particle/snowflake.png");
    }
}
