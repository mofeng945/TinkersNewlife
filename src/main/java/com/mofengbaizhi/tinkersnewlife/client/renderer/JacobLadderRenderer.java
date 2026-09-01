package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.entity.JacobLadderEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 雅各布天梯 法阵渲染器：不绘制几何体（法阵/光柱视觉由服务端粒子承担，所有客户端可见），
 * 仅作为实体渲染器占位（缺失会导致渲染阶段 NPE）。
 */
public class JacobLadderRenderer extends EntityRenderer<JacobLadderEntity> {

    public JacobLadderRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(JacobLadderEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // 纯粒子视觉
    }

    @Override
    public ResourceLocation getTextureLocation(JacobLadderEntity entity) {
        return null;
    }
}
