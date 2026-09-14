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
 * <h2>逻辑（照着一份已验证可用的实现抄的）</h2>
 * <ul>
 *   <li><b>每帧独立掷骰子</b>决定"这一帧画不画"：跳过概率 {@code 1-(0.25+0.45×强度)}，
 *       也就是强度满时约 70% 的帧会画 —— 这正是"耳语"的闪烁手感；</li>
 *   <li><b>位置、内容、透明度每帧重掷</b>：所以文案会不断换地方闪，读不清但一直在，压迫感更强；</li>
 *   <li><b>条数 = 1 + round(强度×2)</b>（最多 3 条），强度越高同屏越多；</li>
 *   <li>透明度 {@code 40 ~ 40+(60+155×强度)}：永远半透明，且必然带<b>非零 alpha</b>
 *       （alpha 为 0 时文字完全不可见 —— 这里踩过坑，所以注释留在这）；</li>
 *   <li>统一<b>加粗</b>：弱化"UI 提示"感，更像贴近耳朵的低语。</li>
 * </ul>
 *
 * <p>文字全部是<b>可翻译键</b>（{@code whisper.tinkersnewlife.*}），中英文各有一份，加语言包即可扩展。
 * 强度 {@code level}（0~1）来自 {@code UnnameableAmbience}（已平滑），
 * 想要更明显/更淡可以调 {@code [unnameable] whispers} 与效果等级。
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

    private static final Random RNG = new Random();

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
     * @param level 平滑后的强度 0~1
     */
    public static void render(GuiGraphics graphics, int width, int height, float level) {
        if (width <= 0 || height <= 0) return;
        level = Math.max(0.0F, Math.min(1.0F, level));
        if (level <= 0.02F) return;

        Font font = Minecraft.getInstance().font;
        int count = 1 + Math.round(level * 2.0F); // 强度越高同时闪现的条数越多（1~3）

        for (int i = 0; i < count; i++) {
            // 每帧独立掷骰子：跳过的帧什么都不画 → 时隐时现
            if (RNG.nextFloat() > 0.25F + 0.45F * level) {
                continue;
            }
            Component text = Component.translatable(KEYS[RNG.nextInt(KEYS.length)])
                    .withStyle(ChatFormatting.BOLD);
            int textWidth = font.width(text);
            int x = RNG.nextInt(Math.max(1, width - textWidth - 20)) + 10;
            int y = RNG.nextInt(Math.max(1, height - 20)) + 10;
            // 颜色必须带非零 alpha，否则文本完全不可见（(alpha << 24) | rgb）
            int alpha = 40 + RNG.nextInt(Math.max(1, (int) (60 + 155 * level)));
            graphics.drawString(font, text, x, y, (alpha << 24) | TINTS[RNG.nextInt(TINTS.length)], true);
        }
    }
}
