package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.entity.DomainVisualEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 领域视觉实体渲染器：绘制纯黑色球壳（带出现动画）
 * <p>
 * 出现动画：球壳从球顶一点开始向下蔓延，2 秒（40 tick）内完成。
 */
public class DomainVisualRenderer extends EntityRenderer<DomainVisualEntity> {

    /** 动画时长（tick）：2 秒 */
    private static final int ANIMATION_TICKS = 40;

    public DomainVisualRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(DomainVisualEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float radius = entity.getRadius();
        if (radius <= 0) return;

        // 出现动画进度：0~1（各客户端从收到实体起本地计时，进度一致）
        float progress = Math.min(1.0f, entity.tickCount / (float) ANIMATION_TICKS);

        poseStack.pushPose();
        // 实体位置即领域球心，按半径缩放单位球
        poseStack.scale(radius, radius, radius);
        DomainSphereRenderer.render(poseStack, progress);
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(DomainVisualEntity entity) {
        return null;
    }
}
