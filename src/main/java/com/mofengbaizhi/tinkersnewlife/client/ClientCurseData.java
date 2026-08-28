package com.mofengbaizhi.tinkersnewlife.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 客户端咒力状态缓存 + HUD 渲染（左上角）
 * <p>
 * 数据由服务端 PacketSyncCurse 每秒推送；佩戴咒力核心时显示咒力数值，
 * 领域展开时显示领域状态与咒力无限标记。
 */
public class ClientCurseData {

    private static double curse;
    private static double max;
    private static boolean domainActive;
    private static boolean infinite;

    public static void update(double curseIn, double maxIn, boolean domainActiveIn, boolean infiniteIn) {
        curse = curseIn;
        max = maxIn;
        domainActive = domainActiveIn;
        infinite = infiniteIn;
    }

    /** Forge GUI Overlay 渲染入口（registerAboveAll） */
    public static void render(Gui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        if (max <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Font font = mc.font;

        int x = 4;
        int y = 4;

        // 咒力：X / Y
        Component curseLine = Component.translatable("hud.tinkersnewlife.curse",
                (int) Math.floor(curse), (int) Math.ceil(max));
        graphics.drawString(font, curseLine, x, y, infinite ? 0xFFD700 : 0xFFFFFF);

        // 领域状态
        if (domainActive) {
            graphics.drawString(font, Component.translatable("hud.tinkersnewlife.domain_active"),
                    x, y + 10, 0xFFFF55);
        }

        // 咒力无限
        if (infinite) {
            graphics.drawString(font, Component.translatable("hud.tinkersnewlife.infinite"),
                    x, y + 20, 0xFFFFAA);
        }
    }
}
