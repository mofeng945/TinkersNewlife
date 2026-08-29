package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.entity.DomainVisualEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;

/**
 * 纯黑色球壳绘制（含出现动画与领域对抗挖洞）
 * <p>
 * - 动画：球壳从球顶一点开始，随 {@code progress}（0~1）向下蔓延绘制球冠，
 *   2 秒（40 tick）内完成整球；动画期间逐帧立即绘制，完成后用静态 VertexBuffer
 * - 领域对抗：落入对方领域球体内的壳面三角形被剔除（黑色边缘"删去"，两球空间打通）
 * - 纯黑不透明（alpha 255），深度测试开启（被方块遮挡的部分不显示）
 */
public final class DomainSphereRenderer {

    private static final int RINGS = 16;
    private static final int SEGMENTS = 24;

    private static VertexBuffer fullShellBuffer;

    private DomainSphereRenderer() {}

    /** 绘制纯黑色球壳；progress 为出现动画进度（0~1，1 表示完整球壳）；
     *  entity 携带对抗信息（为 null 或未对抗时绘制完整球壳） */
    public static void render(PoseStack poseStack, float progress, DomainVisualEntity entity) {
        setupState();
        boolean clash = entity != null && entity.isClashActive();
        if (progress >= 1.0f && !clash) {
            ensureFullBuffer();
            fullShellBuffer.bind();
            fullShellBuffer.drawWithShader(poseStack.last().pose(), RenderSystem.getProjectionMatrix(),
                    GameRenderer.getPositionColorShader());
            VertexBuffer.unbind();
        } else {
            // 动画期间 或 对抗挖洞期间：逐帧构建网格立即绘制
            double phiMax = Math.max(0.0001, progress * Math.PI);
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            buildShell(builder, phiMax, entity);
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

    /** 球冠网格：从球顶（phi=0）到 phiMax，含边界部分环；对抗时剔除落入对方球体的三角形 */
    private static void buildShell(BufferBuilder builder, double phiMax, DomainVisualEntity entity) {
        Vec3 clashCenter = entity != null && entity.isClashActive() ? entity.getClashCenter() : null;
        double clashR = clashCenter != null ? entity.getClashRadius() : 0;
        // 单位球顶点 → 世界坐标：实体位置(领域球心) + 单位向量×半径
        Vec3 origin = entity != null ? entity.position() : Vec3.ZERO;
        float radius = entity != null ? entity.getRadius() : 1.0f;

        int maxRing = (int) Math.floor(phiMax / Math.PI * RINGS);
        for (int i = 0; i < maxRing; i++) {
            double phi1 = Math.PI * i / RINGS;
            double phi2 = Math.PI * (i + 1) / RINGS;
            addRing(builder, phi1, phi2, origin, radius, clashCenter, clashR);
        }
        if (maxRing < RINGS) {
            double phi1 = Math.PI * maxRing / RINGS;
            addRing(builder, phi1, phiMax, origin, radius, clashCenter, clashR);
        }
    }

    /** 一个纬度带：SEGMENTS 个四边形面片（各两个三角形）；对抗时按三角形重心剔除 */
    private static void addRing(BufferBuilder builder, double phi1, double phi2,
                                Vec3 origin, float radius, Vec3 clashCenter, double clashR) {
        for (int j = 0; j < SEGMENTS; j++) {
            double a1 = 2 * Math.PI * j / SEGMENTS;
            double a2 = 2 * Math.PI * (j + 1) / SEGMENTS;
            // 三角形 1：(phi1,a1) (phi2,a1) (phi1,a2)
            addTri(builder, phi1, a1, phi2, a1, phi1, a2, origin, radius, clashCenter, clashR);
            // 三角形 2：(phi1,a2) (phi2,a1) (phi2,a2)
            addTri(builder, phi1, a2, phi2, a1, phi2, a2, origin, radius, clashCenter, clashR);
        }
    }

    private static void addTri(BufferBuilder builder,
                               double p1, double a1, double p2, double a2, double p3, double a3,
                               Vec3 origin, float radius, Vec3 clashCenter, double clashR) {
        if (clashCenter != null) {
            // 重心（世界坐标）落入对方球体 → 整个三角形剔除（黑色边缘删去）
            Vec3 c = worldPos(origin, radius, (float) ((p1 + p2 + p3) / 3), (float) ((a1 + a2 + a3) / 3));
            if (c.distanceToSqr(clashCenter) <= clashR * clashR) return;
        }
        addVertex(builder, p1, a1);
        addVertex(builder, p2, a2);
        addVertex(builder, p3, a3);
    }

    /** 单位球参数坐标 → 世界坐标（领域球心 + 单位向量×半径） */
    private static Vec3 worldPos(Vec3 origin, float radius, float phi, float a) {
        double x = Math.sin(phi) * Math.cos(a) * radius;
        double y = Math.cos(phi) * radius;
        double z = Math.sin(phi) * Math.sin(a) * radius;
        return new Vec3(origin.x + x, origin.y + y, origin.z + z);
    }

    private static void addVertex(BufferBuilder builder, double phi, double a) {
        float x = (float) (Math.sin(phi) * Math.cos(a));
        float y = (float) Math.cos(phi);
        float z = (float) (Math.sin(phi) * Math.sin(a));
        builder.vertex(x, y, z).color(0, 0, 0, 255).endVertex();
    }

    /** 完整球壳静态缓冲（动画完成后、未对抗时复用） */
    private static void ensureFullBuffer() {
        if (fullShellBuffer != null) return;
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        buildShell(builder, Math.PI, null);
        var rendered = builder.end();
        fullShellBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        fullShellBuffer.bind();
        fullShellBuffer.upload(rendered);
        VertexBuffer.unbind();
    }
}
