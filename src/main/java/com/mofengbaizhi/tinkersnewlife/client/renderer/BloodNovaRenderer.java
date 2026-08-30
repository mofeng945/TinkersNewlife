package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.entity.BloodNovaEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 赤血操术·超新星 血球渲染器：绘制微小血红色实心圆球（直径约 0.2 格），
 * 带轻微呼吸缩放与外层淡红光晕。
 */
public class BloodNovaRenderer extends EntityRenderer<BloodNovaEntity> {

    /** 球壳细分 */
    private static final int RINGS = 8;
    private static final int SEGMENTS = 12;

    public BloodNovaRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(BloodNovaEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight) {
        float pulse = 1.0F + 0.12F * (float) Math.sin(entity.tickCount * 0.35F);
        float scale = BloodNovaEntity.BALL_RADIUS * pulse;

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        poseStack.pushPose();
        // 外层淡红光晕（半透明，略大）
        drawSphere(poseStack, scale * 1.7F, 0.9F, 0.15F, 0.15F, 55);
        // 血球本体（不透明深红）
        drawSphere(poseStack, scale, 0.78F, 0.04F, 0.04F, 255);
        poseStack.popPose();

        RenderSystem.enableCull();
    }

    /** 立即模式绘制单位球壳（含呼吸缩放已由 scale 处理） */
    private static void drawSphere(PoseStack poseStack, float radius, float r, float g, float b, int alpha) {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < RINGS; i++) {
            double phi1 = Math.PI * i / RINGS;
            double phi2 = Math.PI * (i + 1) / RINGS;
            for (int j = 0; j < SEGMENTS; j++) {
                double a1 = 2 * Math.PI * j / SEGMENTS;
                double a2 = 2 * Math.PI * (j + 1) / SEGMENTS;
                addTri(builder, phi1, a1, phi2, a1, phi1, a2, radius, r, g, b, alpha);
                addTri(builder, phi1, a2, phi2, a1, phi2, a2, radius, r, g, b, alpha);
            }
        }
        // 立即绘制：模型矩阵压入 RenderSystem 模型视图栈
        RenderSystem.getModelViewStack().pushPose();
        RenderSystem.getModelViewStack().mulPoseMatrix(poseStack.last().pose());
        RenderSystem.applyModelViewMatrix();
        BufferUploader.drawWithShader(builder.end());
        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
    }

    private static void addTri(BufferBuilder builder, double p1, double a1, double p2, double a2,
                               double p3, double a3, float radius, float r, float g, float b, int alpha) {
        addVertex(builder, p1, a1, radius, r, g, b, alpha);
        addVertex(builder, p2, a2, radius, r, g, b, alpha);
        addVertex(builder, p3, a3, radius, r, g, b, alpha);
    }

    private static void addVertex(BufferBuilder builder, double phi, double a, float radius,
                                  float r, float g, float b, int alpha) {
        float x = (float) (Math.sin(phi) * Math.cos(a) * radius);
        float y = (float) (Math.cos(phi) * radius);
        float z = (float) (Math.sin(phi) * Math.sin(a) * radius);
        builder.vertex(x, y, z).color(r, g, b, alpha).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(BloodNovaEntity entity) {
        return null;
    }
}
