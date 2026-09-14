package com.mofengbaizhi.tinkersnewlife.client.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Random;

/**
 * 「不可名状」的<b>低语文字</b>：屏幕随机位置闪现一句随机的话，闪烁、随机跳过、时隐时现。
 *
 * <h2>为什么不是"每帧全随机"</h2>
 * 如果每帧都把位置和内容重新随机会变成一团乱跳的雪花（根本读不出字）。
 * 这里拆成两层：
 * <ul>
 *   <li><b>位置与内容每 {@link #REROLL_MIN}~{@link #REROLL_MAX} tick 才换一次</b>
 *       （≈0.4~1 秒），所以一条低语会"停"一小会儿再飘到别处；</li>
 *   <li><b>每帧只随机"这一帧要不要画 + 透明度"</b> —— 这才是"闪现"的手感，
 *       配上随机跳过，就成了耳语式的时隐时现。</li>
 * </ul>
 *
 * <p>文字全部是<b>可翻译键</b>（{@code whisper.tinkersnewlife.*}），中英文各有一份，加语言包即可扩展。
 * 强度 {@code level}（0~1）来自效果等级，越高则同时出现的条数越多、越亮。
 */
public final class UnnameableWhisperRenderer {

    /** 低语内容（可翻译键）——顺序即权重（靠前的更容易被抽到） */
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

    /** 低语颜色（都不含 alpha，alpha 由随机值叠加）：苍白紫 / 惨白青 / 病态粉 */
    private static final int[] TINTS = {0xD9C8F2, 0xC9E6F2, 0xE8C8D9};

    /** 同时存在的低语槽位数（强度按倍数取用前 N 个） */
    private static final int SLOTS = 4;

    private static final int REROLL_MIN = 8;
    private static final int REROLL_MAX = 20;

    /** 每帧"这一帧不画"的概率基础值（配合随机跳过，做出闪烁感） */
    private static final float SKIP_BASE = 0.28F;
    private static final float SKIP_PER_LEVEL = 0.30F;

    private static final Random RNG = new Random();

    /** 每个槽位当前这句低语的状态 */
    private static final String[] SLOT_KEY = new String[SLOTS];
    private static final int[] SLOT_X = new int[SLOTS];
    private static final int[] SLOT_Y = new int[SLOTS];
    private static final int[] SLOT_TINT = new int[SLOTS];
    private static final int[] SLOT_ALPHA = new int[SLOTS];
    private static final boolean[] SLOT_ACTIVE = new boolean[SLOTS];

    private static int nextRerollTick = 0;

    private UnnameableWhisperRenderer() {
    }

    /**
     * 每帧调用一次。
     *
     * @param level 强度 0~1（来自效果等级）
     */
    public static void render(GuiGraphics graphics, int width, int height, int tick, float level) {
        if (width <= 0 || height <= 0) return;
        level = Math.max(0.0F, Math.min(1.0F, level));

        if (tick >= nextRerollTick) {
            reroll(width, height, tick, level);
        }

        Font font = Minecraft.getInstance().font;
        float skipChance = SKIP_BASE + SKIP_PER_LEVEL * level;

        for (int i = 0; i < SLOTS; i++) {
            if (!SLOT_ACTIVE[i]) continue;
            // 每帧独立掷一次骰子：这一帧跳过 = 闪一下
            if (RNG.nextFloat() < skipChance) continue;

            Component text = Component.translatable(SLOT_KEY[i]);
            // 每帧给透明度一点抖动，做出"忽明忽暗"
            int alpha = (int) (SLOT_ALPHA[i] * (0.55F + RNG.nextFloat() * 0.45F));
            if (alpha <= 6) continue;
            // 颜色必须带非零 alpha，否则文本完全不可见（`(alpha << 24) | rgb`）
            graphics.drawString(font, text, SLOT_X[i], SLOT_Y[i], (alpha << 24) | SLOT_TINT[i], true);
        }
    }

    /** 重掷所有槽位的位置/内容/亮度：强度越高，同时出现的条数越多 */
    private static void reroll(int width, int height, int tick, float level) {
        nextRerollTick = tick + REROLL_MIN + RNG.nextInt(REROLL_MAX - REROLL_MIN + 1);

        int active = 1 + Math.round(level * (SLOTS - 1));
        Font font = Minecraft.getInstance().font;

        for (int i = 0; i < SLOTS; i++) {
            SLOT_ACTIVE[i] = i < active;
            if (!SLOT_ACTIVE[i]) continue;
            // 每条独立地"这次要不要出现"（越弱越容易直接不出现）
            if (RNG.nextFloat() > 0.45F + 0.55F * level) {
                SLOT_ACTIVE[i] = false;
                continue;
            }
            String key = KEYS[RNG.nextInt(KEYS.length)];
            int textWidth = font.width(Component.translatable(key));
            int x = RNG.nextInt(Math.max(1, width - textWidth - 20)) + 10;
            int y = RNG.nextInt(Math.max(1, height - 20)) + 10;
            SLOT_KEY[i] = key;
            SLOT_X[i] = x;
            SLOT_Y[i] = y;
            SLOT_TINT[i] = TINTS[RNG.nextInt(TINTS.length)];
            // 越强越亮：40 ~ 235
            SLOT_ALPHA[i] = 40 + RNG.nextInt(Math.max(1, (int) (60 + 175 * level)));
        }
    }
}
