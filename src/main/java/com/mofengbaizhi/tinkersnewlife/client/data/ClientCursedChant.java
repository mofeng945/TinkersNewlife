package com.mofengbaizhi.tinkersnewlife.client.data;

import com.mofengbaizhi.tinkersnewlife.client.hud.ChannelBarRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 咒言咏唱读条缓存 + HUD 渲染（复用通用 {@link ChannelBarRenderer}）。
 */
public class ClientCursedChant {

    private static long total = 0;
    private static long remaining = 0;
    private static long lastSyncMs = 0;
    private static String label = "";

    public static void update(long remainingIn, long totalIn, String labelIn) {
        if (totalIn <= 0) {
            total = 0;
            remaining = 0;
            label = "";
            return;
        }
        total = totalIn;
        remaining = Math.max(0, remainingIn);
        label = labelIn == null ? "" : labelIn;
        lastSyncMs = System.currentTimeMillis();
    }

    private static long remainingTicks() {
        if (total <= 0) return 0;
        long elapsedMs = System.currentTimeMillis() - lastSyncMs;
        return Math.max(0, remaining - elapsedMs / 50L);
    }

    public static boolean isChanting() {
        return total > 0 && remainingTicks() > 0;
    }

    /** Forge GUI Overlay 渲染入口（registerAboveAll） */
    public static void render(Gui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || total <= 0) return;
        long left = remainingTicks();
        if (left <= 0) return;
        Component title = Component.translatable("hud.tinkersnewlife.chant", label);
        ChannelBarRenderer.render(graphics, title, left, total);
    }
}
