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
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.43F);
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    protected void scale(KtuluMomo entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(KtuluMomo entity) {
        return TEXTURE;
    }
}
