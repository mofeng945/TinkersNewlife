package com.mofengbaizhi.tinkersnewlife.client;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.BossBarRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * TNL987 \u2605\u8840\u6761\u515c\u5e95\uff08\u2605\u5ba2\u6237\u7aef\u4fa7 \u2713\uff09\uff1a
 * \u2605\u53ea\u8981\u5ba2\u6237\u7aef\u771f\u7684\u753b\u4e86\u4e00\u6761\u8840\u6761 \u2713
 * \u2605\u5c31\u8bb0\u4e0b\u5b83\u7684 UUID \u2713\uff08\u2605\u4e0e\u670d\u52a1\u7aef\u90a3\u6761\u4e92\u4e3a\u53cc\u4fdd\u9669 \u2713\uff09\u3002
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT)
public final class BossBarFallback {

    private BossBarFallback() {
    }

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        try {
            BossBarRegistry.mark(event.getBossEvent().getId());
        } catch (Throwable ignored) {
        }
    }
}
