package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.entity.WeakPointEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * 十划咒法·弱点实体渲染：手绘一颗脉冲金色光点（billboard，无模型/无纹理）。
 * 实体中心锚定在目标弱点位置（70% 身高、身前 0.3 格）。
 */
public class WeakPointRenderer extends EntityRenderer<WeakPointEntity> {

    public WeakPointRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(WeakPointEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float t = entity.tickCount + partialTick;
        float pulse = 1.0F + 0.18F * (float) Math.sin(t * 0.22D);
        float half = 0.16F * pulse; // 略大于碰撞箱的视觉尺寸，便于看清

        poseStack.pushPose();
        poseStack.translate(0.0D, 0.02D, 0.0D);
        Quaternionf cam = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        poseStack.mulPose(cam);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f mat = poseStack.last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        // 金色光点（外亮内亮两层叠加，近似发光）
        quad(bb, mat, half, 1.0F, 0.86F, 0.32F, 0.85F);
        quad(bb, mat, half * 0.5F, 1.0F, 0.97F, 0.72F, 1.0F);
        BufferUploader.drawWithShader(bb.end());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private static void quad(BufferBuilder bb, Matrix4f mat, float s,
                             float r, float g, float b, float a) {
        bb.vertex(mat, -s, -s, 0.0F).color(r, g, b, a).endVertex();
        bb.vertex(mat, -s, s, 0.0F).color(r, g, b, a).endVertex();
        bb.vertex(mat, s, s, 0.0F).color(r, g, b, a).endVertex();
        bb.vertex(mat, s, -s, 0.0F).color(r, g, b, a).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(WeakPointEntity entity) {
        return null;
    }
}
