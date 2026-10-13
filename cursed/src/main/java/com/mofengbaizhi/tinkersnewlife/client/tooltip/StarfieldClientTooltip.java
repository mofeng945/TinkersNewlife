package com.mofengbaizhi.tinkersnewlife.client.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

/**
 * TNL895 \u2605\u2605 \u5de5\u5177\u63d0\u793a\u6846\u5185\u7684**\u53ef\u52a8\u661f\u7a7a\u80cc\u666f** \u2713
 *
 * <p>\u2605\u7528\u6237\u53e3\u5f84\uff1a\u2605"\u6211\u8bf4\u7684\u661f\u7a7a\u6e32\u67d3\u662f\u6307\u5728\u5de5\u5177\u63d0\u793a\u6846\u5185\u6e32\u67d3\u53ef\u52a8\u7684\u661f\u7a7a\u80cc\u666f" \u2713
 *
 * <p>\u2605\u673a\u5236 \u2713\uff1a\u2605Forge \u7684 {@code IClientItemExtensions#getTooltipImage} \u2713
 * \u21d2 \u2605\u539f\u7248\u6bcf\u5e27\u90fd\u4f1a\u8c03 {@code renderImage} \u2713 \u21d2 \u2605\u7528\u65f6\u95f4\u7b97\u4f4d\u7f6e/\u4eae\u5ea6\u5c31\u80fd"\u52a8" \u2713\u3002
 * \u2605\u7528 {@code GuiGraphics#fill} \u753b 1px \u661f\u70b9 \u2713\uff08\u2605\u4e0d\u9700\u8981\u8d34\u56fe\u6587\u4ef6 \u2713 \u21d2 \u2605\u4e0d\u78b0\u7528\u6237\u7f8e\u672f \u2713\uff09\u3002
 */
public class StarfieldClientTooltip implements ClientTooltipComponent, net.minecraft.world.inventory.tooltip.TooltipComponent {

    private final int width;
    private final int height;
    /** \u2605\u661f\u70b9\u6570 \u2713 */
    private static final int STARS = 120;

    private StarfieldClientTooltip(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /** TNL895 \u2605\u5de5\u5382\uff08\u2605\u53ea\u5728\u5ba2\u6237\u7aef\u8c03\u7528 \u2713\uff09 */
    public static StarfieldClientTooltip of(int width, int height) {
        return new StarfieldClientTooltip(width, height);
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public int getWidth(Font font) {
        return this.width;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics g) {
        long t = System.currentTimeMillis();
        int w = this.width;
        int h = this.height;
        // \u2605\u6df1\u7a7a\u5e95\uff08\u2605\u5e26\u900f\u660e \u2713\uff09
        g.fill(x, y, x + w, y + h, 0xE6060812);
        // \u2605\u4e00\u5c42\u6781\u6de1\u7684\u661f\u4e91\u5e26 \u2713
        for (int i = 0; i < 3; i++) {
            int ny = y + (int) ((h * (i + 1)) / 4.0F) + (int) (Math.sin(t / 3000.0 + i) * 2);
            g.fill(x + 2, ny, x + w - 2, ny + 1, 0x14203050);
        }
        // \u2605\u661f\u70b9\uff08\u2605\u81ea\u5df2\u6f02\u79fb \u002b \u95ea\u70c1 \u2713\uff09
        for (int i = 0; i < STARS; i++) {
            long seed = (i + 1) * 2654435761L;
            int sx = (int) Math.floorMod((seed >>> 8) % w, w);
            int sy = (int) Math.floorMod((seed >>> 21) % h, h);
            int drift = (int) ((t / 120L + i * 3L) % w);           // \u2605\u5411\u5de6\u6162\u6162\u6f02 \u2713
            int px = x + Math.floorMod(sx - drift, w);
            double tw = (Math.sin(t / 640.0 + i * 1.7) + 1.0) / 2.0;
            int a = (int) (70 + 170 * tw);
            boolean bright = i % 17 == 0;
            int c = bright ? 0xFFFFFF : 0xD8DCF0;
            g.fill(px, y + sy, px + 1, y + sy + 1, (a << 24) | c);
            if (bright) {
                g.fill(px - 1, y + sy, px, y + sy + 1, (a / 3 << 24) | c);
                g.fill(px + 1, y + sy, px + 2, y + sy + 1, (a / 3 << 24) | c);
            }
        }
        // \u2605\u8fb9\u6846\uff08\u2605\u767d\u7070\u7ec6\u8fb9 \u2713\uff09
        g.fill(x, y, x + w, y + 1, 0x40FFFFFF);
        g.fill(x, y + h - 1, x + w, y + h, 0x40FFFFFF);
        g.fill(x, y, x + 1, y + h, 0x30FFFFFF);
        g.fill(x + w - 1, y, x + w, y + h, 0x30FFFFFF);
    }
}
