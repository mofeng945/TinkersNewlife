package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * 墨默的**美术零件**：立绘 + 九宫格气泡 + 好感度→表情 的**唯一数据源** ✓。
 *
 * <p>为什么单独抽出来（用户：「让进入菜单界面也显示立绘，根据好感度变表情」✓）：
 * 对话界面和三选项菜单都要画同一张立绘、同一套气泡 ⇒ **尺寸/贴图/表情规则只能写一份** ✗
 * 否则两边尺寸一改就不同步（这种"两处各写一套"的坑本仓库已经踩过好几次 ✗）。</p>
 *
 * <p>立绘：六张 `textures/gui/momo/momo_*.png`，实测**全是 363×800** ✓；
 * 大小 = 屏高 × {@link #H_RATIO}，再受屏宽 × {@link #W_RATIO} 限制（窄窗口防挤压文字区 ✓）。</p>
 */
public final class MomoArt {

    private MomoArt() {}

    // ---- 表情下标（= PORTRAIT 数组下标 ✓）----
    public static final int EXPR_NORMAL = 0;
    public static final int EXPR_HAPPY = 1;
    public static final int EXPR_BLUSH = 2;
    public static final int EXPR_AWKWARD = 3;
    public static final int EXPR_SURPRISED = 4;
    public static final int EXPR_DISGUST = 5;

    private static final String[] PORTRAIT = {
            "momo_normal", "momo_happy", "momo_blush", "momo_awkward", "momo_surprised", "momo_disgust"
    };
    private static final int TEX_W = 363;
    private static final int TEX_H = 800;

    /**
     * 立绘显示区（**要调只改这几个数** ✓）
     * <p>用户口径：先「放大一点，至少占半个屏幕」+「气泡尖角对不上她的头」，再「立绘又太大了，大半身体和腿都看不到了」
     * ⇒ 最终折中：**可见源区 = 贴图上部 {@link #CROP_FRAC}（0.95 ≈ 全身，只切掉脚 ✓）**，
     * 高度拉到**屏高的 {@link #H_RATIO}（98%）**（比最早那版 62% 高了约 1.6 倍 ⇒ 既大又看得见腿 ✓），
     * 宽度上限屏宽 {@link #W_RATIO} ⇒ 854×480 下实测 **227×470** ✓。
     */
    private static final float CROP_FRAC = 0.95F;   // 可见源区 = 贴图上部 95%（≈全身，只切掉脚 ✓；腿在贴图 520~760 段 ✓）
    private static final float H_RATIO = 0.98F;     // 可见高度 = 屏高 × 0.98
    private static final float W_RATIO = 0.50F;     // 宽度上限 = 屏宽 × 0.50（≈半个屏幕 ✓）

    /** **脸中心**在贴图里的纵向位置（六张实测都是 363×800、脸约在 y=165 ✓）⇒ 用来算尖角该指哪 ✓ */
    private static final float FACE_IN_TEX = 165.0F / TEX_H;

    /** 好感档位（与对话树的解锁档一致 ✓） */
    public static final int[] TIER = { 0, 10, 20, 30, 40, 50 };

    /**
     * **没在说具体某句时**露哪张脸（菜单界面 / 对话界面选项页的默认脸 ✓）。
     * 只放"寒暄该有的脸"：平静 / 开心 / 脸红 ✓
     * —— 尴尬 / 惊讶 / 嫌恶 只留给具体回答（用户口径：问候不该是惊讶 ✗ §483）。
     */
    private static final int[] GREET_EXPR = { EXPR_NORMAL, EXPR_NORMAL, EXPR_HAPPY, EXPR_BLUSH, EXPR_HAPPY, EXPR_HAPPY };

    // ---- 九宫格 ----
    private static final int NINE = 19;      // 贴图边长
    private static final int RADIUS = 9;     // 圆角半径（= 四角切片大小 ✓）
    public static final int PAD = 9;         // 气泡内边距

    public static final ResourceLocation BUBBLE =
            new ResourceLocation(TinkersNewlife.MOD_ID, "textures/gui/momo/bubble_momo.png");
    public static final ResourceLocation OPTION =
            new ResourceLocation(TinkersNewlife.MOD_ID, "textures/gui/momo/bubble_option.png");

    /**
     * 好感度 ⇒ 默认表情（两个界面共用 ✓ 保证菜单和对话里"同一好感同一张脸" ✓）。
     * <b>负好感一律嫌恶</b> ✓（用户口径：「负数好感度时应当是嫌恶表情」——那种关系下她不该给你好脸 ✗）。
     */
    public static int exprForFavor(int favor) {
        if (favor < 0) return EXPR_DISGUST;
        int best = 0;
        for (int t = 0; t < TIER.length; t++) if (favor >= TIER[t]) best = t;
        return GREET_EXPR[Math.max(0, Math.min(GREET_EXPR.length - 1, best))];
    }

    /** 可见源区高度（贴图像素）= 贴图高 × {@link #CROP_FRAC} ✓ */
    private static int cropH() {
        return Math.round(TEX_H * CROP_FRAC);
    }

    /** 立绘尺寸 {宽, 高} ✓（按**可见源区**的宽高比缩放 ✓） */
    public static int[] portraitSize(int screenW, int screenH) {
        int ch = cropH();
        int h = (int) (screenH * H_RATIO);
        int w = Math.max(1, Math.round(TEX_W * (h / (float) ch)));
        int cap = (int) (screenW * W_RATIO);
        if (w > cap) {
            w = Math.max(1, cap);
            h = Math.max(1, Math.round(ch * (w / (float) TEX_W)));
        }
        return new int[] { w, h };
    }

    /** **她的脸在屏幕上的 Y**（气泡尖角按这个对准她的头 ✓） */
    public static int faceY(int screenW, int screenH) {
        int[] s = portraitSize(screenW, screenH);
        float frac = (TEX_H * FACE_IN_TEX) / cropH();     // 脸在可见区里的比例
        return (screenH - s[1]) + Math.round(s[1] * frac);
    }

    /** 立绘占的宽度（给文字区让位用 ✓） */
    public static int portraitWidth(int screenW, int screenH) {
        return portraitSize(screenW, screenH)[0];
    }

    /** 画立绘：**右侧、底部对齐** ✓ **只画上部 {@link #CROP_FRAC} 那段并放大**（半身 ✓）；贴图缺失静默降级 ✓ */
    public static void portrait(GuiGraphics g, int expr, int screenW, int screenH) {
        int[] s = portraitSize(screenW, screenH);
        int idx = Math.max(0, Math.min(PORTRAIT.length - 1, expr));
        try {
            ResourceLocation rl = new ResourceLocation(TinkersNewlife.MOD_ID,
                    "textures/gui/momo/" + PORTRAIT[idx] + ".png");
            // 11 参 blit：源矩形 = 贴图上部的半身段（0,0,363,cropH），目标 = 放大后的 s[0]×s[1] ✓
            g.blit(rl, screenW - s[0] - 16, screenH - s[1], s[0], s[1],
                    0F, 0F, TEX_W, cropH(), TEX_W, TEX_H);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 九宫格拼装（19×19、半径 9、中间 1 行/列拉伸 ✓）。
     *
     * <p><b>必须用 11 参 `blit`</b>：`(rl, x, y, 宽, 高, u, v, 源宽, 源高, 贴图宽, 贴图高)`
     * —— 源尺寸与目标尺寸**解耦** ✓。9 参那个内部把 `uWidth = 目标宽`（1:1）⇒ 拿它拉伸会取样越界、
     * 被钳成一条边 ⇒ 画出一排圆点（踩过 ✗ §479）。</p>
     */
    public static void nine(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h) {
        int r = RADIUS, t = NINE;
        w = Math.max(w, r * 2 + 2);
        h = Math.max(h, r * 2 + 2);
        int mw = w - r * 2, mh = h - r * 2;
        // 四角 1:1
        g.blit(tex, x, y, r, r, 0F, 0F, r, r, t, t);
        g.blit(tex, x + w - r, y, r, r, (float) (t - r), 0F, r, r, t, t);
        g.blit(tex, x, y + h - r, r, r, 0F, (float) (t - r), r, r, t, t);
        g.blit(tex, x + w - r, y + h - r, r, r, (float) (t - r), (float) (t - r), r, r, t, t);
        // 四边：取中间那 1 像素拉伸
        g.blit(tex, x + r, y, mw, r, (float) r, 0F, 1, r, t, t);
        g.blit(tex, x + r, y + h - r, mw, r, (float) r, (float) (t - r), 1, r, t, t);
        g.blit(tex, x, y + r, r, mh, 0F, (float) r, r, 1, t, t);
        g.blit(tex, x + w - r, y + r, r, mh, (float) (t - r), (float) r, r, 1, t, t);
        // 中心 1×1 拉伸
        g.blit(tex, x + r, y + r, mw, mh, (float) r, (float) r, 1, 1, t, t);
    }

    /** 尖角（指向立绘的纯色小三角 ✓ 颜色跟气泡一致 ✓） */
    public static void tail(GuiGraphics g, int x, int tailY, int color) {
        for (int i = 0; i < 8; i++) {
            g.fill(x + i, tailY - (8 - i) / 2, x + i + 1, tailY + (8 - i) / 2 + 1, color);
        }
    }

    // ============================================================
    //  统一文字/配色（对话·交易·雇佣·菜单 **四个界面共用** ✓ §497 用户口径"都改成相同风格"）
    // ============================================================

    /** 字号缩放（**不加粗、不打阴影** ✓ §495/§496 用户口径） */
    public static final float TEXT_SCALE = 1.25F;
    public static final int LINE_H = Math.round(10 * TEXT_SCALE);

    public static final int TEXT_DARK = 0x202020;
    public static final int MOMO_TEXT = 0x2B2118;      // 她的话：暖色
    public static final int PLAYER_TEXT = 0x1C2430;    // 你的话：冷色
    public static final int HINT_TEXT = 0xDDDDDD;
    public static final int MOMO_TINT = 0x1CFFD9A0;
    public static final int PLAYER_TINT = 0x2E8FB8E8;
    public static final int HOVER_TINT = 0x33FFFFFF;
    public static final int DISABLED_TINT = 0x55202020;
    public static final int DISABLED_TEXT = 0xFF6A6A6A;
    public static final int BUBBLE_FILL = 0xFFF7F3E7;

    /** 统一压暗背景 ✓ */
    public static void dim(GuiGraphics g, int screenW, int screenH) {
        g.fill(0, 0, screenW, screenH, 0x99000000);
    }

    /** 统一画文字（缩放、不阴影、不删边 ✓） */
    public static void text(GuiGraphics g, Font font, String s, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().scale(TEXT_SCALE, TEXT_SCALE, 1F);
        g.drawString(font, s, Math.round(x / TEXT_SCALE), Math.round(y / TEXT_SCALE), color, false);
        g.pose().popPose();
    }

    public static void text(GuiGraphics g, Font font, FormattedCharSequence seq, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().scale(TEXT_SCALE, TEXT_SCALE, 1F);
        g.drawString(font, seq, Math.round(x / TEXT_SCALE), Math.round(y / TEXT_SCALE), color, false);
        g.pose().popPose();
    }

    /** 屏上实际字宽（含缩放 ✓ 用来居中/自适应气泡 ✓） */
    public static int textWidth(Font font, String s) {
        return Math.round(font.width(s) * TEXT_SCALE);
    }

    /** 按缩放反算的换行 ✓ */
    public static List<FormattedCharSequence> wrap(Font font, String s, int maxScreenW) {
        return font.split(Component.literal(s), Math.max(40, (int) (maxScreenW / TEXT_SCALE)));
    }

    /** 立绘左边留给 UI 的宽度（四个界面统一口径 ✓） */
    public static int panelWidth(int screenW, int screenH) {
        return Math.max(200, screenW - portraitWidth(screenW, screenH) - 48);
    }

    /** 气泡宽度上限 = 空白区 2/3 ✓（用户口径 §492） */
    public static int bubbleMaxW(int panelW) {
        return Math.max(150, panelW * 2 / 3);
    }

    /**
     * 画一段**她说的话**的气泡（右对齐到 {@code rightX} ✓ §501 交易/雇佣界面开场白用）。
     *
     * @return {左缘X, 顶Y, 宽, 高} —— 尖角按它对齐 ✓
     */
    public static int[] sayRight(GuiGraphics g, Font font, int rightX, int y, String text, int maxW) {
        List<FormattedCharSequence> lines = wrap(font, text, Math.max(60, maxW - PAD * 2));
        int w = Math.min(maxW, maxLineWidth(font, lines) + PAD * 2);
        int h = lines.size() * LINE_H + PAD * 2;
        int x = rightX - w;
        nine(g, BUBBLE, x, y, w, h);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, MOMO_TINT);
        int ly = y + PAD;
        for (FormattedCharSequence line : lines) {
            text(g, font, line, x + PAD, ly, MOMO_TEXT);
            ly += LINE_H;
        }
        return new int[] { x, y, w, h };
    }

    /** 这段话说出来会占多高（用来把它摆在面板上方 ✓） */
    public static int sayHeight(Font font, String text, int maxW) {
        return wrap(font, text, Math.max(60, maxW - PAD * 2)).size() * LINE_H + PAD * 2;
    }

    /** 换行后最宽那行的屏上宽度 ✓ */
    public static int maxLineWidth(Font font, List<FormattedCharSequence> lines) {
        int max = 0;
        for (FormattedCharSequence l : lines) {
            max = Math.max(max, font.width(l));
        }
        return Math.round(max * TEXT_SCALE);
    }
}
