package com.mofengbaizhi.tinkersnewlife.client.tooltip;

import net.minecraft.client.gui.GuiGraphics;

/**
 * TNL896 ★★ 星空绘制（★纯静态工具 ✓）
 *
 * <p>★用户口径：★"在工具提示框内渲染可动的星空背景" ✓
 * ★旧方案（{@code Item#getTooltipImage} ✗）崩溃：
 * ★{@code IllegalArgumentException: Unknown TooltipComponent} ✗
 * （★原版 {@code ClientTooltipComponent.create} 没认我们的对象 ✓）。
 * ★现在改走**工具提示事件** ✓（★不碰组件接口 ✗）。
 */
public final class StarfieldPainter {

    private static final int STARS = 120;

    private StarfieldPainter() {
    }

    /** ★在 (x,y,w,h) 区域里画星空（★每帧调用 ⇒ ★会动 ✓） */
    public static void draw(GuiGraphics g, int x, int y, int w, int h) {

        // TNL925 ★★ 用户口径：★"我不是让你把物品提示框背景往上拉，
        //   我是让你把**图层往上提**，透明度改回去" ✓
        //   ★做法 ✓：★先铺一层**不透明**的深空底 ✓
        //     ⇒ ★下面的物品格被盖住 ✗
        //   ★然后才是原来那层**半透明**星空 ✓
        //     （★星云/星点/边框全保留 ✓）
        g.fill(x, y, x + w, y + h, 0xFF06070E);
        if (w <= 0 || h <= 0) {
            return;
        }
        long t = System.currentTimeMillis();
        g.fill(x, y, x + w, y + h, 0xF0080A12);            // ★深空底 ✓
        for (int i = 0; i < 3; i++) {                       // ★星云带 ✓
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
        g.fill(x, y, x + w, y + 1, 0x40FFFFFF);             // ★白灰细边 ✓
        g.fill(x, y + h - 1, x + w, y + h, 0x40FFFFFF);
        g.fill(x, y, x + 1, y + h, 0x30FFFFFF);
        g.fill(x + w - 1, y, x + w, y + h, 0x30FFFFFF);
    }
}
