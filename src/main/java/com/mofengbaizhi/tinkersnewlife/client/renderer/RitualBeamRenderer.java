package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.entity.RitualBeamEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 咒力核心仪式·<b>信标光柱</b>渲染：直接复用原版 {@link BeaconRenderer#renderBeaconBeam}，
 * 因此看起来就是真正的信标光束（带旋转的竖向流光纹理 + 外层辉光），而不是粒子泡泡。
 *
 * <p>颜色来自实体同步数据（= 量器内材料流体的颜色），高度也是同步下来的。
 */
public class RitualBeamRenderer extends EntityRenderer<RitualBeamEntity> {

    public RitualBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    /** 光柱很高，不能被视锥剔除掉（否则走远一点光柱就消失） */
    @Override
    public boolean shouldRender(RitualBeamEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public void render(RitualBeamEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        int color = entity.getBeamColor();
        float[] colors = new float[]{
                ((color >> 16) & 0xFF) / 255.0f,
                ((color >> 8) & 0xFF) / 255.0f,
                (color & 0xFF) / 255.0f
        };
        long gameTime = entity.level().getGameTime();

        // 原版签名：(poseStack, buffer, texture, partialTick, textureScale, gameTime,
        //            yOffset, height, colors, beamRadius, glowRadius)
        BeaconRenderer.renderBeaconBeam(
                poseStack, buffer, BeaconRenderer.BEAM_LOCATION,
                partialTicks, 1.0F, gameTime,
                entity.getYOffset(), entity.getBeamHeight(),
                colors, 0.2F, 0.25F);
    }

    @Override
    public ResourceLocation getTextureLocation(RitualBeamEntity entity) {
        return BeaconRenderer.BEAM_LOCATION;
    }
}
