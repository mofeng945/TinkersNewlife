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

    /**
     * TNL898 \u2605\u2605 \u53ea\u7f6e\u65d7\u6807 \u2713 \u2014\u2014 \u2605\u661f\u7a7a\u7531\u6df7\u5165\u7528**\u539f\u7248\u7b97\u597d\u7684\u6846**\u753b \u2713
     *
     * <p>\u2605\u4e4b\u524d\u81ea\u5df1\u7b97\u884c\u6570/\u5b57\u5bbd\u7684\u5199\u6cd5\uff08\u2605\u53d7\u201c\u9ad8\u7ea7\u63d0\u793a\u201d\u5f00\u5173\u5f71\u54cd \u2717\uff09\u5df2\u5e9f\u5f03 \u2713\u3002
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPre(RenderTooltipEvent.Pre event) {
        try {
            StarfieldState.active = mine(event.getItemStack());
        } catch (Throwable ignored) {
            StarfieldState.active = false;
        }
    }
}
