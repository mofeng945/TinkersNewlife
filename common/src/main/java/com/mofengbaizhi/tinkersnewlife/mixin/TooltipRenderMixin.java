package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.client.tooltip.StarfieldPainter;
import com.mofengbaizhi.tinkersnewlife.client.tooltip.StarfieldState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TNL899 \u2605\u2605 \u7528**\u539f\u7248\u7b97\u597d\u7684\u6846**\u753b\u661f\u7a7a \u2713
 *
 * <p>\u2605\u7528\u6237\u53e3\u5f84\uff1a\u2605"\u4f60\u4e0d\u80fd\u8d70\u539f\u7248\u63d0\u793a\u6846\u8ba1\u7b97\u65b9\u6cd5\u5417" \u2713
 * \u2605\u80fd \u2713\uff1a\u539f\u7248\u753b\u6846\u5c31\u662f {@code TooltipRenderUtil.m_280205_} \u2713
 * \uff08\u2605\u771f\u5b9e\u63cf\u8ff0\u7b26\uff1a{@code (Lnet/minecraft/client/gui/GuiGraphics;IIIII)V} \u2713\uff09\u3002
 *
 * <p>\u2605\u4e24\u6761\u8def\u5f84\u90fd\u6df7 \u2713\uff1a
 * <ul>
 *   <li>{@code m_280205_}\uff1a\u2605\u539f\u7248\u9ed8\u8ba4\u80cc\u666f \u2713</li>
 *   <li>{@code m_280115_}\uff1a\u2605Forge \u6539\u8272\u540e\u8d70\u7684\u91cd\u8f7d \u2713
 *       \u2014\u2014\u2605\u522b\u7684\u6a21\u7ec4\u4e5f\u53ef\u80fd\u89e6\u53d1\u5b83 \u2717\uff0c\u2605\u4e0d\u6df7\u5c31\u6f0f\u753b \u2713</li>
 * </ul>
 *
 * <p>\u2605\u4e0a\u4e00\u7248\u4e3a\u4ec0\u4e48\u5931\u8d25 \u2717\uff1a\u2605\u6211\u628a 8 \u53c2\u90a3\u6761\u7684\u53c2\u6570**\u6570\u9519\u4e86** \u2717
 * \uff08{@code InvalidInjectionException: Invalid descriptor} \u2713\uff09\u3002
 * \u2605\u73b0\u5728\u53ea\u58f0\u660e\u53c2\u6570\u7684**\u524d\u7f00** \u2713\uff08{@code g, x, y, width, height} \u002b {@code CallbackInfo} \u2713\uff09
 * \u21d2 \u2605\u65e0\u8bba\u76ee\u6807\u6709\u591a\u5c11\u4e2a\u53c2\u6570\u90fd\u80fd\u5bf9\u4e0a \u2713\u2713
 */
@Mixin(TooltipRenderUtil.class)
public class TooltipRenderMixin {

    private static long TNL$LAST = 0L;

    @Inject(method = "m_280205_", at = @At("TAIL"), remap = false)
    private static void tinkersnewlife$starfield$std(GuiGraphics g, int x, int y, int width, int height,
                                                     CallbackInfo ci) {
        tinkersnewlife$draw(g, x, y, width, height, "std");
    }

    @Inject(method = "m_280115_", at = @At("TAIL"), remap = false)
    private static void tinkersnewlife$starfield$colored(GuiGraphics g, int x, int y, int width, int height,
                                                         CallbackInfo ci) {
        tinkersnewlife$draw(g, x, y, width, height, "colored");
    }

    private static void tinkersnewlife$draw(GuiGraphics g, int x, int y, int width, int height, String which) {
        if (!StarfieldState.active) {
            return;
        }
        StarfieldState.active = false;
        try {
            StarfieldPainter.draw(g, x, y, width, height);
            long now = System.currentTimeMillis();
            if (now - TNL$LAST > 3000L) {          // TNL899 \u2605\u9650\u6d41\u63a2\u9488 \u2713
                TNL$LAST = now;
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                        "TNL899 starfield drawn via {} at {},{},{}x{}", which, x, y, width, height);
            }
        } catch (Throwable ignored) {
        }
    }
}
