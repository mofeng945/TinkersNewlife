package com.mofengbaizhi.tinkersnewlife.client.portal;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * TNL993 \u2605\u2605 \u767d\u8272\u7a7a\u95f4\u4f20\u9001\u95e8\uff1a\u2605"\u76f4\u63a5\u6e32\u67d3\u5bf9\u9762\u4e16\u754c"\u7b2c \u2462 \u6b65 \u2713
 *
 * <p>\u2605\u672c\u6b65\u5148\u628a**\u4e0a\u4e0b\u6587\u94fe\u8def\u5168\u90e8\u63a5\u901a\u5e76\u9a8c\u8bc1** \u2713\uff0c
 * \u2605\u4f46\u2605**\u6682\u4e0d\u9012\u5f52\u8c03\u7528 `LevelRenderer#renderLevel`** \u2717 \u2014\u2014
 * \u2605\u90a3\u4e2a\u65b9\u6cd5\u5728 1.20.1 \u6709 **8 \u4e2a\u53c2\u6570** \u2717\uff0c\u2605\u800c\u4e14\u6e32\u67d3\u524d\u540e\u8981\u6446\u5f88\u591a
 * `RenderSystem` \u72b6\u6001 \u2713 \u21d2 \u2605\u5148\u628a"\u7ed1 FBO / \u6e05\u5c4f / \u8fd8\u539f"\u8dd1\u901a \u2713\uff0c
 * \u2605\u4e0b\u4e00\u6b65\u518d\u628a\u90a3\u4e00\u53e5\u52a0\u4e0a\u53bb \u2713\u3002
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT)
public final class PortalViewRenderer {

    private static long lastLog;

    private PortalViewRenderer() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            PortalViewLevels.remember(Minecraft.getInstance().level);
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (PortalCameraSwap.isActive()) {
            return;                       // \u2605\u9632\u9012\u5f52 \u2713
        }
        Minecraft mc = Minecraft.getInstance();
        try {
            RenderTarget rt = PortalViewTarget.get();
            // \u2605\u2460 \u7ed1\u526f\u7f13\u51b2 \u002b \u6e05\u5c4f \u2713
            rt.bindWrite(true);
            rt.clear(Minecraft.ON_OSX);
            // \u2605\u2461 \u56de\u5230\u4e3b\u7f13\u51b2 \u2713
            mc.getMainRenderTarget().bindWrite(true);
            if (System.currentTimeMillis() - lastLog > 5000L) {
                lastLog = System.currentTimeMillis();
                TinkersNewlife.LOGGER.warn("TNL993 portal view stage ok: target={}x{} tex={} knownLevels={}",
                        rt.width, rt.height, PortalViewTarget.textureId(),
                        PortalViewLevels.has(mc.level == null ? null : mc.level.dimension()));
            }
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("TNL993 portal view stage FAILED: {}", t.toString());
        }
    }
}
