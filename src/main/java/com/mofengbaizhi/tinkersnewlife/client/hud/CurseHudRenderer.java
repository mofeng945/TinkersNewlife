package com.mofengbaizhi.tinkersnewlife.client.hud;

import com.mofengbaizhi.tinkersnewlife.client.data.ClientCurseData;
import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 咒术 HUD：<b>咒力进度条</b>（当前 / 上限），位置与宽度可在游戏内拖动调整。
 *
 * <p>布局（以配置的左上角为原点）：
 * <pre>
 *   ┌──────────────────────────────┐
 *   │████████████░░░░░░░░░░░░░░░░░░│  ← 进度条（咒力/max），数值居中显示
 *   └──────────────────────────────┘
 *    当前术式名                      ← 有选中术式时
 *    领域展开中 / 咒力无限            ← 状态行
 * </pre>
 * 咒力无限时进度条走金色满格并缓慢呼吸；领域展开时条框描边变亮。
 */
public final class CurseHudRenderer {

    /** 进度条高度 */
    public static final int BAR_H = 9;
    /** 数值文字与状态行的高度（拖动命中判断用） */
    public static final int TEXT_H = 11;

    private CurseHudRenderer() {}

    /** 位置：优先用编辑界面里的实时值，否则用配置值 */
    public static int baseX() {
        var screen = Minecraft.getInstance().screen;
        if (screen instanceof com.mofengbaizhi.tinkersnewlife.client.screen.CurseHudEditScreen edit) {
            return edit.getEditX();
        }
        return CurseHudConfig.getX();
    }

    public static int baseY() {
        var screen = Minecraft.getInstance().screen;
        if (screen instanceof com.mofengbaizhi.tinkersnewlife.client.screen.CurseHudEditScreen edit) {
            return edit.getEditY();
        }
        return CurseHudConfig.getY();
    }

    public static int barWidth() {
        var screen = Minecraft.getInstance().screen;
        if (screen instanceof com.mofengbaizhi.tinkersnewlife.client.screen.CurseHudEditScreen edit) {
            return edit.getEditWidth();
        }
        return CurseHudConfig.getWidth();
    }

    /** HUD 整体高度（拖动框大小） */
    public static int totalHeight() {
        int h = BAR_H;
        if (!ClientCurseData.getTechniqueId().isEmpty()) h += TEXT_H;
        if (ClientCurseData.isDomainActive() || ClientCurseData.isInfinite()) h += TEXT_H;
        return h;
    }

