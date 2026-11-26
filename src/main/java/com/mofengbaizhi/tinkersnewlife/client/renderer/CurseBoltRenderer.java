package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.entity.CurseBoltEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 咒力外放 顺转能量弹渲染器：不绘制模型（视觉由白色光尘粒子构成）。
 */
public class CurseBoltRenderer extends EntityRenderer<CurseBoltEntity> {

    public CurseBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(CurseBoltEntity entity) {
        return new ResourceLocation("minecraft:textures/particle/glitter.png");
    }
}
