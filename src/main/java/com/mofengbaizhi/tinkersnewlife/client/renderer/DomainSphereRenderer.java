package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;

/**
 * 纯黑色球壳绘制（含出现动画）
 * <p>
 * - 动画：球壳从球顶一点开始，随 {@code progress}（0~1）向下蔓延绘制球冠，
 *   2 秒（40 tick）内完成整球；动画期间逐帧立即绘制，完成后用静态 VertexBuffer
 * - 纯黑不透明（alpha 255），深度测试开启（被方块遮挡的部分不显示）
 */
public final class DomainSphereRenderer {

    private static final int RINGS = 16;
    private static final int SEGMENTS = 24;

    private static VertexBuffer fullShellBuffer;

    private DomainSphereRenderer() {}

    /** 绘制纯黑色球壳；progress 为出现动画进度（0~1，1 表示完整球壳） */
    public static void render(PoseStack poseStack, float progress) {
        setupState();
        if (progress >= 1.0f) {
            ensureFullBuffer();
            fullShellBuffer.bind();
            fullShellBuffer.drawWithShader(poseStack.last().pose(), RenderSystem.getProjectionMatrix(),
                    GameRenderer.getPositionColorShader());
            VertexBuffer.unbind();
        } else {
            // 动画：逐帧绘制从球顶蔓延到 phiMax 的球冠
            double phiMax = Math.max(0.0001, progress * Math.PI);
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            buildCap(builder, phiMax);
            // 立即绘制：把 PoseStack 模型矩阵压入 RenderSystem 模型视图栈
            RenderSystem.getModelViewStack().pushPose();
            RenderSystem.getModelViewStack().mulPoseMatrix(poseStack.last().pose());
            RenderSystem.applyModelViewMatrix();
            BufferUploader.drawWithShader(builder.end());
            RenderSystem.getModelViewStack().popPose();
            RenderSystem.applyModelViewMatrix();
        }
        restoreState();
    }

    private static void setupState() {
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    private static void restoreState() {
        RenderSystem.enableCull();
    }

    /** 球冠网格：从球顶（phi=0）到 phiMax，含边界部分环 */
    private static void buildCap(BufferBuilder builder, double phiMax) {
        int maxRing = (int) Math.floor(phiMax / Math.PI * RINGS);
        for (int i = 0; i < maxRing; i++) {
            double phi1 = Math.PI * i / RINGS;
            double phi2 = Math.PI * (i + 1) / RINGS;
            addQuad(builder, phi1, phi2);
        }
        if (maxRing < RINGS) {
            double phi1 = Math.PI * maxRing / RINGS;
            addQuad(builder, phi1, phiMax);
        }
    }

    /** 两个三角形组成一个纬度带四边形面片 */
    private static void addQuad(BufferBuilder builder, double phi1, double phi2) {
        for (int j = 0; j < SEGMENTS; j++) {
            double a1 = 2 * Math.PI * j / SEGMENTS;
            double a2 = 2 * Math.PI * (j + 1) / SEGMENTS;
            addVertex(builder, phi1, a1);
            addVertex(builder, phi2, a1);
            addVertex(builder, phi1, a2);
            addVertex(builder, phi1, a2);
            addVertex(builder, phi2, a1);
            addVertex(builder, phi2, a2);
        }
    }

    private static void addVertex(BufferBuilder builder, double phi, double a) {
        float x = (float) (Math.sin(phi) * Math.cos(a));
        float y = (float) Math.cos(phi);
        float z = (float) (Math.sin(phi) * Math.sin(a));
        builder.vertex(x, y, z).color(0, 0, 0, 255).endVertex();
    }

    /** 完整球壳静态缓冲（动画完成后复用） */
    private static void ensureFullBuffer() {
        if (fullShellBuffer != null) return;
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        buildCap(builder, Math.PI);
        var rendered = builder.end();
        fullShellBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        fullShellBuffer.bind();
        fullShellBuffer.upload(rendered);
        VertexBuffer.unbind();
    }
}
