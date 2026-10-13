package com.mofengbaizhi.tinkersnewlife.client.tooltip;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.StarfallLamentItem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * TNL900 \u2605\u2605 \u661f\u9668\u60b2\u53f9\u5de5\u5177\u63d0\u793a\uff1a**\u53ef\u52a8\u661f\u7a7a\u80cc\u666f** \u2713
 *
 * <p>\u2605\u7528\u6237\u63d0\u793a\uff1a\u2605"\u4f60\u53ef\u4ee5\u770b\u4e00\u4e0b\u8be1\u5384\u9057\u7269\u76f8\u5173\u5b9e\u73b0" \u2713
 * \u21d2 \u2605\u53cd\u7f16\u8bd1\u8be5\u6a21\u7ec4\u7684 {@code TooltipStarfieldHandler} \u2713
 * \uff08\u2605\u7528\u6237\u6574\u5408\u5305\u91cc\u5c31\u6709\u8fd9\u4e2a\u7c7b \u2713\uff09\uff0c\u5b83\u7684\u505a\u6cd5\u5c31\u662f\u6807\u51c6\u7b54\u6848 \u2713\uff1a
 *
 * <ul>
 *   <li>\u2605**\u4e0d\u9700\u8981\u6df7\u5165** \u2717\uff1b\u2605\u5c31\u7528 {@code RenderTooltipEvent.Color} \u2713\uff1b</li>
 *   <li>\u2605\u8be5\u4e8b\u4ef6\u7684 ``x/y`` **\u5c31\u662f\u63d0\u793a\u6846\u6700\u7ec8\u5750\u6807** \u2713
 *       \uff08\u2605\u4f4d\u7f6e\u5668\u5df2\u7ecf\u7b97\u5b8c \u2713 \u2014\u2014 \u2605\u8fd9\u5c31\u662f\u6211\u4e4b\u524d\u4e00\u76f4\u641e\u9519\u7684\u70b9 \u2717\uff09\uff1b</li>
 *   <li>\u2605\u5bbd\u9ad8\u76f4\u63a5\u7528 ``event.getComponents()`` \u91cc\u7684
 *       {@code ClientTooltipComponent} \u91cf\u51fa\u6765 \u2713
 *       \uff08\u2605\u8fd9\u5c31\u662f**\u539f\u7248\u81ea\u5df1\u7684\u7b97\u6cd5** \u2713\uff09\uff1b</li>
 *   <li>\u2605\u753b\u5728 ``(x-3, y-4)`` \u5230 ``(x+w+3, y+h+4)`` \u2713\uff08\u2605\u8ddf\u53c2\u8003\u5b9e\u73b0\u4e00\u81f4 \u2713\uff09\u3002</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StarfallTooltipHandler {

    private static boolean mine(net.minecraft.world.item.ItemStack stack) {
        return stack != null && stack.getItem() instanceof StarfallLamentItem;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTooltipColor(RenderTooltipEvent.Color event) {
        try {
            if (!mine(event.getItemStack())) {
                return;
            }
            event.setBackground(0);
            event.setBorderStart(0);
            event.setBorderEnd(0);

            Font font = event.getFont();
            List<ClientTooltipComponent> components = event.getComponents();
            int width = 0;
            int height = components.size() == 1 ? -2 : 0;      // \u2605\u53c2\u8003\u5b9e\u73b0\u7684\u540c\u6b3e\u4fee\u6b63 \u2713
            for (ClientTooltipComponent c : components) {
                width = Math.max(width, c.getWidth(font));
                height += c.getHeight();
            }
            if (width <= 0 || height <= 0) {
                return;
            }
            int x = event.getX();
            int y = event.getY();
            /*
             * TNL926 \u2605\u2605 \u7528\u6237\u53e3\u5f84\uff1a\u2605"\u6211\u6ca1\u8bf4\u683c\u5b50\uff0c\u6211\u8bf4\u7684\u662f**\u7269\u54c1\u6e32\u67d3\u5230\u6211\u65b0\u63d0\u793a\u6846\u80cc\u666f\u7684\u4e0a\u5c42\u4e86**" \u2713
             *
             * <p>\u2605\u6839\u56e0 \u2717\uff1a\u2605``GuiGraphics#fill`` \u9ed8\u8ba4\u753b\u5728 **z = 0** \u2717\uff0c
             * \u2605\u800c\u539f\u7248\u63d0\u793a\u6846\u80cc\u666f\u662f\u753b\u5728 **z = 400** \u2713
             * \uff08\u2605``renderTooltipInternal`` \u91cc\u4f20\u7684\u5c31\u662f 400 \u2713\uff09
             * \u21d2 \u2605\u7269\u54c1\u56fe\u6807\uff08\u2605\u540c\u5c4f\u5e55\u540e\u753b \u2717\uff09\u53cd\u800c\u538b\u5728\u661f\u7a7a\u4e0a\u9762 \u2717\u3002
             *
             * <p>\u2605\u73b0\u5728 \u2713\uff1a\u2605\u628a\u661f\u7a7a\u6574\u4f53**\u6cbf z \u5e73\u79fb +400** \u2713
             * \uff08\u2605pose \u63a8\u5165/\u5f39\u51fa \u2713\uff09\u21d2 \u2605\u661f\u7a7a\u9762\u677f\u4e0e\u539f\u7248\u80cc\u666f\u540c\u5c42 \u2713
             * \u21d2 \u2605\u538b\u5728\u7269\u54c1\u4e0a\u9762 \u2713\uff1b\u2605\u800c\u6587\u5b57\u662f\u5728\u540c\u4e00\u5c42\u4e4b\u540e\u753b\u7684 \u2713
             * \u21d2 \u2605\u4f9d\u7136\u5728\u661f\u7a7a\u4e4b\u4e0a \u2713\u2713
             */
            com.mojang.blaze3d.vertex.PoseStack tnl$pose = event.getGraphics().pose();
            tnl$pose.pushPose();
            tnl$pose.translate(0.0F, 0.0F, 400.0F);
            StarfieldPainter.draw(event.getGraphics(), x - 3, y - 4, width + 6, height + 8);
            tnl$pose.popPose();
        } catch (Throwable ignored) {
        }
    }
}
