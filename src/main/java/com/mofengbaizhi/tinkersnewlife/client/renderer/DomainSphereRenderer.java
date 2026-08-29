package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;

/**
 * 纯黑色空心球壳（半透明黑面片）绘制
 * <p>
 * - 三角面片组成球壳（非线框、非方块），半透明黑 → 球壳可见且内部可透视（空心）
 * - 深度测试开启：被方块遮挡的部分不显示
 * - 球壳不写深度（depthMask off）：近/远两面都可见，保持"空心"观感
 * 单位球静态 VertexBuffer 只构建一次，渲染时按领域半径缩放。
 */
public final class DomainSphereRenderer {

    private static VertexBuffer shellBuffer;

    private DomainSphereRenderer() {}

    /** 绘制纯黑色球壳（当前 PoseStack 原点为球心，单位半径由外部缩放） */
    public static void render(PoseStack poseStack) {
        ensureBuffer();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);   // 纯黑不透明，正常写深度（被方块遮挡的部分不显示）
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        shellBuffer.bind();
        shellBuffer.drawWithShader(poseStack.last().pose(), RenderSystem.getProjectionMatrix(),
                GameRenderer.getPositionColorShader());
        VertexBuffer.unbind();
        RenderSystem.enableCull();
    }

    private static void ensureBuffer() {
        if (shellBuffer != null) return;
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int rings = 16;      // 纬度分段
        int segments = 24;   // 经度分段
        int alpha = 255;     // 纯黑不透明
        for (int i = 0; i < rings; i++) {
            double phi1 = Math.PI * i / rings;
            double phi2 = Math.PI * (i + 1) / rings;
            for (int j = 0; j < segments; j++) {
                double a1 = 2 * Math.PI * j / segments;
                double a2 = 2 * Math.PI * (j + 1) / segments;
                // 两个三角形组成一个四边形面片
                addVertex(builder, phi1, a1, alpha);
                addVertex(builder, phi2, a1, alpha);
                addVertex(builder, phi1, a2, alpha);
                addVertex(builder, phi1, a2, alpha);
                addVertex(builder, phi2, a1, alpha);
                addVertex(builder, phi2, a2, alpha);
            }
        }
        var rendered = builder.end();
        shellBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        shellBuffer.bind();
        shellBuffer.upload(rendered);
        VertexBuffer.unbind();
    }

    private static void addVertex(BufferBuilder builder, double phi, double a, int alpha) {
        float x = (float) (Math.sin(phi) * Math.cos(a));
        float y = (float) Math.cos(phi);
        float z = (float) (Math.sin(phi) * Math.sin(a));
        builder.vertex(x, y, z).color(0, 0, 0, alpha).endVertex();
    }
}
