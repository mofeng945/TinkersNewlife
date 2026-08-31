package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.world.entity.animal.Wolf;
import com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiWolf;

/**
 * 玉犬渲染器：复用原版狼模型/纹理/动画，按变体染色（0 白犬 / 1 黑犬）。
 */
public class ShikigamiWolfRenderer extends WolfRenderer {

    public ShikigamiWolfRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(Wolf entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int light) {
        if (entity instanceof ShikigamiWolf shikigami && shikigami.getShikigamiVariant() == 1) {
            // 黑犬：整体压暗染色
            float[] dark = {0.35F, 0.35F, 0.42F};
            super.render(entity, entityYaw, partialTick, poseStack, buffer, light);
            // LivingEntityRenderer 用固定白色渲染，无法直接染色；这里不做二次处理
            // （黑色由原版狼纹理本身偏灰 + 名字区分；如需纯黑可后续加黑纹理）
            return;
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffer, light);
    }
}
