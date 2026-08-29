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
 * 纯黑色空心圆球（线框）绘制
 * <p>
 * 单位球线框网格（经纬线），静态 VertexBuffer 只构建一次，
 * 渲染时按领域半径缩放即可。纯绘制形状，不使用方块。
 */
public final class DomainSphereRenderer {

    private static VertexBuffer sphereLines;

    private DomainSphereRenderer() {}

    /** 绘制黑色空心圆球（当前 PoseStack 原点为球心，单位半径由外部缩放） */
    public static void render(PoseStack poseStack) {
        ensureBuffer();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        RenderSystem.lineWidth(1.5f);
        sphereLines.bind();
        sphereLines.drawWithShader(poseStack.last().pose(), RenderSystem.getProjectionMatrix(),
                GameRenderer.getRendertypeLinesShader());
        VertexBuffer.unbind();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void ensureBuffer() {
        if (sphereLines != null) return;
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        int rings = 14;     // 纬度圈数
        int segments = 28;  // 每圈分段
        // 纬度圈（跳过两极）
        for (int i = 1; i < rings; i++) {
            double phi = Math.PI * i / rings;
            double y = Math.cos(phi);
            double r = Math.sin(phi);
            for (int j = 0; j < segments; j++) {
                double a1 = 2 * Math.PI * j / segments;
                double a2 = 2 * Math.PI * (j + 1) / segments;
                addLine(builder,
                        r * Math.cos(a1), y, r * Math.sin(a1),
                        r * Math.cos(a2), y, r * Math.sin(a2));
            }
        }
        // 经线圈
        for (int j = 0; j < segments; j++) {
            double a = 2 * Math.PI * j / segments;
            double ca = Math.cos(a);
            double sa = Math.sin(a);
            for (int i = 0; i < rings; i++) {
                double p1 = Math.PI * i / rings;
                double p2 = Math.PI * (i + 1) / rings;
                addLine(builder,
                        Math.sin(p1) * ca, Math.cos(p1), Math.sin(p1) * sa,
                        Math.sin(p2) * ca, Math.cos(p2), Math.sin(p2) * sa);
            }
        }
        var rendered = builder.end();
        sphereLines = new VertexBuffer(VertexBuffer.Usage.STATIC);
        sphereLines.bind();
        sphereLines.upload(rendered);
        VertexBuffer.unbind();
    }

    /** 纯黑色线段，法线取球面径向 */
    private static void addLine(BufferBuilder builder,
                                double x1, double y1, double z1,
                                double x2, double y2, double z2) {
        builder.vertex(x1, y1, z1).color(0, 0, 0, 255)
                .normal((float) x1, (float) y1, (float) z1).endVertex();
        builder.vertex(x2, y2, z2).color(0, 0, 0, 255)
                .normal((float) x2, (float) y2, (float) z2).endVertex();
    }
}
