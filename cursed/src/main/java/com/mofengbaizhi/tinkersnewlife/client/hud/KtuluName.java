package com.mofengbaizhi.tinkersnewlife.client.hud;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Random;

/**
 * TNL918 \u2605\u2605 \u8840\u6761\u540d\u5b57\uff1a\u2605[\u661f\u8fb0\u9b54\u5973]\u58a8\u9ed8\u00b7\u514b\u56fe\u9732 \u2713
 *
 * <p>\u2605\u7528\u6237\u53e3\u5f84 \u2713\uff1a
 * <ul>
 *   <li>\u2605``[\u661f\u8fb0\u9b54\u5973]``\uff1a\u2605\u5f13\u90a3\u5957\u6d41\u5149\uff0c\u5728**\u7ea2/\u9ed1/\u91d1**\u4e4b\u95f4\u53d8 \u2713\uff1b</li>
 *   <li>\u2605``\u58a8\u9ed8``\u4e0e``\u514b\u56fe\u9732``\uff1a\u2605\u540c\u4e00\u5957\u6d41\u5149\uff0c\u5728**\u9ed1/\u767d/\u7070**\u4e4b\u95f4\u53d8 \u2713\uff1b</li>
 *   <li>\u2605**\u201c\u514b\u56fe\u9732\u201d\u8fd9\u4e09\u4e2a\u5b57\u672c\u8eab\u7528\u539f\u7248 \u00a7k \u6df7\u6dc6\u76d6\u4f4f** \u2713
 *       \uff08\u2605\u7528\u6237\uff1a\u201cmc \u539f\u7248\u4e0d\u662f\u6709\u628a\u67d0\u4e9b\u5b57\u53d8\u6210\u4e71\u7801\u7684\u6df7\u6dc6\u65b9\u6cd5\u5417\u201d \u2713\uff09\uff1b</li>
 *   <li>\u2605\u2605\u6bcf **100 tick \u63b7 10%**\uff0c\u547d\u4e2d\u5219**\u771f\u5b57\u53ea\u9732 6 tick\uff08\u2605\u4e00\u77ac\u95f4\uff09** \u2713\u3002</li>
 * </ul>
 */
public final class KtuluName {

    private static final int CHECK_TICKS = 100;
    private static final float TRUE_CHANCE = 0.10F;
    /** \u2605\u771f\u5b57\u9732\u591a\u4e45\uff08tick\uff09\u2713 */
    private static final int TRUE_TICKS = 6;

    private static final Random RND = new Random();
    private static int revealLeft = 0;
    private static int tickCounter = 0;

    private KtuluName() {
    }

    /** \u2605\u6bcf tick \u8c03\u4e00\u6b21\uff08\u2605\u5ba2\u6237\u7aef \u2713\uff09 */
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

    /** \u2605\u4e09\u8272\u5faa\u73af\u6d41\u5149\uff08\u2605\u9010\u5b57\u53d6\u8272 \u2713\uff09 */
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

    /** \u2605\u7ec4\u5408\u5b8c\u6574\u540d\u5b57 \u2713 */
    public static Component build() {
        MutableComponent out = Component.literal("");
        // \u2605[\u661f\u8fb0\u9b54\u5973] \u2014\u2014 \u2605\u7ea2/\u9ed1/\u91d1 \u2713
        out.append(flow("[\u661f\u8fb0\u9b54\u5973]", 0xFF2A2A, 0x101010, 0xFFC83C, 0.55D, 0.14D));
        // \u2605\u58a8\u9ed8\u00b7 \u2014\u2014 \u2605\u9ed1/\u767d/\u7070 \u2713
        out.append(flow("\u58a8\u9ed8\u00b7", 0xFFFFFF, 0x808080, 0x202020, 0.45D, 0.18D));
        // \u2605\u514b\u56fe\u9732 \u2014\u2014 \u2605\u5e73\u65f6 \u00a7k \u76d6\u4f4f \u2713 / \u2605\u5076\u5c14\u9732\u771f\u5b57 \u2713
        if (revealLeft > 0) {
            out.append(flow("\u514b\u56fe\u9732", 0xFFFFFF, 0x808080, 0x202020, 0.45D, 0.18D));
        } else {
            out.append(Component.literal("\u514b\u56fe\u9732").withStyle(ChatFormatting.OBFUSCATED));
        }
        return out;
    }
}
