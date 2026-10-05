package com.mofengbaizhi.tinkersnewlife.client.slashblade;

// 移植自 TiCEX (MIT): moffy.ticex.client.render.slashblade.SBToolBladeItemRenderer

import com.mojang.blaze3d.vertex.PoseStack;
import com.mofengbaizhi.tinkersnewlife.client.slashblade.context.ContextFrame;
import com.mofengbaizhi.tinkersnewlife.client.slashblade.context.ItemRenderContext;
import com.mofengbaizhi.tinkersnewlife.client.slashblade.context.KatanaContexts;
import mods.flammpfeil.slashblade.client.renderer.entity.BladeItemEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * 地上那把刀的渲染器（= TiCEX 的 {@code SBToolBladeItemRenderer} ✓ 逐字移植 ✓）。
 *
 * <p>与 {@link SBToolISTER} 同一个套路 ✓：先把"正在渲染的物品 + 地面显示上下文"压进上下文栈 ✓，
 * 再交给本体的 {@code BladeItemEntityRenderer} ✓（于是掉在地上的刀同样会按材料换贴图 ✓）。
 */
public class SBToolBladeItemRenderer extends BladeItemEntityRenderer {

    public SBToolBladeItemRenderer(Context context) {
        super(context);
    }

    @Override
    public void render(ItemEntity itemIn, float entityYaw, float partialTicks, PoseStack matrixStackIn,
                       MultiBufferSource bufferIn, int packedLightIn) {
        ItemRenderContext itemRenderContext = new ItemRenderContext(
                itemIn.getItem(),
                ItemDisplayContext.GROUND,
                false,
                matrixStackIn,
                bufferIn,
                packedLightIn,
                OverlayTexture.NO_OVERLAY
        );

        try (ContextFrame<ItemRenderContext> local = KatanaContexts.SB_RENDERING_CONTEXT.open(itemRenderContext)) {
            super.render(itemIn, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);
        }
    }
}
