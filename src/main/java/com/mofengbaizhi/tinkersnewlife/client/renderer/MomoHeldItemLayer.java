package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.client.model.MomoGeoModel;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/** Equipment is synced by vanilla; this layer only attaches it to the animated hand. */
public final class MomoHeldItemLayer extends BlockAndItemGeoLayer<KtuluMomo> {
    public MomoHeldItemLayer(GeoRenderer<KtuluMomo> renderer) { super(renderer); }

    @Override
    protected ItemStack getStackForBone(GeoBone bone, KtuluMomo momo) {
        if (MomoGeoModel.GHOST_PASS || !"rightArm".equals(bone.getName())) return null;
        ItemStack stack = momo.getMainHandItem();
        return stack.isEmpty() ? null : stack;
    }

    @Override
    protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, KtuluMomo momo) {
        return ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    @Override
    protected void renderStackForBone(PoseStack pose, GeoBone bone, ItemStack stack, KtuluMomo momo,
                                      MultiBufferSource buffers, float partialTick, int light, int overlay) {
        pose.pushPose();
        try {
            // Shoulder-pivot attachment, calibrated visually in game before final art alignment.
            pose.translate(0, -0.625, 0);
            pose.mulPose(Axis.XP.rotationDegrees(-90));
            super.renderStackForBone(pose, bone, stack, momo, buffers, partialTick, light, overlay);
        } finally {
            pose.popPose();
        }
    }
}
