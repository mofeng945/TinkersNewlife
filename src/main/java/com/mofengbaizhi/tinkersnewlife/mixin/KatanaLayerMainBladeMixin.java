package com.mofengbaizhi.tinkersnewlife.mixin;

// 移植自 TiCEX (MIT): moffy.ticex.mixin.slashblade.LayerMainBladeMixin

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mofengbaizhi.tinkersnewlife.client.slashblade.context.ContextFrame;
import com.mofengbaizhi.tinkersnewlife.client.slashblade.context.ItemRenderContext;
import com.mofengbaizhi.tinkersnewlife.client.slashblade.context.KatanaContexts;
import com.mojang.blaze3d.vertex.PoseStack;
import jp.nyatla.nymmd.MmdMotionPlayerGL2;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.client.renderer.layers.LayerMainBlade;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import slimeknights.tconstruct.library.tools.item.IModifiable;

/**
 * 第三人称"手里那把刀"的渲染层 —— 移植 TiCEX {@code LayerMainBladeMixin}（MIT）。
 *
 * <p>把 {@code LayerMainBlade} 那个 lambda（真正画主手刀的地方 ✓）包一层：
 * 若正在画的是**匠魂物品**（= 我们的拔刀剑 ✓）就压入渲染上下文 ✓，
 * 于是第三人称的刀也会按材料换贴图/换色 ✓。
 *
 * <p>⚠ 已核对字节码：{@code lambda$render$4(ISlashBladeState, LivingEntity, float, PoseStack, float, double, double,
 * ItemStack, MultiBufferSource, int, MmdMotionPlayerGL2)} 存在 ✓（`javap -p` 实查 ✓）。
 */
@Mixin(value = LayerMainBlade.class, remap = false)
public class KatanaLayerMainBladeMixin {

    @WrapMethod(method = "lambda$render$4")
    private void renderWith(ISlashBladeState s, LivingEntity entity, float partialTicks, PoseStack matrixStack,
                            float motionYOffset, double motionScale, double modelScaleBase, ItemStack stack,
                            MultiBufferSource bufferIn, int lightIn, MmdMotionPlayerGL2 mmp, Operation<Void> original) {
        if (!(stack.getItem() instanceof IModifiable)) {
            original.call(s, entity, partialTicks, matrixStack, motionYOffset, motionScale, modelScaleBase, stack, bufferIn, lightIn, mmp);
            return;
        }

        ItemRenderContext itemRenderContext = new ItemRenderContext(
                stack,
                ItemDisplayContext.FIXED,
                false,
                matrixStack,
                bufferIn,
                lightIn,
                OverlayTexture.NO_OVERLAY
        );

        try (ContextFrame<ItemRenderContext> local = KatanaContexts.SB_RENDERING_CONTEXT.open(itemRenderContext)) {
            original.call(s, entity, partialTicks, matrixStack, motionYOffset, motionScale, modelScaleBase, stack, bufferIn, lightIn, mmp);
        }
    }
}
