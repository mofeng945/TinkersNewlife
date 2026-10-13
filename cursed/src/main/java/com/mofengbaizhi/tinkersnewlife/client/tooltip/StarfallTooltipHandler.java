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
     * TNL896 \u2605\u2605 \u661f\u7a7a\u6846\u7684**\u4f4d\u7f6e\u4fee\u6b63** \u2713
     *
     * <p>\u2605\u7528\u6237\u622a\u56fe\u53cd\u9988\uff1a\u2605"\u661f\u7a7a\u5757\u8dd1\u5230\u9f20\u6807\u90a3\u8fb9\u53bb\u4e86" \u2713
     * \u2605\u6839\u56e0 \u2717\uff1a{@code RenderTooltipEvent.Pre} \u7ed9\u7684 ``x/y`` \u662f**\u9f20\u6807\u5750\u6807** \u2717
     * \uff08\u2605\u4e0d\u662f\u63d0\u793a\u6846\u5de6\u4e0a\u89d2 \u2717\uff09\u3002
     *
     * <p>\u2605\u73b0\u5728\uff1a\u2605\u7167**\u539f\u7248 ``renderTooltipInternal``**\u7684\u903b\u8f91\u81ea\u5df1\u7b97 \u2713\uff1a
     * <pre>
     *   x = mouseX + 12;  y = mouseY - 12;
     *   if (x + w &gt; screenW) x -= 28 + w;              // \u2605\u9760\u53f3\u8fb9 \u21d2 \u7ffb\u5230\u9f20\u6807\u5de6\u4fa7 \u2713
     *   if (y + h + 6 &gt; screenH) y = screenH - h - 6;   // \u2605\u9760\u4e0b\u8fb9 \u21d2 \u5411\u4e0a\u8d34 \u2713
     * </pre>
     * \u2605\u5bbd\u9ad8\u4e5f\u6539\u6210\u5148\u6309\u5c4f\u5bbd**\u6362\u884c**\u518d\u7b97 \u2713\uff08\u2605\u957f\u5f15\u6587\u4f1a\u6362\u884c \u2717\uff09\u3002
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPre(RenderTooltipEvent.Pre event) {
        try {
            if (!mine(event.getItemStack())) {
                return;
            }
            net.minecraft.client.gui.Font font = event.getFont();
            // TNL897 \u2605\u2605 \u884c\u6570\u4fee\u6b63\uff1a\u2605\u5fc5\u987b\u8ddf\u539f\u7248\u4e00\u6837\u5224\u65ad"\u9ad8\u7ea7\u63d0\u793a" \u2713
            //   \u2605\u7528\u6237\u622a\u56fe\uff1a\u2605\u661f\u7a7a\u6761**\u53ea\u76d6\u4f4f\u524d\u4e24\u884c** \u2717
            //   \u2605\u56e0\u4e3a\u539f\u7248\u5f00\u4e86 F3+H \u540e\u4f1a\u591a\u51fa id/NBT \u884c \u2717
            //   \u2605\u800c\u6211\u7528 NORMAL \u53d6\u884c \u21d2 \u2605\u5c11\u7b97\u4e86\u51e0\u884c \u2713
            boolean tnl$adv = net.minecraft.client.Minecraft.getInstance().options.advancedItemTooltips;
            net.minecraft.world.item.TooltipFlag tnl$flag = tnl$adv
                    ? net.minecraft.world.item.TooltipFlag.Default.ADVANCED
                    : net.minecraft.world.item.TooltipFlag.Default.NORMAL;
            java.util.List<net.minecraft.network.chat.Component> lines = event.getItemStack().getTooltipLines(
                    net.minecraft.client.Minecraft.getInstance().player, tnl$flag);
            java.util.List<net.minecraft.util.FormattedCharSequence> wrapped = new java.util.ArrayList<>();
            for (net.minecraft.network.chat.Component c : lines) {
                wrapped.addAll(font.split(c, Math.max(60, event.getScreenWidth() - 60)));
            }
            int w = 0;
            for (net.minecraft.util.FormattedCharSequence t : wrapped) {
                w = Math.max(w, font.width(t));
            }
            int h = Math.max(10, wrapped.size() * 10);
            int x = event.getX() + 12;
            int y = event.getY() - 12;
            if (x + w > event.getScreenWidth()) {
                x -= 28 + w;
            }
            if (y + h + 6 > event.getScreenHeight()) {
                y = event.getScreenHeight() - h - 6;
            }
            StarfieldPainter.draw(event.getGraphics(), x - 3, y - 3, w + 6, h + 6);
        } catch (Throwable ignored) {
        }
    }
}
