package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

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

    /** 立绘大小（**要调只改这两个数** ✓）：屏高比例 + 屏宽上限 */
    private static final float H_RATIO = 0.62F;
    private static final float W_RATIO = 0.28F;

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

    /** 好感度 ⇒ 默认表情（两个界面共用 ✓ 保证菜单和对话里"同一好感同一张脸" ✓） */
    public static int exprForFavor(int favor) {
        int best = 0;
        for (int t = 0; t < TIER.length; t++) if (favor >= TIER[t]) best = t;
        return GREET_EXPR[Math.max(0, Math.min(GREET_EXPR.length - 1, best))];
    }

    /** 立绘尺寸 {宽, 高} ✓ */
    public static int[] portraitSize(int screenW, int screenH) {
        int h = (int) (screenH * H_RATIO);
        int w = Math.max(1, Math.round(TEX_W * (h / (float) TEX_H)));
        int cap = (int) (screenW * W_RATIO);
        if (w > cap) {
            w = Math.max(1, cap);
            h = Math.max(1, Math.round(TEX_H * (w / (float) TEX_W)));
        }
        return new int[] { w, h };
    }

    /** 立绘占的宽度（给文字区让位用 ✓） */
    public static int portraitWidth(int screenW, int screenH) {
        return portraitSize(screenW, screenH)[0];
    }

    /** 画立绘：**右侧、底部对齐** ✓ 贴图缺失静默降级（不让界面崩 ✓） */
    public static void portrait(GuiGraphics g, int expr, int screenW, int screenH) {
        int[] s = portraitSize(screenW, screenH);
        int idx = Math.max(0, Math.min(PORTRAIT.length - 1, expr));
        try {
            ResourceLocation rl = new ResourceLocation(TinkersNewlife.MOD_ID,
                    "textures/gui/momo/" + PORTRAIT[idx] + ".png");
            // 11 参 blit：源 = 整张贴图（源尺寸与目标尺寸解耦 ✓ 9 参那个是 1:1 取样，会失真 ✗）
            g.blit(rl, screenW - s[0] - 16, screenH - s[1], s[0], s[1], 0F, 0F, TEX_W, TEX_H, TEX_W, TEX_H);
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
}
