package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * 克图露·墨默渲染器 —— ★复用商人墨默的模型（玩家模型 ✓）
 * 只把贴图换成**黑白灰度版** ✓（★用户口径：“染色为黑白” ✓）。
 */
public class KtuluMomoRenderer extends HumanoidMobRenderer<KtuluMomo, HumanoidModel<KtuluMomo>> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("tinkersnewlife", "textures/entity/ktulu_momo.png");
    private static final float MODEL_SCALE = 0.85F;

    public KtuluMomoRenderer(EntityRendererProvider.Context context) {
        super(context, new com.mofengbaizhi.tinkersnewlife.client.model.KtuluMomoModel(
                context.bakeLayer(ModelLayers.PLAYER)), 0.43F);   // TNL1089
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    /**
     * TNL1091 在 super.render 之前把蓄力进度喂给模型（setupAnim 就在它里面跑）。
     * 进度来自隐藏标记的冷却 —— 那是我们验证过能到客户端的通道。
     */
    /** TNL1158 姿势探针的限流 */
    private static long tnl$poseLog = 0L;

    /** TNL1093 由冷却算出的进度永远是 0（19 行探针全 t=0.0），所以动画改成
     *  客户端自己的时钟：一看到蓄力就开始计时。*/
    private static long tnl$chargeStart = -1L;

    @Override
    public void render(KtuluMomo entity, float entityYaw, float partialTicks,
                       com.mojang.blaze3d.vertex.PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        boolean charging = entity.tnl$isCharging();
        if (!charging && mc.player != null) {
            charging = mc.player.getCooldowns().isOnCooldown(
                    com.mofengbaizhi.tinkersnewlife.content.ModItems.AEONIAN_SKILL_MARKER.get());
        }
        long now = mc.level != null ? mc.level.getGameTime() : 0L;
        // TNL1155 和弦姿势只能给和弦用。通用技能标记是每个技能都会写的，
        // 所以在此之前音波的前摇看起来和和弦一模一样。
        // TNL1161 和弦状态来自墨默实体本身，和共鸣蓄力同一条路。
        // TNL1169 姿势现在统一来自同步的法术通道
        if (entity.tnl$spell() != 0) {
            charging = true;
        }
        // TNL1216 姿势的唯一来源：技能通道的法术编号 → 姿势枚举
        com.mofengbaizhi.tinkersnewlife.content.skill.MomoPose tnl$pose =
                com.mofengbaizhi.tinkersnewlife.content.skill.MomoPose.of(entity.tnl$spell());
        boolean chordCast = tnl$pose == com.mofengbaizhi.tinkersnewlife.content.skill.MomoPose.CHORD
                && com.mofengbaizhi.tinkersnewlife.content.skill.MomoSkills.DISCORDANT_CHORD;
        boolean tnl$blinkPose = tnl$pose == com.mofengbaizhi.tinkersnewlife.content.skill.MomoPose.BLINK;
        boolean tnl$resonancePose = tnl$pose == com.mofengbaizhi.tinkersnewlife.content.skill.MomoPose.RESONANCE;
        // TNL1158 探针：和弦前摇仍然没姿势，所以把客户端实际看到的状态打出来
        if (System.currentTimeMillis() - tnl$poseLog > 1000L) {
            tnl$poseLog = System.currentTimeMillis();
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                    "TNL1158 pose charging={} skillCd={} chordCd={} chordCast={} entityCharging={}",
                    charging,
                    mc.player != null && mc.player.getCooldowns().isOnCooldown(
                            com.mofengbaizhi.tinkersnewlife.content.ModItems.AEONIAN_SKILL_MARKER.get()),
                    chordCast, entity.tnl$isChordCasting(), entity.tnl$isCharging());
        }
        if (!charging && !chordCast) {
            // TNL1113 for the chord the clock is held while the rings are still travelling, so a
            // dropped sync frame cannot restart the animation and desync it from the hit detection
            if (!(com.mofengbaizhi.tinkersnewlife.content.skill.MomoSkills.DISCORDANT_CHORD
                    && tnl$chargeStart >= 0L
                    && (now - tnl$chargeStart) < 40L + 5L * 33L + 120L)) {
                tnl$chargeStart = -1L;
            }
        } else if (tnl$chargeStart < 0L) {
            tnl$chargeStart = now;
        }
        float progress = tnl$chargeStart < 0L ? 0.0F
                : Math.max(0.0F, Math.min(1.0F, (now - tnl$chargeStart) / 40.0F));
        // TNL1163 和弦抬手消失的**根因**：模型里有一句早退
        // `if (!CHARGING) return;`，而 CHARGING 只有共鸣会为真。和弦走的是
        // 自己的标志，所以 CHARGING 一直是 false，模型根本没走到 CHORD 分支就返回了。
        com.mofengbaizhi.tinkersnewlife.client.model.KtuluMomoModel.CHARGING =
                charging || chordCast || tnl$blinkPose || tnl$resonancePose;
        com.mofengbaizhi.tinkersnewlife.client.model.KtuluMomoModel.PROGRESS = progress;
        // TNL1156 明确告诉模型这一帧属于哪个技能
        com.mofengbaizhi.tinkersnewlife.client.model.KtuluMomoModel.CHORD = chordCast;
        com.mofengbaizhi.tinkersnewlife.client.model.KtuluMomoModel.BLINK = tnl$blinkPose;
        // TNL1129 原型（§1166 那套）是在实体渲染链里画的，这里照做。
        // 用局部坐标：姿态矩阵已经落在实体上，残影就填相对它的偏移。
        // TNL1130 现在由原型 ThrustGhostRenderer 画，不必再画一遍
        // tnl$drawBlinkGhosts(entity, poseStack, buffer);
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        // TNL1107 和弦的实体圆环几何，和模型一样从实体上画
        // TNL1114 用户："算了，恢复粒子环" —— 贴图四边形方案关闭。
        // if (com.mofengbaizhi.tinkersnewlife.content.skill.MomoSkills.DISCORDANT_CHORD
        //         && tnl$chargeStart >= 0L) {
        //     tnl$drawChordRings(entity, poseStack, buffer, now - tnl$chargeStart);
        // }
    }

    @Override
    public ResourceLocation getTextureLocation(KtuluMomo entity) {
        return TEXTURE;   // TNL1091 restored, my earlier slice had removed it
    }

    /** TNL1129 瞬身残影：把玩家模型多画几份 */
    private static HumanoidModel<?> tnl$ghostModel;

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void tnl$drawBlinkGhosts(KtuluMomo entity, PoseStack poseStack,
                                            net.minecraft.client.renderer.MultiBufferSource buffer) {
        var ghosts = com.mofengbaizhi.tinkersnewlife.client.renderer.ThrustGhostCache.alive();
        if (ghosts.isEmpty()) {
            return;
        }
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (tnl$ghostModel == null) {
            try {
                tnl$ghostModel = new HumanoidModel(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER));
            } catch (Throwable t) {
                return;
            }
        }
        // 交给共用的实体批处理去 flush，和模型本身一样
        var vc = buffer.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(TEXTURE));
        int now = mc.player.tickCount;
        double ox = entity.getX();
        double oy = entity.getY();
        double oz = entity.getZ();
        for (com.mofengbaizhi.tinkersnewlife.client.renderer.ThrustGhostCache.Ghost g : ghosts) {
            float fade = 1.0F - g.progress(now);
            if (fade <= 0.15F) {
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(g.x - ox, g.y - oy, g.z - oz);
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-g.yaw));
            poseStack.scale(-1.0F, -1.0F, 1.0F);
            tnl$ghostModel.renderToBuffer(poseStack, vc, 0xF000F0,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                    0.08F, 0.09F, 0.12F, 0.55F * fade);
            poseStack.popPose();
        }
    }

    /** TNL1107 五道细环：每道都从墨默脚下（半径 0）开始，彼此间隔 33 tick */
    private static void tnl$drawChordRings(KtuluMomo entity, PoseStack poseStack,
                                          net.minecraft.client.renderer.MultiBufferSource buffer, long elapsed) {
        double after = elapsed - 40.0D;                     // 40 ticks of charge first
        if (after <= 0.0D) {
            return;
        }
        com.mojang.blaze3d.vertex.VertexConsumer vc =
                buffer.getBuffer(net.minecraft.client.renderer.RenderType.lightning());
        org.joml.Matrix4f mat = poseStack.last().pose();
        double baseY = entity.getY() + 0.02D;
        for (int i = 0; i < 5; i++) {
            double r = (after - i * 33.0D) * 0.15D;
            if (r <= 0.05D || r > 16.0D) {
                continue;
            }
            float fade = (float) Math.max(0.0D, 1.0D - r / 16.0D);
            // TNL1112 姿态矩阵已经平移到实体上，所以环必须用
            // **局部坐标**发出；传世界坐标会把它推走。
            tnl$ringQuad(null, mat, 0.0F, 0.03F, 0.0F, (float) r,
                    Math.max(0.20F, fade), buffer);
        }
    }

    /** 一道环用一圈四边形画出来（内径 r-w、外径 r+w），高 0.5 格 */
    /** TNL1110 几何带一直画不出来，所以改成"躺在地上的平面四边形 ＋ 灰色圆环贴图"，
     *  从中往外生长。*/
    private static final net.minecraft.resources.ResourceLocation RING_TEX =
            new net.minecraft.resources.ResourceLocation("tinkersnewlife", "textures/effect/chord_ring.png");

    private static void tnl$ringQuad(com.mojang.blaze3d.vertex.VertexConsumer ignored,
                                     org.joml.Matrix4f mat, float cx, float y, float cz, float h, float alpha,
                                     net.minecraft.client.renderer.MultiBufferSource buffer) {
        com.mojang.blaze3d.vertex.VertexConsumer vc = buffer.getBuffer(
                net.minecraft.client.renderer.RenderType.entityTranslucent(RING_TEX));
        int light = 0xF000F0;
        vc.vertex(mat, cx - h, y, cz - h).color(1.0F, 1.0F, 1.0F, alpha).uv(0.0F, 0.0F)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(0.0F, 1.0F, 0.0F).endVertex();
        vc.vertex(mat, cx + h, y, cz - h).color(1.0F, 1.0F, 1.0F, alpha).uv(1.0F, 0.0F)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(0.0F, 1.0F, 0.0F).endVertex();
        vc.vertex(mat, cx + h, y, cz + h).color(1.0F, 1.0F, 1.0F, alpha).uv(1.0F, 1.0F)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(0.0F, 1.0F, 0.0F).endVertex();
        vc.vertex(mat, cx - h, y, cz + h).color(1.0F, 1.0F, 1.0F, alpha).uv(0.0F, 1.0F)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(0.0F, 1.0F, 0.0F).endVertex();
    }

    @Override
    protected void scale(KtuluMomo entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }

}
