package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.menu.PortableCraftingStationMenu;
import com.mofengbaizhi.tinkersnewlife.content.menu.PortableToolkitMenu;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * \u00a7823 \u2605\u5ba2\u6237\u7aef tick \u9a71\u52a8\u5668\uff1a\u8ba9\u4fbf\u643a\u8bbe\u5907\u7684\u914d\u65b9\u5339\u914d**\u6bcf tick \u90fd\u8dd1** \u2713\u3002
 *
 * <h2>\u4e3a\u4ec0\u4e48\u4e0d\u7528 broadcastChanges\uff08\u2605\u65e5\u5fd7\u8bc1\u636e \u2713\uff09</h2>
 * <pre>
 *   \u65e5\u5fd7\uff1a\u00a7821 / \u00a7822 \u63a2\u9488 **\u4e00\u6761\u90fd\u6ca1\u6709** \u2717
 *   \u21d2 \u2605{@code AbstractContainerMenu#broadcastChanges} \u662f**\u670d\u52a1\u7aef**\u7528\u6765\u540c\u6b65\u7684 \u2717
 *     \u5ba2\u6237\u7aef**\u4e0d\u4f1a\u8c03\u5b83** \u2717 \u21d2 \u6211\u4e0a\u4e00\u7248\u9009\u9519\u4e86\u5165\u53e3 \u2717
 * </pre>
 * \u2605\u6539\u6210\u7528 Forge \u7684\u5ba2\u6237\u7aef tick \u4e8b\u4ef6\uff08\u2605\u7eaf Forge \u2717 \u4e0d\u9760\u6df7\u5165\u3001\u4e0d\u9760 Mantle \u2713\uff09\u3002
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PortableRecipeClientTicker {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) {
                return;
            }
            if (mc.player.containerMenu instanceof PortableCraftingStationMenu menu) {
                menu.tnl$clientTick();
            } else if (mc.player.containerMenu instanceof PortableToolkitMenu menu) {
                menu.tnl$clientTick();
            }
        } catch (Throwable ignored) {
        }
    }
}
