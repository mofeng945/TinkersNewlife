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
 * TNL950 \u2605\u2605 **\u53ea\u6362\u7c7b\u578b**\uff0c\u2605\u4e0d\u78b0\u51e0\u4f55 \u2713
 *
 * <p>\u2605\u7528\u6237\u9009\u7684\u8def\uff1a\u2605"\u7ee7\u7eed"\uff08\u2605\u6309\u7b2c\u4e00\u6761\uff1a\u53ea\u6539 renderType \u2713\uff09
 *
 * <p>\u2605\u4e3a\u4ec0\u4e48\u8fd9\u6b21\u80fd\u6210 \u2713\uff1a
 * \u2605\u4e0a\u4e00\u6b21\u6211\u8bd5\u8fc7\u540c\u4e00\u4e2a\u94a9\u5b50\u4f46**\u7740\u8272\u5668\u8fd8\u6ca1\u786e\u8ba4\u80fd\u7f16\u8bd1** \u2717\uff1b
 * \u2605\u73b0\u5728\u65e5\u5fd7\u5df2\u7ecf\u8bc1\u5b9e ``TNL944 star_body shader compiled ok`` \u2713
 * \u21d2 \u2605\u8fd9\u6761\u8def\u6709\u671b\u76f4\u63a5\u751f\u6548 \u2713\u3002
 *
 * <p>\u2605\u5b83\u53ea\u6539"\u2605\u8fd9\u4e2a\u7269\u54c1\u7528\u54ea\u4e2a\u6e32\u67d3\u7c7b\u578b" \u2713\uff1a
 * \u2605\u51e0\u4f55\u3001\u6279\u6b21\u3001\u59ff\u6001\u5168\u90e8\u4ecd\u7531\u539f\u7248\u7ba1\u7ebf\u5904\u7406 \u2713
 * \u21d2 \u2605**\u4e0d\u53ef\u80fd\u518d\u51fa\u73b0"\u5f13\u4e0d\u89c1"** \u2713\uff08\u2605\u9664\u975e\u7740\u8272\u5668\u672c\u8eab\u753b\u4e0d\u51fa\u4e1c\u897f \u2717\uff09\u3002
 */
@Mixin(ItemBlockRenderTypes.class)
public class StarBodyRenderTypeMixin {

    @Inject(method = "m_109279_", at = @At("RETURN"), cancellable = true, remap = false)
    private static void tinkersnewlife$starBody(ItemStack stack, boolean fabulous,
                                                CallbackInfoReturnable<RenderType> cir) {
        try {
            if (stack == null || !(stack.getItem() instanceof StarfallLamentItem)) {
                return;
            }
            RenderType rt = StarBodyClient.starBodyOrNull();
            if (rt != null) {
                cir.setReturnValue(rt);
                long now = System.currentTimeMillis();
                if (now - TNL$LOG > 3000L) {                  // TNL950 \u63a2\u9488 \u2713
                    TNL$LOG = now;
                    com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                            "TNL950 renderType swapped for the bow -> {}", rt);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static long TNL$LOG = 0L;
}
