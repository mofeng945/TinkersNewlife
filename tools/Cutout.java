import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * 墨默立绘抠图（纯色背景 + **从边缘泛洪**）。
 *
 * <p>为什么不用"全局色键"：角色身上有大量近白衣物（头巾/衣领/十字架高光）⇒ 全局按颜色抠会把衣服一起抠掉 ✗
 * ⇒ 只抠"与画面边缘连通的背景色" ✓ 内部白衣服因为不连通，安全 ✓。
 *
 * <p><b>三条踩过的坑（都别再踩）：</b>
 * <ol>
 *   <li><b>容差必须逐通道</b>（`max(|Δr|,|Δg|,|Δb|)`）：用三通道**差值和**时，米白背景 `(252,252,236)` 对浅色皮肤
 *       `(255,224,196)` 的和差只有 71 ⇒ 腿被当成背景整段吃掉 ✗</li>
 *   <li><b>不要给泛洪加"中性/暖色"硬判据</b>：背景里 jpg 噪点一旦不满足判据就成了**墙**，泛洪被拦住 ⇒
 *       大片背景清不掉（实测 clear 从 29.7% 掉到 24.2%，`leftBg` 十万级 ✗），而腿上的斑驳空洞照旧 ✗</li>
 *   <li><b>不要从"下边缘"播种</b>（本类默认不播 ✓）：立绘是**半身裁在图里**的，腿/裙摆**贴着画面下边缘**，
 *       从下边缘播种 ⇒ 泛洪顺着下边缘直接进腿，自下往上啃（"多多少少被扣掉一些"就是这么来的 ✗）。
 *       画面下方的背景经由左右两侧绕过去照样能被清掉 ✓ 所以不播下边缘**不会**留下背景 ✓。</li>
 * </ol>
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

    /** 是否从**下边缘**播种：立绘腿/裙摆贴着下边缘 ⇒ **绝不能播**（否则顺着下边缘进腿啃腿 ✗） */
    private static final boolean SEED_BOTTOM = false;
    /** 是否从**上边缘**播种：头顶上方通常有背景 ⇒ 播 ✓ */
    private static final boolean SEED_TOP = true;

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
        // 播种：上边缘 / 左右边缘 ✓ **下边缘默认不播**（立绘腿贴下边缘 ⇒ 播了就会啃腿 ✗）
        if (SEED_TOP) for (int x = 0; x < w; x++) sp = push(px, seen, stack, sp, x, 0, w, h, br, bg, bb, limit);
        if (SEED_BOTTOM) for (int x = 0; x < w; x++) sp = push(px, seen, stack, sp, x, h - 1, w, h, br, bg, bb, limit);
        for (int y = 0; y < h; y++) {
            sp = push(px, seen, stack, sp, 0, y, w, h, br, bg, bb, limit);
            sp = push(px, seen, stack, sp, w - 1, y, w, h, br, bg, bb, limit);
        }
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
        // 自检①：还留在画面里、且**很确定是背景**（逐通道 ≤4）的不透明像素 ⇒ 应该接近 0（>0 = 有漏网背景块 ✗）
        int leftBg = 0;
        // 自检②：下边缘附近**还剩下的背景**（不播下边缘的代价）⇒ 应该接近 0 ❗️这条最要紧
        int bottomBg = 0;
        for (int i = 0; i < w * h; i++) {
            int p = px[i];
            if ((p >>> 24) == 0) continue;
            int r = (p >> 16) & 0xFF, gg = (p >> 8) & 0xFF, b = p & 0xFF;
            int d = Math.max(Math.abs(r - br), Math.max(Math.abs(gg - bg), Math.abs(b - bb)));
            if (d <= 4) {
                leftBg++;
                if (i / w > h * 0.75) bottomBg++;
            }
        }
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
        return String.format("%dx%d  clear=%.1f%%  bg=(%d,%d,%d)  近白残留(含白衣)=%d（其中下方=%d）", w, h, 100.0 * cleared / (w * h), br, bg, bb, leftBg, bottomBg);
    }

    /** 把 (x,y) 压栈（若是没访问过、且颜色接近背景 ⇒ 清 alpha 并返回新栈顶） */
    private static int push(int[] px, boolean[] seen, int[] stack, int sp, int x, int y,
                            int w, int h, int br, int bg, int bb, int limit) {
        if (x < 0 || y < 0 || x >= w || y >= h) return sp;
        int id = y * w + x;
        if (seen[id]) return sp;
        seen[id] = true;
        int p = px[id];
        int r = (p >> 16) & 0xFF, gg = (p >> 8) & 0xFF, b = p & 0xFF;
        int d = Math.max(Math.abs(r - br), Math.max(Math.abs(gg - bg), Math.abs(b - bb)));
        if (d > limit) return sp;                            // 逐通道最大差 ✓（无其它硬判据 ⇒ 不造墙 ✓）
        px[id] = p & 0x00FFFFFF;        // 清掉 alpha ⇒ 透明
        stack[sp++] = id;
        return sp;
    }
}
