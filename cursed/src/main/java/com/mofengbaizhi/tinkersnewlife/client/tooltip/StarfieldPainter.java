package com.mofengbaizhi.tinkersnewlife.client.tooltip;

import net.minecraft.client.gui.GuiGraphics;

/**
 * TNL896 \u2605\u2605 \u661f\u7a7a\u7ed8\u5236\uff08\u2605\u7eaf\u9759\u6001\u5de5\u5177 \u2713\uff09
 *
 * <p>\u2605\u7528\u6237\u53e3\u5f84\uff1a\u2605"\u5728\u5de5\u5177\u63d0\u793a\u6846\u5185\u6e32\u67d3\u53ef\u52a8\u7684\u661f\u7a7a\u80cc\u666f" \u2713
 * \u2605\u65e7\u65b9\u6848\uff08{@code Item#getTooltipImage} \u2717\uff09\u5d29\u6e83\uff1a
 * \u2605{@code IllegalArgumentException: Unknown TooltipComponent} \u2717
 * \uff08\u2605\u539f\u7248 {@code ClientTooltipComponent.create} \u6ca1\u8ba4\u6211\u4eec\u7684\u5bf9\u8c61 \u2713\uff09\u3002
 * \u2605\u73b0\u5728\u6539\u8d70**\u5de5\u5177\u63d0\u793a\u4e8b\u4ef6** \u2713\uff08\u2605\u4e0d\u78b0\u7ec4\u4ef6\u63a5\u53e3 \u2717\uff09\u3002
 */
public final class StarfieldPainter {

    private static final int STARS = 120;

    private StarfieldPainter() {
    }

    /** \u2605\u5728 (x,y,w,h) \u533a\u57df\u91cc\u753b\u661f\u7a7a\uff08\u2605\u6bcf\u5e27\u8c03\u7528 \u21d2 \u2605\u4f1a\u52a8 \u2713\uff09 */
    public static void draw(GuiGraphics g, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        long t = System.currentTimeMillis();
        g.fill(x, y, x + w, y + h, 0xF0080A12);            // \u2605\u6df1\u7a7a\u5e95 \u2713
        for (int i = 0; i < 3; i++) {                       // \u2605\u661f\u4e91\u5e26 \u2713
            int ny = y + (h * (i + 1)) / 4 + (int) (Math.sin(t / 3000.0 + i) * 2);
            g.fill(x + 2, ny, x + w - 2, ny + 1, 0x18283860);
        }
        for (int i = 0; i < STARS; i++) {
            long seed = (i + 1) * 2654435761L;
            int sx = (int) Math.floorMod((seed >>> 8) % w, w);
            int sy = (int) Math.floorMod((seed >>> 21) % h, h);
            int drift = (int) ((t / 120L + i * 3L) % w);
            int px = x + Math.floorMod(sx - drift, w);
            double tw = (Math.sin(t / 640.0 + i * 1.7) + 1.0) / 2.0;
            int a = (int) (70 + 170 * tw);
            boolean bright = i % 17 == 0;
            int c = bright ? 0xFFFFFF : 0xD8DCF0;
            g.fill(px, y + sy, px + 1, y + sy + 1, (a << 24) | c);
            if (bright) {
                g.fill(px - 1, y + sy, px, y + sy + 1, ((a / 3) << 24) | c);
                g.fill(px + 1, y + sy, px + 2, y + sy + 1, ((a / 3) << 24) | c);
            }
        }
        g.fill(x, y, x + w, y + 1, 0x40FFFFFF);             // \u2605\u767d\u7070\u7ec6\u8fb9 \u2713
        g.fill(x, y + h - 1, x + w, y + h, 0x40FFFFFF);
        g.fill(x, y, x + 1, y + h, 0x30FFFFFF);
        g.fill(x + w - 1, y, x + w, y + h, 0x30FFFFFF);
    }
}
