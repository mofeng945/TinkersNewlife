package com.mofengbaizhi.tinkersnewlife.client.hud;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Random;

/**
 * TNL915 \u2605\u2605 \u514b\u56fe\u9732\u00b7\u58a8\u9ed8\u7684**\u8840\u6761\u540d\u5b57**\u6e32\u67d3 \u2713
 *
 * <p>\u2605\u7528\u6237\u53e3\u5f84 \u2713\uff1a
 * <ul>
 *   <li>\u2605``[\u661f\u8fb0\u9b54\u5973]``\uff1a\u2605\u201c\u5f13\u90a3\u4e00\u5957\u5f69\u8272\u6e32\u67d3\u201d\u5728**\u7ea2/\u9ed1/\u91d1**\u4e4b\u95f4\u53d8\u5316 \u2713\uff1b</li>
 *   <li>\u2605``\u58a8\u9ed8\u00b7\u514b\u56fe\u9732``\uff1a\u2605\u540c\u4e00\u5957\u6d41\u5149\uff0c\u4f46\u5728**\u9ed1/\u767d/\u7070**\u4e4b\u95f4\u53d8\u5316 \u2713\uff1b</li>
 *   <li>\u2605\u514b\u56fe\u9732\uff1a\u2605\u52a0**\u4e71\u7801\u4fee\u9970\u7b26** \u2713\uff08\u2605\u7528 {@code OBFUSCATED} \u968f\u673a\u5b57\u7b26 \u2713\uff09\uff1b</li>
 *   <li>\u2605**\u6bcf 100 tick \u6709 10% \u6982\u7387**\u5c55\u793a**\u771f\u540d** \u2713\uff08\u2605\u771f\u540d = \u514b\u56fe\u9732\u00b7\u58a8\u9ed8 \u2713\uff09\u3002</li>
 * </ul>
 */
public final class KtuluName {

    /** \u2605\u771f\u540d\u6982\u7387\u68c0\u67e5\u95f4\u9694\uff08tick\uff09\u2713 */
    private static final int CHECK_TICKS = 100;
    /** \u2605\u771f\u540d\u51fa\u73b0\u6982\u7387 \u2713 */
    private static final float TRUE_CHANCE = 0.10F;

    private static final Random RND = new Random();
    private static boolean showTrue = false;
    private static int tickCounter = 0;
    private static String glitch = "xkq";

    private KtuluName() {
    }

    /** \u2605\u6bcf tick \u8c03\u4e00\u6b21\uff08\u2605\u5ba2\u6237\u7aef \u2713\uff09 */
    public static void clientTick() {
        tickCounter++;
        if (tickCounter % 20 == 0) {
            glitch = randomGlitch();
        }
        if (tickCounter >= CHECK_TICKS) {
            tickCounter = 0;
            showTrue = RND.nextFloat() < TRUE_CHANCE;      // \u2605\u6bcf 100 tick 10% \u2713
        }
    }

    /**
     * TNL917 \u2605\u2605 \u4fee\u6b63\uff1a\u2605\u4e71\u7801\u8981"\u770b\u7740\u5c31\u662f\u4e71\u7801" \u2713
     *
     * <p>\u2605\u7528\u6237\u53cd\u9988\uff1a\u2605"\u4f60\u4e71\u7801\u6ca1\u7801\u4f4f\u554a" \u2713
     * \u2605\u539f\u56e0 \u2717\uff1a\u2605\u6211\u7684\u5b57\u7b26\u6c60\u91cc\u653e\u4e86\u2605**\u771f\u5b57** \u2717
     * \uff08\u2605\u5757\u5b57\u7b26/\u65e5\u6587/\u6c49\u5b57 \u2713\uff09\u21d2 \u2605\u5373\u4f7f\u52a0\u4e86 OBFUSCATED
     * \u4e5f\u80fd\u88ab\u770b\u51fa\u662f\u4ec0\u4e48\u5b57 \u2717\u3002
     * \u2605\u73b0\u5728\uff1a\u2605\u53ea\u7528**\u82f1\u6587\u5b57\u6bcd \u002b \u6570\u5b57** \u2713
     * \u21d2 \u2605\u6bcf\u5e27\u968f\u673a\u91cd\u6392 \u21d2 \u2605\u770b\u8d77\u6765\u5c31\u662f\u4e00\u4e32\u4e71\u7801 \u2713\u2713
     */
    private static String randomGlitch() {
        String pool = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        int len = 4 + RND.nextInt(3);
        for (int i = 0; i < len; i++) {
            sb.append(pool.charAt(RND.nextInt(pool.length())));
        }
        return sb.toString();
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

    /** \u2605\u7ec4\u5408\u51fa\u5b8c\u6574\u540d\u5b57\uff1a\u2605[\u661f\u8fb0\u9b54\u5973]\u58a8\u9ed8\u00b7\u514b\u56fe\u9732 \u2713 */
    public static Component build() {
        MutableComponent out = Component.literal("");
        // \u2605[\u661f\u8fb0\u9b54\u5973] \u2014\u2014 \u2605\u7ea2/\u9ed1/\u91d1 \u2713
        out.append(flow("[", 0xFF2A2A, 0x101010, 0xFFC83C, 0.55D, 0.14D));
        out.append(flow("\u661f\u8fb0\u9b54\u5973", 0xFF2A2A, 0x101010, 0xFFC83C, 0.55D, 0.14D));
        out.append(flow("]", 0xFF2A2A, 0x101010, 0xFFC83C, 0.55D, 0.14D));
        // \u2605\u58a8\u9ed8 \u2014\u2014 \u2605\u9ed1/\u767d/\u7070 \u2713
        out.append(flow("\u58a8\u9ed8", 0xFFFFFF, 0x808080, 0x202020, 0.45D, 0.18D));
        out.append(flow("\u00b7", 0xFFFFFF, 0x808080, 0x202020, 0.45D, 0.18D));
        // \u2605\u514b\u56fe\u9732 \u2014\u2014 \u2605\u9ed1/\u767d/\u7070 \u002b \u4e71\u7801 \u2713
        if (showTrue) {
            // \u2605\u771f\u540d\uff08\u2605\u6bcf 100 tick 10% \u2713\uff09
            out.append(flow("\u514b\u56fe\u9732\u00b7\u58a8\u9ed8", 0xFF2A2A, 0x101010, 0xFFC83C, 0.55D, 0.14D));
        } else {
            out.append(flow("\u514b\u56fe\u9732", 0xFFFFFF, 0x808080, 0x202020, 0.45D, 0.18D));
            out.append(Component.literal(glitch).withStyle(ChatFormatting.OBFUSCATED));
        }
        return out;
    }
}
