package com.mofengbaizhi.tinkersnewlife.client.data;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 客户端咒力状态缓存 + HUD 渲染（左上角）
 * <p>
 * 数据由服务端 PacketSyncCurse 每秒推送；佩戴咒力核心时显示咒力数值与当前选中的术式，
 * 领域展开时显示领域状态与咒力无限标记。
 */
public class ClientCurseData {

    private static double curse;
    private static double max;
    private static boolean domainActive;
    private static boolean infinite;
    /** 当前选中的术式 id（如 tinkersnewlife:kai），无术式时为 "" */
    private static String techniqueId = "";
    /** 已调伏式神位掩码（位 = ShikigamiType.ordinal()） */
    private static int tamedMask = 0;
    /** 咒力亲和（召唤消耗计算用） */
    private static int affinity = 0;
    /** 咒力输出等级（召唤消耗计算用） */
    private static int output = 1;
    /** 天与咒缚·暴君标识（服务端同步） */
    private static boolean restricted = false;
    /** 天与咒缚·咒力标识（服务端同步） */
    private static boolean bound = false;

    public static void update(double curseIn, double maxIn, boolean domainActiveIn, boolean infiniteIn,
                              String techniqueIdIn, int tamedMaskIn, int affinityIn, int outputIn,
                              boolean restrictedIn, boolean boundIn) {
        curse = curseIn;
        max = maxIn;
        domainActive = domainActiveIn;
        infinite = infiniteIn;
        techniqueId = techniqueIdIn == null ? "" : techniqueIdIn;
        tamedMask = tamedMaskIn;
        affinity = affinityIn;
        output = Math.max(1, outputIn);
        restricted = restrictedIn;
        bound = boundIn;
    }

    /** 天与咒缚·暴君是否生效（服务端同步） */
    public static boolean isRestricted() {
        return restricted;
    }

    /** 天与咒缚·咒力是否生效（服务端同步） */
    public static boolean isBound() {
        return bound;
    }

    /** 当前咒力（服务端同步，未佩戴时为 0） */
    public static double getCurse() {
        return curse;
    }

    /** 当前咒力上限（服务端同步，未佩戴时为 0） */
    public static double getMax() {
        return max;
    }

    /** 领域是否展开中 */
    public static boolean isDomainActive() {
        return domainActive;
    }

    /** 是否咒力无限 */
    public static boolean isInfinite() {
        return infinite;
    }

    /** 该式神是否已调伏（客户端镜像，玉犬永远可用） */
    public static boolean isShikigamiTamed(com.mofengbaizhi.tinkersnewlife.content.curse.shikigami.ShikigamiType type) {
        if (type == com.mofengbaizhi.tinkersnewlife.content.curse.shikigami.ShikigamiType.DOG) return true;
        return (tamedMask & (1 << type.ordinal())) != 0;
    }

    /** 咒力亲和（服务端同步） */
    public static int getAffinity() {
        return affinity;
    }

    /** 咒力输出等级（服务端同步） */
    public static int getOutput() {
        return output;
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

        // 当前选中的术式（随切换键循环）
        if (!techniqueId.isEmpty()) {
            Component techniqueName = Component.translatable("modifier." + techniqueId.replace(':', '.'));
            graphics.drawString(font, Component.translatable("hud.tinkersnewlife.technique", techniqueName),
                    x, y + 10, 0x55FFFF);
        }

        // 领域状态
        if (domainActive) {
            graphics.drawString(font, Component.translatable("hud.tinkersnewlife.domain_active"),
                    x, y + 20, 0xFFFF55);
        }

        // 咒力无限
        if (infinite) {
            graphics.drawString(font, Component.translatable("hud.tinkersnewlife.infinite"),
                    x, y + 30, 0xFFFFAA);
        }
    }
}
