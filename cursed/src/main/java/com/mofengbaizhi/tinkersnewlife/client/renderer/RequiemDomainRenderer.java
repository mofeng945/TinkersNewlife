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

/**
 * TNL1266 生息绝响的领域：**贴地转动的六芒星**。
 *
 * <p>为什么单独一个类：在 `KtuluMomoRenderer#render` 里画时，姿态矩阵还停在
 * **相机空间**（实体渲染的平移发生在 `super.render` 内部），而我用的是"相对实体"的偏移
 * ⇒ 结果画到了别的地方，看不到（用户："法阵没绘制出来"）。
 * 这里改成仓库里已验证的做法：在 {@link RenderLevelStageEvent} 里画，
 * 自己把姿态推到"实体脚下"（世界坐标 − 相机坐标），再用局部偏移发线。
 *
 * <p>纯几何线（{@code RenderType.lightning()}，顶点格式只有 POSITION_COLOR），
 * 不需要任何贴图素材。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RequiemDomainRenderer {

    private static final float RADIUS = 6.0F;

    /** TNL1271 探针限流 */
    private static long LAST_PROBE = 0L;

    private RequiemDomainRenderer() {
    }

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        // TNL1271 探针：用户要求"放心加"。每秒打一行，定位法阵为何不可见。
        // 看什么：stage 是否 AFTER_ENTITIES / 找到几只墨默 / 其中几只在 REQUIEM / 第一只的 animPose。
        try {
            Minecraft mcP = Minecraft.getInstance();
            long nowP = System.currentTimeMillis();
            if (mcP.level != null && nowP - LAST_PROBE > 1000L) {
                LAST_PROBE = nowP;
                int tnl$momo = 0;
                int tnl$req = 0;
                int tnl$sample = -1;
                for (net.minecraft.world.entity.Entity e : mcP.level.entitiesForRendering()) {
                    if (e instanceof KtuluMomo m) {
                        if (tnl$momo == 0) {
                            tnl$sample = m.tnl$animPose();
                        }
                        tnl$momo++;
                        if (m.tnl$animPose() == MomoPose.REQUIEM.ordinal()) {
                            tnl$req++;
                        }
                    }
                }
                TinkersNewlife.LOGGER.warn(
                        "TNL1271 domain stage={} momo={} requiem={} animPoseSample={} cam=({},{},{})",
                        event.getStage(), tnl$momo, tnl$req, tnl$sample,
                        (int) event.getCamera().getPosition().x,
                        (int) event.getCamera().getPosition().y,
                        (int) event.getCamera().getPosition().z);
            }
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("TNL1271 probe failed: {}", t.toString());
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
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
            float p = Math.max(0.0F, Math.min(1.0F, momo.tnl$animProgress()));
            float alpha = p < 0.1F ? p / 0.1F : (p > 0.85F ? (1.0F - p) / 0.15F : 1.0F);
            alpha = Math.max(0.05F, Math.min(1.0F, alpha));
            draw(momo, poseStack, buffer, cam, alpha);
            drew = true;
        }
        if (drew) {
            buffer.endBatch(RenderType.lightning());   // 画完必须自己 flush
        }
    }

    private static void draw(KtuluMomo momo, PoseStack poseStack, MultiBufferSource.BufferSource buffer,
                             Vec3 cam, float alpha) {
        poseStack.pushPose();
        poseStack.translate(momo.getX() - cam.x, momo.getY() - cam.y, momo.getZ() - cam.z);
        VertexConsumer vc = buffer.getBuffer(RenderType.lightning());
        org.joml.Matrix4f mat = poseStack.last().pose();
        float y = 0.03F;
        float spin = (momo.tickCount % 1200) * 0.3F;
        for (int tri = 0; tri < 2; tri++) {
            float off = spin + tri * 60.0F;
            for (int i = 0; i < 3; i++) {
                double a1 = Math.toRadians(off + i * 120.0F);
                double a2 = Math.toRadians(off + ((i + 1) % 3) * 120.0F);
                vc.vertex(mat, (float) Math.cos(a1) * RADIUS, y, (float) Math.sin(a1) * RADIUS)
                        .color(0.35F, 0.95F, 0.75F, alpha).endVertex();
                vc.vertex(mat, (float) Math.cos(a2) * RADIUS, y, (float) Math.sin(a2) * RADIUS)
                        .color(0.35F, 0.95F, 0.75F, alpha).endVertex();
            }
        }
        int seg = 48;
        float outer = RADIUS * 1.18F;
        for (int i = 0; i < seg; i++) {
            double a1 = Math.toRadians(spin * 0.5D + i * (360.0D / seg));
            double a2 = Math.toRadians(spin * 0.5D + (i + 1) * (360.0D / seg));
            vc.vertex(mat, (float) Math.cos(a1) * outer, y, (float) Math.sin(a1) * outer)
                    .color(0.25F, 0.75F, 0.60F, alpha * 0.7F).endVertex();
            vc.vertex(mat, (float) Math.cos(a2) * outer, y, (float) Math.sin(a2) * outer)
                    .color(0.25F, 0.75F, 0.60F, alpha * 0.7F).endVertex();
        }
        poseStack.popPose();
    }
}
