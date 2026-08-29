package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.entity.DomainVisualEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 领域视觉实体渲染器：绘制纯黑色空心圆球（线框形状）
 */
public class DomainVisualRenderer extends EntityRenderer<DomainVisualEntity> {

    public DomainVisualRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(DomainVisualEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float radius = entity.getRadius();
        if (radius <= 0) return;
        poseStack.pushPose();
        // 实体位置即领域球心，按半径缩放单位球
        poseStack.scale(radius, radius, radius);
        DomainSphereRenderer.render(poseStack);
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(DomainVisualEntity entity) {
        return null;
    }
}
