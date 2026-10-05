package com.mofengbaizhi.tinkersnewlife.client.slashblade.context;

// 移植自 TiCEX (MIT): moffy.ticex.client.render.provider.context.ItemRenderContext

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public record ItemRenderContext(
        ItemStack itemStack,
        ItemDisplayContext displayContext,
        boolean leftHand,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int combinedLight,
        int combinedOverlay
) {

}
