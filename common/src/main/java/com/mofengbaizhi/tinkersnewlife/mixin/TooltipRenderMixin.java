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
 * TNL898 \u2605\u2605 \u76f4\u63a5\u7528**\u539f\u7248\u7b97\u597d\u7684\u6846**\u753b\u661f\u7a7a \u2713
 *
 * <p>\u2605\u7528\u6237\u53e3\u5f84\uff1a\u2605"\u4f60\u4e0d\u80fd\u8d70\u539f\u7248\u63d0\u793a\u6846\u8ba1\u7b97\u65b9\u6cd5\u5417" \u2713
 * \u2605\u80fd \u2713\uff1a\u539f\u7248\u753b\u6846\u5c31\u662f
 * {@code TooltipRenderUtil.m_280205_(GuiGraphics, x, y, width, height, z)} \u2713
 * \uff08\u2605\u6211\u5df2\u7528 javap \u67e5\u5230\u771f\u5b9e\u7b7e\u540d \u2713\uff09\u3002
 * \u2605\u6df7\u5165\u5b83\u7684 HEAD \u21d2 \u2605\u76f4\u63a5\u62ff\u5230\u539f\u7248\u7b97\u51fa\u7684 x/y/w/h \u2713
 * \uff08\u2605\u4e0d\u518d\u81ea\u5df1\u4f30\u884c\u6570/\u5b57\u5bbd \u2717\uff09\u3002
 * \u2605\u5f69\u8272\u7248\uff08Forge \u6539\u8272\u65f6\u8d70\u7684 8 \u53c2\u91cd\u8f7d\uff09\u4e5f\u4e00\u5e76\u6df7 \u2713\u3002
 */
@Mixin(TooltipRenderUtil.class)
public class TooltipRenderMixin {

    @Inject(method = "m_280205_", at = @At("HEAD"), remap = false)
    private static void tinkersnewlife$starfield$std(GuiGraphics g, int x, int y, int width, int height,
                                                     int z, CallbackInfo ci) {
        tinkersnewlife$starfield(g, x, y, width, height);
    }

    @Inject(method = "m_280115_", at = @At("HEAD"), remap = false)
    private static void tinkersnewlife$starfield$colored(GuiGraphics g, int x, int y, int width, int height,
                                                         int z, int bg, int bs, int be, CallbackInfo ci) {
        tinkersnewlife$starfield(g, x, y, width, height);
    }

    private static void tinkersnewlife$starfield(GuiGraphics g, int x, int y, int width, int height) {
        if (!StarfieldState.active) {
            return;
        }
        StarfieldState.active = false;      // \u2605\u4e00\u6b21\u6027\uff08\u2605\u6bcf\u5e27\u7531 Pre \u4e8b\u4ef6\u91cd\u65b0\u7f6e\u4f4d \u2713\uff09
        try {
            StarfieldPainter.draw(g, x, y, width, height);
        } catch (Throwable ignored) {
        }
    }
}
