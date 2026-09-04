package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.PhantomRenderer;
import net.minecraft.world.entity.monster.Phantom;

/**
 * 炎熔操术 · 幻翼（焰羽）渲染器：复用原版幻翼模型/纹理/动画，整体缩放到 1/8。
 */
public class FlamePhantomRenderer extends PhantomRenderer {

    public FlamePhantomRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.05F;
    }

    @Override
    public void render(Phantom entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.scale(0.125F, 0.125F, 0.125F);
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        poseStack.popPose();
    }
}
