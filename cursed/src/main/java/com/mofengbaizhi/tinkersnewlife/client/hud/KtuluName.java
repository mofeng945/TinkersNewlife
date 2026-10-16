package com.mofengbaizhi.tinkersnewlife.client.hud;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Random;

/**
 * TNL918 ★★ 血条名字：★[星辰魔女]墨默·克图露 ✓
 *
 * <p>★用户口径 ✓：
 * <ul>
 *   <li>★``[星辰魔女]``：★弓那套流光，在**红/黑/金**之间变 ✓；</li>
 *   <li>★``墨默``与``克图露``：★同一套流光，在**黑/白/灰**之间变 ✓；</li>
 *   <li>★**“克图露”这三个字本身用原版 §k 混淆盖住** ✓
 *       （★用户：“mc 原版不是有把某些字变成乱码的混淆方法吗” ✓）；</li>
 *   <li>★★每 **100 tick 掷 10%**，命中则**真字只露 6 tick（★一瞬间）** ✓。</li>
 * </ul>
 */
public final class KtuluName {

    private static final int CHECK_TICKS = 100;
    private static final float TRUE_CHANCE = 0.10F;
    /** ★真字露多久（tick）✓ */
    private static final int TRUE_TICKS = 6;

    private static final Random RND = new Random();
    private static int revealLeft = 0;
    private static int tickCounter = 0;

    private KtuluName() {
    }

    /** ★每 tick 调一次（★客户端 ✓） */
    public static void clientTick() {
        if (revealLeft > 0) {
            revealLeft--;
        }
        tickCounter++;
        if (tickCounter >= CHECK_TICKS) {
            tickCounter = 0;
            if (RND.nextFloat() < TRUE_CHANCE) {
                revealLeft = TRUE_TICKS;
            }
        }
    }

    private static int lerp(int c1, int c2, double t) {
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }

    /** ★三色循环流光（★逐字取色 ✓） */
    public static MutableComponent flow(String text, int c1, int c2, int c3, double speed, double step) {
        MutableComponent out = Component.literal("");
        double t0 = System.currentTimeMillis() / 1000.0D * speed;
        for (int i = 0; i < text.length(); i++) {
            double ph = ((t0 + i * step) % 1.0D + 1.0D) % 1.0D;
            double u = ph < 0.5D ? ph * 2.0D : (1.0D - ph) * 2.0D;
            int col = u < 0.5D ? lerp(c1, c2, u * 2.0D) : lerp(c2, c3, (u - 0.5D) * 2.0D);
            out.append(Component.literal(String.valueOf(text.charAt(i))).setStyle(Style.EMPTY.withColor(col)));
        }
        return out;
    }

    /** ★组合完整名字 ✓ */
    public static Component build() {
        MutableComponent out = Component.literal("");
        // ★[星辰魔女] —— ★红/黑/金 ✓
        out.append(flow("[星辰魔女]", 0xFF2A2A, 0x101010, 0xFFC83C, 0.55D, 0.14D));
        // ★墨默· —— ★黑/白/灰 ✓
        out.append(flow("墨默·", 0xFFFFFF, 0x808080, 0x202020, 0.45D, 0.18D));
        // ★克图露 —— ★平时 §k 盖住 ✓ / ★偶尔露真字 ✓
        if (revealLeft > 0) {
            out.append(flow("克图露", 0xFFFFFF, 0x808080, 0x202020, 0.45D, 0.18D));
        } else {
            out.append(Component.literal("克图露").withStyle(ChatFormatting.OBFUSCATED));
        }
        return out;
    }
}
