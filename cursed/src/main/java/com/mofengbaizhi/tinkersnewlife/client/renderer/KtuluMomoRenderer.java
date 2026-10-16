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
