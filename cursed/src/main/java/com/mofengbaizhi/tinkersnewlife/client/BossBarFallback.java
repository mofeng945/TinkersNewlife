package com.mofengbaizhi.tinkersnewlife.client;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.BossBarRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * TNL987 ★血条兜底（★客户端侧 ✓）：
 * ★只要客户端真的画了一条血条 ✓
 * ★就记下它的 UUID ✓（★与服务端那条互为双保险 ✓）。
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
