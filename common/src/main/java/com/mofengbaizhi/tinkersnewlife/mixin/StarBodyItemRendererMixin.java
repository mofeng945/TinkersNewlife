package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.client.render.StarBodyClient;
import com.mofengbaizhi.tinkersnewlife.content.item.StarfallLamentItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TNL947 \u2605\u2605 \u8ba9**\u666e\u901a\u7269\u54c1**\u4e5f\u80fd\u8d70\u6211\u4eec\u7684 BEWLR \u2713
 *
 * <p>\u2605\u7528\u6237\u53e3\u5f84\uff1a\u2605"1"\uff08\u2605\u9009\u6df7\u5165 ``ItemRenderer`` \u8fd9\u6761\u8def \u2713\uff09
 *
 * <p>\u2605\u4e3a\u4ec0\u4e48\u5fc5\u987b\u6df7 \u2717\uff1a\u2605\u65e5\u5fd7\u8bc1\u636e \u2713\uff08\u2605TNL946 \u8c03\u7528\u6808 \u2713\uff09
 * \u8bf4\u660e\u8fd9\u5957\u73af\u5883\u91cc {@code IClientItemExtensions#getCustomRenderer()}
 * \u2605**\u53ea\u6709 YSM\uff08\u2605\u65af\u8482\u592b\u6a21\u578b \u2713\uff09\u4e0e TaCZ\uff08\u2605\u67aa\u68b0 \u2713\uff09\u6765\u95ee** \u2717\uff0c
 * \u2605\u539f\u7248\u7269\u54c1\u6e32\u67d3\u8def\u5f84**\u4e0d\u95ee** \u2717 \u21d2 \u2605\u6211\u4eec\u7684 BEWLR \u6c38\u8fdc\u4e0d\u88ab\u62ff\u53bb\u753b\u5f13 \u2713\u3002
 * \u2605\u53c2\u8003\u5b9e\u73b0\uff08\u2605\u77ff\u77f3\u90a3\u4e24\u4e2a \u2713\uff09\u80fd\u8dd1\u662f\u56e0\u4e3a\u5b83\u4eec\u662f
 * {@code BlockItem}\uff0c\u2605\u539f\u7248\u5bf9"\u2605\u65b9\u5757\u7269\u54c1 \u002b EntityBlock"\u6709\u4e13\u95e8\u5206\u652f \u2713\u3002
 *
 * <p>\u2605\u73b0\u5728\uff1a\u2605\u5728 ``ItemRenderer#m_115143_``\uff08\u2605\u5df2 javap \u6838\u5b9e\u7684 SRG \u540d \u2713\uff09
 * \u7684 **HEAD** \u63d2\u4e00\u624b \u2713\uff1a\u2605\u53ea\u8981\u662f\u661f\u9668\u60b2\u53f9\uff0c
 * \u2605\u5c31\u7528\u6211\u4eec\u7684 BEWLR \u753b \u002b ``cancel`` \u2713\uff08\u2605\u4e0d\u4f1a\u91cd\u590d\u753b \u002b
 * \u2605\u4e0d\u4f1a\u9012\u5f52 \u2713 \u2014\u2014 \u2605BEWLR \u91cc\u76f4\u63a5\u5199\u9876\u70b9 \u2717 \u4e0d\u8c03 ItemRenderer \u2713\uff09\u3002
 */
@Mixin(ItemRenderer.class)
public class StarBodyItemRendererMixin {

    @Inject(method = "m_115143_", at = @At("HEAD"), cancellable = true, remap = false)
    private void tinkersnewlife$starBow(ItemStack stack, ItemDisplayContext context, boolean leftHand,
                                        PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                                        BakedModel model, CallbackInfo ci) {
        try {
            if (stack == null || !(stack.getItem() instanceof StarfallLamentItem)) {
                return;
            }
            if (!StarBodyClient.shaderReady()) {
                return;                       // \u2605\u7740\u8272\u5668\u6ca1\u597d \u21d2 \u2605\u8d70\u539f\u7248 \u2713
            }
            BlockEntityWithoutLevelRenderer r = StarBodyClient.rendererOrNull();
            if (r == null) {
                return;
            }
            r.renderByItem(stack, context, pose, buffers, light, overlay);
            ci.cancel();
        } catch (Throwable ignored) {
        }
    }
}
