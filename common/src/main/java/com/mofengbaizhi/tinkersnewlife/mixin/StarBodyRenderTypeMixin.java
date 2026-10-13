package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.client.render.StarBodyClient;
import com.mofengbaizhi.tinkersnewlife.content.item.StarfallLamentItem;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TNL940 \u2605\u2605 \u628a\u661f\u9668\u60b2\u53f9\u7684\u6e32\u67d3\u7c7b\u578b\u6362\u6210**\u661f\u7a7a\u7740\u8272\u5668** \u2713
 *
 * <p>\u2605\u4e3a\u4ec0\u4e48\u8d70\u6df7\u5165 \u2717\uff1a\u2605Forge \u7684 ``IClientItemExtensions``
 * **\u6ca1\u6709** ``getRenderType`` \u2717\uff08\u2605\u6211\u5df2\u7528 javap \u6838\u5b9e \u2713\uff09\uff0c
 * \u2605\u539f\u7248\u7269\u54c1\u7684\u7c7b\u578b\u7edf\u4e00\u7531
 * ``ItemBlockRenderTypes#m_109279_(ItemStack, boolean)`` \u51b3\u5b9a \u2713
 * \u21d2 \u2605\u5728\u90a3\u91cc\u63d2\u4e00\u624b\u6700\u5e72\u51c0 \u2713\uff08\u2605\u624b\u6301/\u2605GUI/\u2605\u6389\u843d\u7269\u5168\u8986\u76d6 \u2713\uff09\u3002
 *
 * <p>\u2605\u515c\u5e95 \u2713\uff1a\u2605\u7740\u8272\u5668\u6ca1\u52a0\u8f7d\u597d\u65f6 ``starBody()`` \u8fd4\u56de ``null``
 * \u21d2 \u2605\u4e0d\u63d2\u624b \u2713 \u21d2 \u2605\u81ea\u52a8\u9000\u56de\u539f\u7248\u6e32\u67d3 \u2713\u3002
 */
@Mixin(ItemBlockRenderTypes.class)
public class StarBodyRenderTypeMixin {

    @Inject(method = "m_109279_", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tinkersnewlife$starBody(ItemStack stack, boolean fabulous,
                                                CallbackInfoReturnable<RenderType> cir) {
        try {
            if (stack != null && stack.getItem() instanceof StarfallLamentItem) {
                RenderType rt = StarBodyClient.starBody();
                if (rt != null) {
                    cir.setReturnValue(rt);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
