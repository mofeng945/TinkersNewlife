package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 「不可名状」的 <b>信号干扰 / 花屏</b>特效（纯客户端，不需要 shader）。
 *
 * <h2>思路</h2>
 * 原版渲染管线里想"真的把画面撕裂/错位"必须上后处理 shader（渲染目标 → 全屏 pass），
 * 成本高、还得管兼容。这里换成**全屏覆盖层**模仿老式显示器/信号不良的观感，靠三样东西：
 * <ol>
 *   <li><b>横向撕裂条</b>：随机高度、随机位置的半透明横条，并且左红右青错开 2px
 *       （假装色差），叠出"扫描线错位"的味道；</li>
 *   <li><b>噪点</b>：几十到一百多个小方块，明暗随机；</li>
 *   <li><b>滚动干扰带 + 闪屏</b>：一条不断向下滚的亮带，加上不定期的一两 tick 全屏闪白/闪暗。</li>
 * </ol>
 *
 * <h2>为什么要"每 N tick 重画一次图案"</h2>
 * 每帧都重新随机 → 看着是均匀的雪花噪点（像下雪），**不像**信号干扰。
 * 干扰的特征是"画面卡住几帧再跳一下"，所以图案每 {@link #PATTERN_TICKS} tick 才换一次，
 * 中间几帧保持同一张花屏、只让滚动带继续走 —— 观感立刻对了。
 *
 * <p>强度随效果等级放大（{@link #intensityOf}），配置项 {@code unnameable_glitch} /
 * {@code unnameable_glitch_intensity} 可整体关闭或调强弱。
 */
public final class UnnameableGlitchRenderer {

    /** 图案保持多少 tick 再换下一张（2 tick ≈ 每 0.1 秒跳一次） */
    private static final int PATTERN_TICKS = 2;

    /** 撕裂条配色（半透明，ARGB）：白 / 青 / 暗 / 紫白 */
    private static final int[] BAND_COLORS = {0x38FFFFFF, 0x2E9BE7FF, 0x33000000, 0x2AD8B4FF};

    private record Band(int y, int height, int color) {}

    private record Speck(int x, int y, int w, int h, int color) {}

    private static final Random RNG = new Random();
    private static final List<Band> BANDS = new ArrayList<>();
    private static final List<Speck> SPECKS = new ArrayList<>();

    private static int patternTick = Integer.MIN_VALUE;
    private static int patternWidth = -1;
    private static int patternHeight = -1;
    /** 整张图案的透明度倍率：每次换图案时随机取一个，做出"忽明忽暗"的闪烁 */
    private static float patternAlpha = 1.0F;

    /** 闪屏：持续到该 tick；再下一次闪屏的时刻 */
    private static int flashUntil = Integer.MIN_VALUE;
    private static int flashColor = 0x22FFFFFF;
    private static int nextFlashTick = 0;

    private UnnameableGlitchRenderer() {
    }

    /** 每帧调用（RenderGuiEvent.Post）。不在特效里 / 关掉了就什么都不画 */
    public static void render(GuiGraphics graphics, int width, int height, int tick, MobEffectInstance effect) {
        float intensity = intensityOf(effect);
        if (intensity <= 0.01F || width <= 0 || height <= 0) return;

        if (tick - patternTick >= PATTERN_TICKS || width != patternWidth || height != patternHeight) {
            rebuildPattern(width, height, tick, intensity);
        }

        // ---- 滚动干扰带：与图案刷新无关，持续向下滚，做出"信号不稳"的连续感 ----
        int barHeight = Math.max(24, height / 12);
        int travel = height + barHeight * 2;
        int barY = (int) ((tick * 6L) % travel) - barHeight;
        int barAlpha = (int) (0x1C * intensity);
        if (barAlpha > 0x60) barAlpha = 0x60;
        graphics.fill(0, barY, width, barY + barHeight, (barAlpha << 24) | 0xFFFFFF);
        // 带子下沿再压一条亮线，像回扫线
        graphics.fill(0, barY + barHeight - 1, width, barY + barHeight, (Math.min(0x50, barAlpha + 0x20) << 24) | 0xFFFFFF);

        // ---- 撕裂条（带色差：左红右青）----
        for (Band band : BANDS) {
            int rgb = band.color() & 0xFFFFFF;
            int a = (int) (((band.color() >>> 24) & 0xFF) * patternAlpha * intensity);
            if (a <= 2) continue;
            int layer = (a << 24) | rgb;
            graphics.fill(-2, band.y(), width - 2, band.y() + band.height(), (a / 3 << 24) | 0xFF3B3B);   // 红，左移
            graphics.fill(2, band.y(), width + 2, band.y() + band.height(), (a / 3 << 24) | 0x3BFFF0);    // 青，右移
            graphics.fill(0, band.y(), width, band.y() + band.height(), layer);
        }

        // ---- 噪点 ----
        for (Speck speck : SPECKS) {
            int a = (int) (((speck.color() >>> 24) & 0xFF) * patternAlpha);
            if (a <= 2) continue;
            graphics.fill(speck.x(), speck.y(), speck.x() + speck.w(), speck.y() + speck.h(),
                    (a << 24) | (speck.color() & 0xFFFFFF));
        }

        // ---- 闪屏：不定期来一下 ----
        if (tick >= nextFlashTick) {
            flashUntil = tick + 2 + RNG.nextInt(3);
            int roll = RNG.nextInt(10);
            flashColor = roll < 5 ? 0x26FFFFFF : roll < 8 ? 0x30000000 : 0x2A9BE7FF;
            nextFlashTick = tick + 18 + RNG.nextInt(70);
        }
        if (tick < flashUntil) {
            int a = (int) (((flashColor >>> 24) & 0xFF) * Math.min(1.4F, intensity));
            if (a > 0xFF) a = 0xFF;
            graphics.fill(0, 0, width, height, (a << 24) | (flashColor & 0xFFFFFF));
        }
    }

    /** 重新生成一张"花屏图案" */
    private static void rebuildPattern(int width, int height, int tick, float intensity) {
        patternTick = tick;
        patternWidth = width;
        patternHeight = height;
        // 忽明忽暗：多数时候正常，偶尔整张变淡 —— 这一条最像"信号断续"
        float[] weights = {1.0F, 1.0F, 1.0F, 0.55F, 0.25F};
        patternAlpha = weights[RNG.nextInt(weights.length)];

        BANDS.clear();
        int bandCount = Math.min(20, (int) ((3 + RNG.nextInt(6)) * intensity));
        for (int i = 0; i < bandCount; i++) {
            int y = RNG.nextInt(Math.max(1, height));
            int h = 2 + RNG.nextInt(14);
            BANDS.add(new Band(y, h, BAND_COLORS[RNG.nextInt(BAND_COLORS.length)]));
        }

        SPECKS.clear();
        int speckCount = Math.min(220, (int) ((40 + RNG.nextInt(70)) * intensity));
        for (int i = 0; i < speckCount; i++) {
            int w = 2 + RNG.nextInt(9);
            int h = 1 + RNG.nextInt(4);
            int x = RNG.nextInt(Math.max(1, width - w));
            int y = RNG.nextInt(Math.max(1, height - h));
            // 明暗各半：亮点像雪花，暗点像掉帧
            int alpha = 0x12 + RNG.nextInt(0x40);
            int color = RNG.nextBoolean() ? 0xFFFFFF : 0x000000;
            SPECKS.add(new Speck(x, y, w, h, (alpha << 24) | color));
        }
    }

    /** 强度 = (1 + 等级×0.4) × 配置倍率，夹在 0.2~3.0 */
    private static float intensityOf(MobEffectInstance effect) {
        if (effect == null) return 0F;
        float config = 1.0F;
        try {
            if (!ModConfig.UNNAMEABLE_GLITCH.get()) return 0F;
            config = (float) Math.max(0.0, Math.min(3.0, ModConfig.UNNAMEABLE_GLITCH_INTENSITY.get()));
        } catch (Throwable ignored) {
            // 配置还没加载（极端情况）→ 用默认强度
        }
        float value = (1.0F + effect.getAmplifier() * 0.4F) * config;
        return Math.max(0.0F, Math.min(3.0F, value));
    }
}
