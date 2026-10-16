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

    /**
     * TNL1245 动画的唯一入口：**技能**通过同步通道上报（摆什么姿势、播到几分），
     * 渲染器只做搬运。
     *
     * <p>⚠ 之前这段在一次回滚里被误删，结果 `KtuluMomoModel.POSE` 永远是 NONE，
     * 模型一进来就 `if (POSE == NONE) return;` ⇒ **所有姿势/动画全部不播**。
     * 现在恢复，并且不再自己算进度（旧的客户端时钟那套已不需要）。
     */
    @Override
    public void render(KtuluMomo entity, float entityYaw, float partialTicks,
                       com.mojang.blaze3d.vertex.PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight) {
        int tnl$poseId = entity.tnl$animPose();
        com.mofengbaizhi.tinkersnewlife.content.skill.MomoPose[] tnl$values =
                com.mofengbaizhi.tinkersnewlife.content.skill.MomoPose.values();
        com.mofengbaizhi.tinkersnewlife.client.model.KtuluMomoModel.POSE =
                tnl$poseId > 0 && tnl$poseId < tnl$values.length
                        ? tnl$values[tnl$poseId]
                        : com.mofengbaizhi.tinkersnewlife.content.skill.MomoPose.NONE;
        com.mofengbaizhi.tinkersnewlife.client.model.KtuluMomoModel.PROGRESS = entity.tnl$animProgress();
        // TNL1255 生息绝响：领域改用**真渲染**（贴地六芒星，几何线，不要贴图）。
        if (com.mofengbaizhi.tinkersnewlife.client.model.KtuluMomoModel.POSE
                == com.mofengbaizhi.tinkersnewlife.content.skill.MomoPose.REQUIEM) {
            tnl$drawRequiemHexagram(entity, poseStack, buffer);
        }
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    /**
     * TNL1255 生息绝响的领域：**贴地六芒星**（两个等边三角形错开 60°）＋ 一个外圈，
     * 随时间缓慢转动，随动画进度淡入淡出。
     *
     * <p>用几何线画（{@code RenderType.lightning()}，不动任何贴图素材）——
     * 粒子版只是过渡，这里是真渲染。局部坐标：姿态矩阵已经落在实体上，
     * 所以线必须用**相对实体**的偏移发出。
     */
    private static void tnl$drawRequiemHexagram(KtuluMomo entity,
                                                com.mojang.blaze3d.vertex.PoseStack poseStack,
                                                net.minecraft.client.renderer.MultiBufferSource buffer) {
        float tnl$p = Math.max(0.0F, Math.min(1.0F,
                com.mofengbaizhi.tinkersnewlife.client.model.KtuluMomoModel.PROGRESS));
        // 淡入 0~0.1，淡出 0.85~1
        float alpha = tnl$p < 0.1F ? tnl$p / 0.1F
                : (tnl$p > 0.85F ? (1.0F - tnl$p) / 0.15F : 1.0F);
        alpha = Math.max(0.05F, Math.min(1.0F, alpha));
        float radius = 6.0F;
        float spin = (entity.tickCount % 1200) * 0.3F;      // 缓慢转动
        com.mojang.blaze3d.vertex.VertexConsumer vc =
                buffer.getBuffer(net.minecraft.client.renderer.RenderType.lightning());
        org.joml.Matrix4f mat = poseStack.last().pose();
        float y = 0.03F;
        // 两个等边三角形
        for (int tri = 0; tri < 2; tri++) {
            float off = spin + tri * 60.0F;
            for (int i = 0; i < 3; i++) {
                double a1 = Math.toRadians(off + i * 120.0F);
                double a2 = Math.toRadians(off + ((i + 1) % 3) * 120.0F);
                float x1 = (float) Math.cos(a1) * radius;
                float z1 = (float) Math.sin(a1) * radius;
                float x2 = (float) Math.cos(a2) * radius;
                float z2 = (float) Math.sin(a2) * radius;
                vc.vertex(mat, x1, y, z1).color(0.35F, 0.95F, 0.75F, alpha).endVertex();
                vc.vertex(mat, x2, y, z2).color(0.35F, 0.95F, 0.75F, alpha).endVertex();
            }
        }
        // 外圈（36 段折线接地成一个圆）
        int seg = 48;
        float outer = radius * 1.18F;
        for (int i = 0; i < seg; i++) {
            double a1 = Math.toRadians(spin * 0.5D + i * (360.0D / seg));
            double a2 = Math.toRadians(spin * 0.5D + (i + 1) * (360.0D / seg));
            vc.vertex(mat, (float) Math.cos(a1) * outer, y, (float) Math.sin(a1) * outer)
                    .color(0.25F, 0.75F, 0.60F, alpha * 0.7F).endVertex();
            vc.vertex(mat, (float) Math.cos(a2) * outer, y, (float) Math.sin(a2) * outer)
                    .color(0.25F, 0.75F, 0.60F, alpha * 0.7F).endVertex();
        }
    }

    @Override
    protected void scale(KtuluMomo entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }

}
