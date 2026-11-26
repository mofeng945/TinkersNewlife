package com.mofengbaizhi.tinkersnewlife.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 通用 HUD 读条渲染（屏幕中央偏上）。
 * 供咒言咏唱、构筑拟造等耗时施法共用：标题 + 深底浅金进度条 + 剩余秒数。
 */
public final class ChannelBarRenderer {

    private ChannelBarRenderer() {}

    /**
     * @param label        读条标题（完整文案，例如「咏唱咒言：…」）
     * @param leftTicks    剩余 tick
     * @param totalTicks   总 tick（>0）
     */
    public static void render(GuiGraphics graphics, Component label, long leftTicks, long totalTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || totalTicks <= 0 || leftTicks <= 0) return;
        Font font = mc.font;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        int barW = 170;
        int barH = 7;
        int cx = screenW / 2;
        int barX = cx - barW / 2;
        int barY = screenH / 2 - 40;

        graphics.drawString(font, label, cx - font.width(label) / 2, barY - 13, 0xFFD4924B);

        graphics.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xFF000000);
        graphics.fill(barX, barY, barX + barW, barY + barH, 0x66000000);
        double progress = 1.0 - (double) leftTicks / (double) totalTicks;
        progress = Math.max(0.0, Math.min(1.0, progress));
        int fill = (int) Math.round(barW * progress);
        if (fill > 0) {
            graphics.fill(barX, barY, barX + fill, barY + barH, 0xFFE8B84B);
        }
        Component remain = Component.literal("§f" + Math.max(1, (leftTicks + 19) / 20) + "s");
        graphics.drawString(font, remain, cx + barW / 2 + 7, barY - 1, 0xFFFFFF);
    }
}
