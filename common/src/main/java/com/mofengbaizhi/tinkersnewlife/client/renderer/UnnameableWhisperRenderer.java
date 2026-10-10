package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Random;

/**
 * 「不可名状」的<b>低语文字</b>：屏幕随机位置闪现一句随机的话，闪烁、时隐时现。
 *
 * <h2>节奏：位置"停"一小会儿，只让透明度闪</h2>
 * 参考实现的写法是<b>每帧</b>重掷位置和内容，实机里字会疯狂跳位，根本读不出来；
 * 于是改成两层分离：
 * <ul>
 *   <li><b>位置与内容每 {@link #HOLD_MIN}~{@link #HOLD_MAX} tick 才换一次</b>（0.5~0.9 秒）——
 *       一句话会在原地停住足够久，眼睛来得及读；</li>
 *   <li><b>每帧只掷"这一帧画不画 + 多亮"</b>（见 {@link #SKIP_PER_FRAME}）——
 *       位置不动，但明暗一直在抖、还偶尔整帧消失，所以仍然是"时隐时现的耳语"，
 *       而不是一行老老实实的 HUD 文字。</li>
 * </ul>
 * 简单说：<b>闪的是亮度，不是位置</b>。
 *
 * <h2>其它</h2>
 * <ul>
 *   <li>条数 = {@code 1 + round(强度×2)}（最多 {@link #SLOTS} 条），强度越高同屏越多，各自独立计时；</li>
 *   <li>透明度 {@code 70 ~ 70+(60+130×强度)}，每帧再乘 0.6~1.0 抖动，
 *       且必然带<b>非零 alpha</b>（alpha 为 0 时文字完全不可见 —— 这里踩过坑）；</li>
 *   <li>统一<b>加粗</b>（{@code ChatFormatting.BOLD}）：弱化"UI 提示"感，更像贴耳的耳语；</li>
 *   <li>文字全部是<b>可翻译键</b>（{@code whisper.tinkersnewlife.*}），中英各一份，加语言包即可扩展；</li>
 *   <li>强度 {@code level}（0~1）来自 {@code UnnameableAmbience}（已平滑），
 *       低于 0.02 时整个渲染直接返回（效果结束不会有残留）。</li>
 * </ul>
 */
public final class UnnameableWhisperRenderer {

    /** 低语内容（可翻译键） */
    private static final String[] KEYS = {
            "whisper.tinkersnewlife.i_see_you",
            "whisper.tinkersnewlife.he_watches",
            "whisper.tinkersnewlife.life_ascend",
            "whisper.tinkersnewlife.spirit_endures",
            "whisper.tinkersnewlife.embrace_mother",
            "whisper.tinkersnewlife.embrace_chaos",
            "whisper.tinkersnewlife.accept_gift",
            "whisper.tinkersnewlife.grow",
            "whisper.tinkersnewlife.eat",
            "whisper.tinkersnewlife.love",
            "whisper.tinkersnewlife.save_me",
            "whisper.tinkersnewlife.we_need_you",
            "whisper.tinkersnewlife.rebirth",
    };

    /** 低语颜色（都不含 alpha，alpha 每帧随机叠加）：惨白偏血色 / 苍白紫 / 病态青 */
    private static final int[] TINTS = {0xD8C2C6, 0xD9C8F2, 0xC9E6F2};

    /** 同时可能出现的低语条数上限（强度决定实际用几条） */
    private static final int SLOTS = 3;

    /** 一句低语在原地停留多少 tick（0.5~0.9 秒：够读，又不至于"钉"在屏幕上） */
    private static final int HOLD_MIN = 10;
    private static final int HOLD_MAX = 18;

    /** 每帧"这一帧干脆不画"的概率 —— 位置不动，所以这个闪烁只让字忽明忽暗、偶尔消失 */
    private static final float SKIP_PER_FRAME = 0.18F;

    private static final Random RNG = new Random();

    private static final String[] SLOT_KEY = new String[SLOTS];
    private static final int[] SLOT_X = new int[SLOTS];
    private static final int[] SLOT_Y = new int[SLOTS];
    private static final int[] SLOT_TINT = new int[SLOTS];
    private static final int[] SLOT_ALPHA = new int[SLOTS];
    private static final boolean[] SLOT_ACTIVE = new boolean[SLOTS];
    /** 每个槽位"这一句停留到哪个 tick" */
    private static final int[] SLOT_UNTIL = new int[SLOTS];

    private UnnameableWhisperRenderer() {
    }

    /** 配置里低语文字是否开启（默认开） */
    public static boolean enabled() {
        try {
            return ModConfig.UNNAMEABLE_WHISPERS.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    /**
     * 每帧调用一次（由 {@code UnnameableOverlay} 在 HUD 层调用）。
     *
     * @param tick  客户端 tick 计数（决定何时换句子）
     * @param level 平滑后的强度 0~1
     */
    public static void render(GuiGraphics graphics, int width, int height, int tick, float level) {
        if (width <= 0 || height <= 0) return;
        level = Math.max(0.0F, Math.min(1.0F, level));
        if (level <= 0.02F) return;

        Font font = Minecraft.getInstance().font;
        int wanted = 1 + Math.round(level * 2.0F); // 强度越高同时闪现的条数越多（1~SLOTS）

        for (int i = 0; i < SLOTS; i++) {
            if (i >= wanted) {
                // 强度降下来了：多余的槽位立刻失效，不用等它自然到期
                SLOT_UNTIL[i] = tick;
                continue;
            }
            if (tick >= SLOT_UNTIL[i]) {
                roll(i, width, height, tick, level, font);
            }
            if (!SLOT_ACTIVE[i]) continue;
            // 逐帧闪烁：偶尔整帧不画 + 透明度抖动（位置不变，所以还读得出来）
            if (RNG.nextFloat() < SKIP_PER_FRAME) continue;
            int alpha = (int) (SLOT_ALPHA[i] * (0.6F + RNG.nextFloat() * 0.4F));
            if (alpha <= 8) continue; // 颜色必须带非零 alpha，否则文本完全不可见
            graphics.drawString(font, text(i), SLOT_X[i], SLOT_Y[i], (alpha << 24) | SLOT_TINT[i], true);
        }
    }

    /** 重掷第 i 条低语：位置、内容、颜色、亮度，并安排下一次更换的时刻 */
    private static void roll(int i, int width, int height, int tick, float level, Font font) {
        SLOT_UNTIL[i] = tick + HOLD_MIN + RNG.nextInt(HOLD_MAX - HOLD_MIN + 1);
        // 每条独立地"这次要不要出现"（越弱越容易直接不出现）
        SLOT_ACTIVE[i] = RNG.nextFloat() <= 0.45F + 0.55F * level;
        if (!SLOT_ACTIVE[i]) return;

        SLOT_KEY[i] = KEYS[RNG.nextInt(KEYS.length)];
        int textWidth = font.width(text(i));
        SLOT_X[i] = RNG.nextInt(Math.max(1, width - textWidth - 20)) + 10;
        SLOT_Y[i] = RNG.nextInt(Math.max(1, height - 20)) + 10;
        SLOT_TINT[i] = TINTS[RNG.nextInt(TINTS.length)];
        // 越强越亮：70 ~ 70+(60+130×强度)
        SLOT_ALPHA[i] = 70 + RNG.nextInt(Math.max(1, (int) (60 + 130 * level)));
    }

    /** 第 i 条低语的文本（可翻译键 + 加粗） */
    private static Component text(int i) {
        return Component.translatable(SLOT_KEY[i]).withStyle(ChatFormatting.BOLD);
    }
}
