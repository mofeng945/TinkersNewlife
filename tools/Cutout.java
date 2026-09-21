import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * 墨默立绘抠图（纯色背景 + **从四边泛洪**）。
 *
 * <p>为什么不用"全局色键"：角色身上有大量近白衣物（头巾/衣领/十字架高光）⇒ 全局按颜色抠会把衣服一起抠掉 ✗
 * ⇒ 只抠"与画面边缘连通的背景色" ✓ 内部白衣服因为不连通，安全 ✓。
 *
 * <p><b>容差口径 = 逐通道最大差</b>（不是三通道差值和 ✗）：
 * 米白背景 (252,252,236) 对**浅色皮肤** (255,224,196) 的**差值和**只有 71 ⇒ 用"和"做阈值会把腿当背景、
 * 顺着画面下边缘从下往上吃干净（墨默两条腿就是这么没的 ✗）；
 * 改成逐通道后皮肤在 G/B 通道分别差 28/40 ⇒ 阈值 20 就能安全保住 ✓，同时 jpg 噪点 ±20 内照样清得掉 ✓。
 *
 * <p>用法：{@code java tools/Cutout.java <逐通道容差> <src.jpg> <dst.png> [<src2> <dst2> ...]}
 * （JDK 11+ 直接单文件运行 ✓ 不需要编译 ✓）
 */
public final class Cutout {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("usage: Cutout <tolerance> <src.jpg> <dst.png> [more pairs...]");
            return;
        }
        int tol = Integer.parseInt(args[0]);
        for (int i = 1; i + 1 < args.length; i += 2) {
            File src = new File(args[i]);
            File dst = new File(args[i + 1]);
            if (!src.isFile()) {
                System.out.println("missing: " + src.getName());
                continue;
            }
            long t0 = System.currentTimeMillis();
            String info = run(src, dst, tol);
            System.out.printf("%-16s -> %-20s %s  (%d ms)%n",
                    src.getName(), dst.getName(), info, System.currentTimeMillis() - t0);
        }
    }

    private static int key(int p) {
        return (((p >> 16) & 0xFF) >> 3) << 10 | (((p >> 8) & 0xFF) >> 3) << 5 | ((p & 0xFF) >> 3);
    }

    private static String run(File src, File dst, int tol) throws Exception {
        BufferedImage in = ImageIO.read(src);
        int w = in.getWidth();
        int h = in.getHeight();
        int[] px = new int[w * h];
        in.getRGB(0, 0, w, h, px, 0, w);

        // 背景色 = **边框一圈的众数**（5bit/通道量化）
        // ⚠ 四角平均不可靠：jpg 四角常有噪点/描边 ⇒ 取到脏色 ⇒ 一个像素都不匹配（实测 clear=0%）
        int[] hist = new int[1 << 15];
        int ring = Math.max(1, Math.min(3, Math.min(w, h) / 64));
        for (int x = 0; x < w; x++) {
            for (int k = 0; k < ring; k++) hist[key(px[x + k * w])]++;
            for (int k = 0; k < ring; k++) hist[key(px[x + (h - 1 - k) * w])]++;
        }
        for (int y = 0; y < h; y++) {
            for (int k = 0; k < ring; k++) hist[key(px[k + y * w])]++;
            for (int k = 0; k < ring; k++) hist[key(px[(w - 1 - k) + y * w])]++;
        }
        int bestKey = 0, bestCnt = -1;
        for (int i = 0; i < hist.length; i++) if (hist[i] > bestCnt) { bestCnt = hist[i]; bestKey = i; }
        int br = (((bestKey >> 10) & 31) << 3) | 4;
        int bg = (((bestKey >> 5) & 31) << 3) | 4;
        int bb = ((bestKey & 31) << 3) | 4;
        int limit = tol;                                    // **逐通道**阈值（见类注释：用"和"会吃掉腿 ✗）

        boolean[] seen = new boolean[w * h];
        int[] stack = new int[w * h];
        int sp = 0;
        // 先把四条边上的"背景色"像素压栈
        for (int x = 0; x < w; x++) { sp = push(px, seen, stack, sp, x, 0, w, h, br, bg, bb, limit); sp = push(px, seen, stack, sp, x, h - 1, w, h, br, bg, bb, limit); }
        for (int y = 0; y < h; y++) { sp = push(px, seen, stack, sp, 0, y, w, h, br, bg, bb, limit); sp = push(px, seen, stack, sp, w - 1, y, w, h, br, bg, bb, limit); }
        int cleared = 0;   // 统计放到泛洪之后（之前写在循环前 ⇒ clear% 永远只有最外一圈 ✗）
        while (sp > 0) {
            int id = stack[--sp];
            int x = id % w;
            int y = id / w;
            sp = push(px, seen, stack, sp, x + 1, y, w, h, br, bg, bb, limit);
            sp = push(px, seen, stack, sp, x - 1, y, w, h, br, bg, bb, limit);
            sp = push(px, seen, stack, sp, x, y + 1, w, h, br, bg, bb, limit);
            sp = push(px, seen, stack, sp, x, y - 1, w, h, br, bg, bb, limit);
        }

        for (int i = 0; i < w * h; i++) if ((px[i] >>> 24) == 0) cleared++;
        BufferedImage full = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        full.setRGB(0, 0, w, h, px, 0, w);
        int targetH = Math.min(h, 800);
        int targetW = Math.max(1, Math.round(w * (targetH / (float) h)));
        BufferedImage out = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(full, 0, 0, targetW, targetH, null);
        g2.dispose();
        File dir = dst.getParentFile();
        if (dir != null) dir.mkdirs();
        ImageIO.write(out, "png", dst);
        return String.format("%dx%d  clear=%.1f%%  bg=(%d,%d,%d)", w, h, 100.0 * cleared / (w * h), br, bg, bb);
    }

    /** 把 (x,y) 压栈（若是没访问过、且颜色接近背景 ⇒ 清 alpha 并返回新栈顶） */
    private static int push(int[] px, boolean[] seen, int[] stack, int sp, int x, int y,
                            int w, int h, int br, int bg, int bb, int limit) {
        if (x < 0 || y < 0 || x >= w || y >= h) return sp;
        int id = y * w + x;
        if (seen[id]) return sp;
        seen[id] = true;
        int p = px[id];
        int dr = Math.abs(((p >> 16) & 0xFF) - br);
        int dg = Math.abs(((p >> 8) & 0xFF) - bg);
        int db = Math.abs((p & 0xFF) - bb);
        int d = Math.max(dr, Math.max(dg, db));             // 逐通道最大差 ✓
        if (d > limit) return sp;
        px[id] = p & 0x00FFFFFF;        // 清掉 alpha ⇒ 透明
        stack[sp++] = id;
        return sp;
    }
}
