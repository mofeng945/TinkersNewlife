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
                    com.mofengbaizhi.tinkersnewlife.content.ModItems.AEONIAN_MARKER.get());
        }
        long now = mc.level != null ? mc.level.getGameTime() : 0L;
        if (!charging) {
            tnl$chargeStart = -1L;
        } else if (tnl$chargeStart < 0L) {
            tnl$chargeStart = now;
        }
        float progress = tnl$chargeStart < 0L ? 0.0F
                : Math.max(0.0F, Math.min(1.0F, (now - tnl$chargeStart) / 40.0F));
        com.mofengbaizhi.tinkersnewlife.client.model.KtuluMomoModel.CHARGING = charging;
        com.mofengbaizhi.tinkersnewlife.client.model.KtuluMomoModel.PROGRESS = progress;
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        // TNL1107 real ring geometry for the Discordant Chord, drawn from the entity like the model
        if (com.mofengbaizhi.tinkersnewlife.content.skill.MomoSkills.DISCORDANT_CHORD
                && tnl$chargeStart >= 0L) {
            tnl$drawChordRings(entity, poseStack, buffer, now - tnl$chargeStart);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(KtuluMomo entity) {
        return TEXTURE;   // TNL1091 restored, my earlier slice had removed it
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
            tnl$ringBand(vc, mat, entity.getX(), entity.getZ(), baseY, r, 0.06D, 1.0F, 1.0F, 1.0F, 0.55F * fade + 0.2F);
        }
    }

    /** one ring drawn as a band of quads (inner radius r - w, outer r + w), 0.5 blocks tall. */
    private static void tnl$ringBand(com.mojang.blaze3d.vertex.VertexConsumer vc,
                                     org.joml.Matrix4f mat,
                                     double cx, double cz, double baseY,
                                     double r, double w, float red, float green, float blue, float alpha) {
        int seg = Math.max(32, (int) (r * 10.0D));
        double inner = Math.max(0.01D, r - w);
        double outer = r + w;
        for (int i = 0; i < seg; i++) {
            double a0 = (Math.PI * 2.0D) * i / seg;
            double a1 = (Math.PI * 2.0D) * (i + 1) / seg;
            float x0i = (float) (cx + Math.cos(a0) * inner);
            float z0i = (float) (cz + Math.sin(a0) * inner);
            float x1i = (float) (cx + Math.cos(a1) * inner);
            float z1i = (float) (cz + Math.sin(a1) * inner);
            float x0o = (float) (cx + Math.cos(a0) * outer);
            float z0o = (float) (cz + Math.sin(a0) * outer);
            float x1o = (float) (cx + Math.cos(a1) * outer);
            float z1o = (float) (cz + Math.sin(a1) * outer);
            float yb = (float) baseY;
            float yt = (float) (baseY + 0.5D);
            // two vertical sides of the band
            tnl$quad(vc, mat, x0i, yb, z0i, x0o, yb, z0o, x0o, yt, z0o, x0i, yt, z0i, red, green, blue, alpha);
            tnl$quad(vc, mat, x0o, yb, z0o, x1o, yb, z1o, x1o, yt, z1o, x0o, yt, z0o, red, green, blue, alpha);
            tnl$quad(vc, mat, x1o, yb, z1o, x1i, yb, z1i, x1i, yt, z1i, x1o, yt, z1o, red, green, blue, alpha);
            tnl$quad(vc, mat, x0i, yt, z0i, x0o, yt, z0o, x1o, yt, z1o, x1i, yt, z1i, red, green, blue, alpha);
        }
    }

    private static void tnl$quad(com.mojang.blaze3d.vertex.VertexConsumer vc, org.joml.Matrix4f mat,
                                 float x1, float y1, float z1, float x2, float y2, float z2,
                                 float x3, float y3, float z3, float x4, float y4, float z4,
                                 float red, float green, float blue, float alpha) {
        vc.vertex(mat, x1, y1, z1).color(red, green, blue, alpha).endVertex();
        vc.vertex(mat, x2, y2, z2).color(red, green, blue, alpha).endVertex();
        vc.vertex(mat, x3, y3, z3).color(red, green, blue, alpha).endVertex();
        vc.vertex(mat, x4, y4, z4).color(red, green, blue, alpha).endVertex();
    }

    @Override
    protected void scale(KtuluMomo entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }

}