    /** Forge GUI Overlay 入口（registerAboveAll） */
    public static void render(Gui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        if (!CurseHudConfig.isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (ClientCurseData.getMax() <= 0) return;   // 没戴咒力核心：不显示

        Font font = mc.font;
        int x = baseX();
        int y = baseY();
        int w = barWidth();

        drawBar(graphics, font, x, y, w);
        y += BAR_H;

        String technique = ClientCurseData.getTechniqueId();
        if (!technique.isEmpty()) {
            Component techniqueName = Component.translatable("modifier." + technique.replace(':', '.'));
            graphics.drawString(font, Component.translatable("hud.tinkersnewlife.technique", techniqueName),
                    x, y, 0x55FFFF);
            y += TEXT_H;
        }
        if (ClientCurseData.isDomainActive()) {
            graphics.drawString(font, Component.translatable("hud.tinkersnewlife.domain_active"), x, y, 0xFFFF55);
            y += TEXT_H;
        }
        if (ClientCurseData.isInfinite()) {
            graphics.drawString(font, Component.translatable("hud.tinkersnewlife.infinite"), x, y, 0xFFFFAA);
        }
    }

    /**
     * 只画那一条咒力进度条（HUD 与编辑界面共用）。
     *
     * @param editing 编辑模式：额外画一圈虚线提示框
     */
    public static void drawBar(GuiGraphics graphics, Font font, int x, int y, int width) {
        double cur = Math.max(0, ClientCurseData.getCurse());
        double max = Math.max(1, ClientCurseData.getMax());
        float pct = (float) Mth.clamp(cur / max, 0.0, 1.0);
        boolean infinite = ClientCurseData.isInfinite();
        if (infinite) pct = 1.0f;

        // 外框 + 底
        graphics.fill(x - 1, y - 1, x + width + 1, y + BAR_H + 1, 0xE0101014);
        graphics.fill(x, y, x + width, y + BAR_H, 0xFF1B1B22);
        // 内层高光边
        graphics.fill(x, y, x + width, y + 1, 0x33FFFFFF);

        // 填充：咒力紫蓝渐变；咒力无限走金色
        int fillW = pct <= 0 ? 0 : Math.max(1, (int) (width * pct));
        if (fillW > 0) {
            int left, right;
            if (infinite) {
                left = 0xFFFFD24A;
                right = 0xFFFFF3B0;
            } else if (pct < 0.25f) {
                left = 0xFF7A2BD6;      // 快见底：偏红紫
                right = 0xFFC2457A;
            } else {
                left = 0xFF3F6BE0;      // 常态：蓝 → 紫
                right = 0xFF9A4BE0;
            }
            // 简易渐变：按 16 段横向铺色
            int steps = 16;
            for (int i = 0; i < steps; i++) {
                int sx = x + fillW * i / steps;
                int ex = x + fillW * (i + 1) / steps;
                if (ex <= sx) continue;
                float t = (i + 0.5f) / steps;
                int color = lerpColor(left, right, t);
                graphics.fill(sx, y + 1, ex, y + BAR_H - 1, color);
            }
            // 顶部高光，让条看起来有厚度
            graphics.fill(x, y + 1, x + fillW, y + 3, 0x40FFFFFF);
        }

        // 计数文字（居中，带阴影）
        Component text = infinite
                ? Component.translatable("hud.tinkersnewlife.curse.infinite_value")
                : Component.translatable("hud.tinkersnewlife.curse",
                (int) Math.floor(cur), (int) Math.ceil(max));
        int tw = font.width(text);
        int ty = y + (BAR_H - 8) / 2 + 1;
        graphics.drawString(font, text, x + (width - tw) / 2, ty, 0xFFFFFFFF, true);

        // 领域展开：描边变亮
        if (ClientCurseData.isDomainActive()) {
            graphics.fill(x - 1, y - 1, x + width + 1, y, 0xFFFFFF55);
            graphics.fill(x - 1, y + BAR_H, x + width + 1, y + BAR_H + 1, 0xFFFFFF55);
            graphics.fill(x - 1, y - 1, x, y + BAR_H + 1, 0xFFFFFF55);
            graphics.fill(x + width, y - 1, x + width + 1, y + BAR_H + 1, 0xFFFFFF55);
        }
    }

    /** 编辑界面预览用：进度条下方的术式/领域/无限状态行（与实际 HUD 排版一致） */
    public static void drawPreviewExtraLines(GuiGraphics graphics, Font font, int x, int y) {
        int lineY = y + BAR_H;
        String technique = ClientCurseData.getTechniqueId();
        if (!technique.isEmpty()) {
            Component techniqueName = Component.translatable("modifier." + technique.replace(':', '.'));
            graphics.drawString(font, Component.translatable("hud.tinkersnewlife.technique", techniqueName),
                    x, lineY, 0x55FFFF);
            lineY += TEXT_H;
        }
        if (ClientCurseData.isDomainActive()) {
            graphics.drawString(font, Component.translatable("hud.tinkersnewlife.domain_active"), x, lineY, 0xFFFF55);
            lineY += TEXT_H;
        }
        if (ClientCurseData.isInfinite()) {
            graphics.drawString(font, Component.translatable("hud.tinkersnewlife.infinite"), x, lineY, 0xFFFFAA);
        }
    }

    /** 编辑模式下的提示框（虚线感：分段画） */
    public static void drawEditFrame(GuiGraphics graphics, int x, int y, int width, int height) {
        int color = 0xFF00E5FF;
        int dash = 6;
        for (int i = 0; i < width; i += dash * 2) {
            int ex = Math.min(x + width, x + i + dash);
            graphics.fill(x + i, y - 3, ex, y - 2, color);
            graphics.fill(x + i, y + height + 2, ex, y + height + 3, color);
        }
        for (int i = 0; i < height; i += dash * 2) {
            int ey = Math.min(y + height, y + i + dash);
            graphics.fill(x - 3, y + i, x - 2, ey, color);
            graphics.fill(x + width + 2, y + i, x + width + 3, ey, color);
        }
    }

    private static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF, aa = (a >>> 24) & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF, ba = (b >>> 24) & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        int al = (int) (aa + (ba - aa) * t);
        return (al << 24) | (r << 16) | (g << 8) | bl;
    }
}
