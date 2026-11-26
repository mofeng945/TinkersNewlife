package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import com.mofengbaizhi.tinkersnewlife.content.skill.MomoPose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * TNL1266 生息绝响的领域：**贴地转动的六芒星**。
 *
 * <h3>为什么单独一个类</h3>
 * 在 {@code KtuluMomoRenderer#render} 里画时，姿态矩阵还停在**相机空间**
 * （实体渲染的平移发生在 {@code super.render} 内部），而我用的是"相对实体"的偏移
 * ⇒ 画到了别的地方，看不到。这里改在 {@link RenderLevelStageEvent#AFTER_ENTITIES}
 * 里画：这个阶段 {@code PoseStack} 已经"相机在原点"，自己 translate 一次即可。
 *
 * <h3>⚠⚠ TNL1276 为什么之前**完全看不见**（真因两条，都是绘制路径的问题）</h3>
 * <ol>
 *   <li>⭐ 之前用的是 {@code RenderType.lightning()} ✗ —— 它是
 *       <b>{@code VertexFormat.Mode.QUADS}</b>（每 4 个顶点一个四边形），
 *       而我每次都只发 **2 个顶点**（一条线）⇒ 每个"四边形"都被补成**零面积的退化面**
 *       ⇒ 光栅化阶段**一个像素都不产生** ✗（这就解释了"条件全对、探针也在、就是看不见"）；</li>
 *   <li>{@code lightning()} 的 {@code OutputStateShard} 是 {@code WEATHER_TARGET} ✗
 *       —— 不是主渲染目标；天气那一趟早就已经合成过了 ⇒ 我们再往里面画，屏幕上永远出不来 ✗。</li>
 * </ol>
 * <p>现在改用 {@code RenderType.debugQuads()}：顶点格式 {@code POSITION_COLOR}、
 * 模式 {@code QUADS}、{@code TRANSLUCENT_TRANSPARENCY}、{@code NO_CULL}、
 * 输出到**主目标**（CompositeState 没有指定 outputState ⇒ 默认 MAIN_TARGET）。
 * 每条线**显式发 4 个顶点**（在水平面上按垂直方向撑开一个宽度），这才是能出像素的几何。
 *
 * <p>纯几何，不需要任何贴图素材（⇒ 也不碰用户的画）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RequiemDomainRenderer {

    /** ★六芒星外接半径（★与 RequiemGoal.RADIUS 一致 ✓ 用户口径 8 格领域 → 视觉略收 ✓） */
    private static final float RADIUS = 6.0F;

    /** ★线宽（★半宽 ✓ 单位格 ✓）—— TNL1277 用户："太细了" ⇒ 0.085 → 0.22 ✓ */
    private static final float LINE_W = 0.22F;

    /** ★离地高度（★太高会悬空、太低会 z-fighting ✓） */
    private static final float LIFT = 0.04F;

    /** TNL1271 探针限流 */
    private static long LAST_PROBE = 0L;

    private RequiemDomainRenderer() {
    }

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        try {
            Vec3 cam = event.getCamera().getPosition();
            PoseStack poseStack = event.getPoseStack();
            MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
            boolean drew = false;
            for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof KtuluMomo momo)) {
                    continue;
                }
                if (momo.tnl$animPose() != MomoPose.REQUIEM.ordinal()) {
                    continue;
                }
                float p = Math.max(0.0F, Math.min(1.0F, momo.tnl$animProgress() / 100.0F));
                // TNL1277 用户："透明度太高"（= 太淡）。原来最低能掉到 0.05 ⇒ 几乎看不见。
                //   现在只保留首尾很窄的淡入淡出，**中段不透明度 0.55~1.0**。
                float fade = p < 0.08F ? p / 0.08F : (p > 0.92F ? (1.0F - p) / 0.08F : 1.0F);
                float alpha = 0.55F + 0.45F * Math.max(0.0F, Math.min(1.0F, fade));
                draw(momo, poseStack, buffer, cam, alpha);
                drew = true;
                // TNL1271 探针：只在**真的要画**的时候每秒报一行（不画就不刷屏）
                long now = System.currentTimeMillis();
                if (now - LAST_PROBE > 1000L) {
                    LAST_PROBE = now;
                    TinkersNewlife.LOGGER.warn(
                            "TNL1271 domain drawing momo=({},{},{}) cam=({},{},{}) progress={} alpha={}",
                            (int) momo.getX(), (int) momo.getY(), (int) momo.getZ(),
                            (int) cam.x, (int) cam.y, (int) cam.z,
                            momo.tnl$animProgress(), alpha);
                }
            }
            if (drew) {
                buffer.endBatch(RenderType.debugQuads());   // 自己画的批次必须自己 flush
            }
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("TNL1271 domain render failed: {}", t.toString());
        }
    }

    private static void draw(KtuluMomo momo, PoseStack poseStack, MultiBufferSource.BufferSource buffer,
                             Vec3 cam, float alpha) {
        poseStack.pushPose();
        try {
            poseStack.translate(momo.getX() - cam.x, momo.getY() - cam.y, momo.getZ() - cam.z);
            VertexConsumer vc = buffer.getBuffer(RenderType.debugQuads());
            Matrix4f mat = poseStack.last().pose();
            float spin = (momo.tickCount % 1200) * 0.3F;
            // ① 两个等边三角形叠成六芒星（每个三角形 = 3 条边）
            // TNL1277 用户："透明度太高而且太细了" ⇒ 线更宽、颜色更亮更实（见 LINE_W / alpha）
            for (int tri = 0; tri < 2; tri++) {
                float off = spin + tri * 60.0F;
                for (int i = 0; i < 3; i++) {
                    float a1 = (float) Math.toRadians(off + i * 120.0F);
                    float a2 = (float) Math.toRadians(off + ((i + 1) % 3) * 120.0F);
                    line(vc, mat, a1, a2, RADIUS, LINE_W, 0.55F, 1.00F, 0.82F, alpha);
                }
            }
            // ② 外圈（同心圆，反向慢转）—— TNL1277 也加粗一档
            int seg = 48;
            float outer = RADIUS * 1.18F;
            for (int i = 0; i < seg; i++) {
                float a1 = (float) Math.toRadians(spin * 0.5F + i * (360.0F / seg));
                float a2 = (float) Math.toRadians(spin * 0.5F + (i + 1) * (360.0F / seg));
                line(vc, mat, a1, a2, outer, LINE_W * 0.75F, 0.38F, 0.92F, 0.72F, alpha * 0.9F);
            }
        } finally {
            poseStack.popPose();
        }
    }

    /**
     * 在水平面上画一条"有宽度"的线 ⇒ 一个四边形（4 个顶点）。
     * ⚠ 必须发满 4 个顶点：{@code QUADS} 模式下 2 个顶点是**退化面**，一个像素都出不来。
     */
    private static void line(VertexConsumer vc, Matrix4f mat, float a1, float a2, float r, float w,
                             float cr, float cg, float cb, float alpha) {
        float x1 = (float) Math.cos(a1) * r;
        float z1 = (float) Math.sin(a1) * r;
        float x2 = (float) Math.cos(a2) * r;
        float z2 = (float) Math.sin(a2) * r;
        float dx = x2 - x1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4F) {
            return;
        }
        // 水平面内垂直于这条线的方向
        float nx = -dz / len * w;
        float nz = dx / len * w;
        vc.vertex(mat, x1 + nx, LIFT, z1 + nz).color(cr, cg, cb, alpha).endVertex();
        vc.vertex(mat, x2 + nx, LIFT, z2 + nz).color(cr, cg, cb, alpha).endVertex();
        vc.vertex(mat, x2 - nx, LIFT, z2 - nz).color(cr, cg, cb, alpha).endVertex();
        vc.vertex(mat, x1 - nx, LIFT, z1 - nz).color(cr, cg, cb, alpha).endVertex();
    }
}
