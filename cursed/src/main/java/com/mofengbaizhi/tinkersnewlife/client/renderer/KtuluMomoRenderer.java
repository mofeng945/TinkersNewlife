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
 * \u514b\u56fe\u9732\u00b7\u58a8\u9ed8\u6e32\u67d3\u5668 \u2014\u2014 \u2605\u590d\u7528\u5546\u4eba\u58a8\u9ed8\u7684\u6a21\u578b\uff08\u73a9\u5bb6\u6a21\u578b \u2713\uff09
 * \u53ea\u628a\u8d34\u56fe\u6362\u6210**\u9ed1\u767d\u7070\u5ea6\u7248** \u2713\uff08\u2605\u7528\u6237\u53e3\u5f84\uff1a\u201c\u67d3\u8272\u4e3a\u9ed1\u767d\u201d \u2713\uff09\u3002
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
     * TNL1091 feed the charge progress to the model before super.render runs (setupAnim happens
     * inside it). Progress is derived from the hidden marker cooldown, the channel we verified.
     */
    /** TNL1158 rate limit for the pose probe */
    private static long tnl$poseLog = 0L;

    /** TNL1093 the cooldown derived progress was always 0 (19 probe lines all t=0.0), so the
     *  animation runs on its own client clock: the moment charging is seen, start counting. */
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
        // TNL1155 the chord pose must only play for the chord. The generic skill marker is set by every
        // skill, so before this the sonic boom wind up looked exactly like the chord wind up.
        // TNL1161 the chord state comes from the momo itself, exactly like the resonance charge.
        boolean chordCast = entity.tnl$isChordCasting()
                && com.mofengbaizhi.tinkersnewlife.content.skill.MomoSkills.DISCORDANT_CHORD;
        // TNL1158 probe: the chord wind up still shows no pose, so log what the client actually sees
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
        com.mofengbaizhi.tinkersnewlife.client.model.KtuluMomoModel.CHARGING = charging;
        com.mofengbaizhi.tinkersnewlife.client.model.KtuluMomoModel.PROGRESS = progress;
        // TNL1156 tell the model exactly which skill this frame belongs to
        com.mofengbaizhi.tinkersnewlife.client.model.KtuluMomoModel.CHORD = chordCast;
        // TNL1129 the prototype draws its effect inside the entity render chain, so do the same here.
        // Local coordinates: the pose matrix is already on the entity, so ghosts are the offset from it.
        // TNL1130 the prototype ThrustGhostRenderer draws these now, no need to draw twice
        // tnl$drawBlinkGhosts(entity, poseStack, buffer);
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        // TNL1107 real ring geometry for the Discordant Chord, drawn from the entity like the model
        // TNL1114 user: "forget it, go back to the particle ring" -- the textured quad is off.
        // if (com.mofengbaizhi.tinkersnewlife.content.skill.MomoSkills.DISCORDANT_CHORD
        //         && tnl$chargeStart >= 0L) {
        //     tnl$drawChordRings(entity, poseStack, buffer, now - tnl$chargeStart);
        // }
    }

    @Override
    public ResourceLocation getTextureLocation(KtuluMomo entity) {
        return TEXTURE;   // TNL1091 restored, my earlier slice had removed it
    }

    /** TNL1129 ghosts of the Blink, drawn as extra copies of the player model. */
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
        // let the shared entity batch own the flush, exactly like the model itself does
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

    /** TNL1107 five thin rings, each born at Momo (radius 0) and spaced 33 ticks apart. */
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
            // TNL1112 the pose matrix is already translated onto the entity, so the ring
            // must be emitted in LOCAL coordinates. Passing world coords pushed it away.
            tnl$ringQuad(null, mat, 0.0F, 0.03F, 0.0F, (float) r,
                    Math.max(0.20F, fade), buffer);
        }
    }

    /** one ring drawn as a band of quads (inner radius r - w, outer r + w), 0.5 blocks tall. */
    /** TNL1110 the geometry band never rendered, so the ring is a grey ring TEXTURE on a flat quad
     *  lying on the ground, growing from the centre outwards. */
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
