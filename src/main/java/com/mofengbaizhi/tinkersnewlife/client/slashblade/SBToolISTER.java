package com.mofengbaizhi.tinkersnewlife.client.slashblade;

// 移植自 TiCEX (MIT): moffy.ticex.client.render.slashblade.SBToolISTER

import com.mojang.blaze3d.vertex.PoseStack;
import com.mofengbaizhi.tinkersnewlife.client.slashblade.context.ContextFrame;
import com.mofengbaizhi.tinkersnewlife.client.slashblade.context.ItemRenderContext;
import com.mofengbaizhi.tinkersnewlife.client.slashblade.context.KatanaContexts;
import mods.flammpfeil.slashblade.client.renderer.SlashBladeTEISR;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 我们的拔刀剑"物品渲染器"（= TiCEX 的 {@code SBToolISTER} ✓ 逐字移植 ✓）。
 *
 * <p>它只做一件事：在调本体渲染器之前，把"当前正在渲染的物品"压进
 * {@link KatanaContexts#SB_RENDERING_CONTEXT} ✓ —— 于是 {@link TicEXSBRenderers}
 * 才知道"现在画的是哪把刀、什么显示上下文" ✓。
 */
public class SBToolISTER extends SlashBladeTEISR {

    public SBToolISTER(BlockEntityRenderDispatcher dispatcher, EntityModelSet modelSet) {
        super(dispatcher, modelSet);
    }

    @Override
    public void renderByItem(ItemStack itemStackIn, ItemDisplayContext type, PoseStack matrixStack, MultiBufferSource bufferIn,
                             int combinedLightIn, int combinedOverlayIn) {
        ItemRenderContext itemRenderContext = new ItemRenderContext(
                itemStackIn,
                type,
                type == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                        || type == ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                matrixStack,
                bufferIn,
                combinedLightIn,
                combinedOverlayIn
        );

        try (ContextFrame<ItemRenderContext> local = KatanaContexts.SB_RENDERING_CONTEXT.open(itemRenderContext)) {
            super.renderByItem(itemStackIn, type, matrixStack, bufferIn, combinedLightIn, combinedOverlayIn);
        }
    }
}
