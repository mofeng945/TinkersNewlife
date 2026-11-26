package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.entity.CursedOrbEntity;
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
 * 无下限 苍/赫/茈 球体渲染器：按类型绘制发光圆球——
 * 苍 = 天蓝、赫 = 亮红、茈 = 紫（带呼吸光晕）。
 */
public class CursedOrbRenderer extends EntityRenderer<CursedOrbEntity> {

    private static final int RINGS = 8;
    private static final int SEGMENTS = 12;
    /** 球半径（格） */
    private static final float RADIUS = 0.18F;

    public CursedOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(CursedOrbEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight) {
        float pulse = 1.0F + 0.1F * (float) Math.sin(entity.tickCount * 0.4F);
        float[] col = switch (entity.getOrbType()) {
            case CursedOrbEntity.TYPE_CANG -> new float[]{0.35F, 0.65F, 1.0F};
            case CursedOrbEntity.TYPE_HE -> new float[]{1.0F, 0.18F, 0.12F};
            default -> new float[]{0.65F, 0.28F, 1.0F};
        };

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        poseStack.pushPose();
        // 外层光晕（半透明，略大）
        drawSphere(poseStack, RADIUS * 1.8F * pulse, col[0], col[1], col[2], 50);
        // 球体本体（不透明）
        drawSphere(poseStack, RADIUS * pulse, col[0], col[1], col[2], 255);
        poseStack.popPose();

        RenderSystem.enableCull();
    }

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
    public ResourceLocation getTextureLocation(CursedOrbEntity entity) {
        return null;
    }
}
