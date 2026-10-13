package com.mofengbaizhi.tinkersnewlife.client.tooltip;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.StarfallLamentItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * TNL896 \u2605\u2605 \u661f\u9668\u60b2\u53f9\u7684\u5de5\u5177\u63d0\u793a\u80cc\u666f\uff1a**\u53ef\u52a8\u661f\u7a7a** \u2713
 *
 * <p>\u2605\u7528\u4e24\u4e2a\u4e8b\u4ef6\u642d\u914d \u2713\uff1a
 * <ul>
 *   <li>{@link RenderTooltipEvent.Color}\uff08LOWEST\uff09\uff1a\u2605\u628a\u539f\u7248\u63d0\u793a\u80cc\u666f\u8bbe\u6210
 *       **\u5b8c\u5168\u900f\u660e** \u2713\uff08\u2605\u4e0d\u7136\u661f\u7a7a\u4f1a\u88ab 0xF0 \u7684\u9ed1\u5e95\u76d6\u4f4f \u2717\uff09\uff1b</li>
 *   <li>{@link RenderTooltipEvent.Pre}\uff1a\u2605\u5148\u753b\u6211\u4eec\u81ea\u5df1\u7684\u661f\u7a7a \u2713
 *       \uff08\u2605\u518d\u7531\u539f\u7248\u628a\u6587\u5b57\u753b\u5728\u4e0a\u9762 \u2713\uff09\u3002</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StarfallTooltipHandler {

    private static boolean mine(net.minecraft.world.item.ItemStack stack) {
        return stack != null && stack.getItem() instanceof StarfallLamentItem;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onColor(RenderTooltipEvent.Color event) {
        try {
            if (!mine(event.getItemStack())) {
                return;
            }
            event.setBackground(0x00000000);     // \u2605\u900f\u660e\u80cc\u666f \u2713
            event.setBorderStart(0x00000000);
            event.setBorderEnd(0x00000000);
        } catch (Throwable ignored) {
        }
    }

    /**
     * TNL896 \u2605\u2605 \u5728 {@code Pre} \u91cc\u753b\u661f\u7a7a \u2713
     *
     * <p>\u2605\u4e3a\u4f55\u81ea\u5df1\u7b97\u5bbd\u9ad8 \u2717\uff1a\u2605{@code Pre} \u6ca1\u6709 getWidth/getHeight \u2717\uff0c
     * \u800c {@code PostBackground}/{@code PostText} \u5728\u672c Forge \u7248\u672c\u91cc\u53c8\u4e0d\u5b58\u5728 \u2717
     * \u21d2 \u2605\u7528\u5de5\u5177\u63d0\u793a\u884c\u7684\u5b57\u5bbd/\u884c\u6570\u7b97\u51fa\u4e00\u4e2a\u8fd1\u4f3c\u6846 \u2713\u3002
     * \u2605\u914d\u5408 {@code Color} \u4e8b\u4ef6\u628a\u539f\u7248\u80cc\u666f\u53d8\u900f\u660e \u2713
     * \u21d2 \u2605\u661f\u7a7a\u53ef\u89c1 \u002b \u2605\u6587\u5b57\u5728\u4e0a\u9762 \u2713\u3002
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPre(RenderTooltipEvent.Pre event) {
        try {
            if (!mine(event.getItemStack())) {
                return;
            }
            java.util.List<net.minecraft.network.chat.Component> lines = event.getItemStack().getTooltipLines(
                    net.minecraft.client.Minecraft.getInstance().player,
                    net.minecraft.world.item.TooltipFlag.Default.NORMAL);
            int w = 0;
            for (net.minecraft.network.chat.Component c : lines) {
                w = Math.max(w, event.getFont().width(c));
            }
            int h = Math.max(10, lines.size() * 10) + 2;
            StarfieldPainter.draw(event.getGraphics(), event.getX() - 3, event.getY() - 3, w + 6, h + 4);
        } catch (Throwable ignored) {
        }
    }
}
