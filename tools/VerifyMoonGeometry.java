/**
 * 月相几何最终验证 —— 对称阈值式，并且<b>验收对象是真正生成的 PNG</b>（不是公式自证）。
 *
 * <pre>
 *   月盘：圆心 (7.5, 7.5)、半径 R（对称）
 *   受光比例（官方口径）：frac(k) = (1 + cos(2πk/8)) / 2
 *   受光阈值：LT = 2·frac − 1        （k=0 满月 ⇒ LT=+1 全亮 ✓ ；k=4 新月 ⇒ LT=−1 全暗 ✓）
 *   归一化横坐标：u = dx / R
 *   受光：亏相（k=1..3）取 u ≤ LT ；盈相（k=5..7）取 u ≥ LT
 * </pre>
 * 期望：0 满月1.000 · 1 亏凸0.854 · 2 下弦0.500 · 3 残月0.146 · 4 新月0.000 · 5 娥眉0.146 · 6 上弦0.500 · 7 盈凸0.854
 * 亮面朝向：1..3 在左 · 5..7 在右。
 */
public class VerifyMoonGeometry {
    static final double CX = 7.5, CY = 7.5, R = 5.6;
    static final String[] NAME = {"Full", "WaningGibbous", "ThirdQuarter", "WaningCrescent",
            "New", "WaxingCrescent", "FirstQuarter", "WaxingGibbous"};
    static final String[] WANT_SIDE = {"center", "LEFT", "LEFT", "LEFT", "none", "RIGHT", "RIGHT", "RIGHT"};

    static boolean inDisc(double x, double y) {
        double dx = (x + 0.5) - CX, dy = (y + 0.5) - CY;
        return Math.sqrt(dx * dx + dy * dy) <= R;
    }

    static boolean isLit(int x, int y, int k) {
        if (!inDisc(x, y)) return false;
        double frac = (1 + Math.cos(2 * Math.PI * k / 8)) / 2;
        double lt = 2.0 * frac - 1.0;   // k=0 满月 => +1 全亮 ; k=4 新月 => -1 全暗
        double u = ((x + 0.5) - CX) / R;
        if (k == 0) return true;
        if (k == 4) return false;
        return (k >= 1 && k <= 3) ? (u <= lt) : (u >= -lt);
    }

    public static void main(String[] args) {
        System.out.printf("%-3s %-16s %-8s %-8s %-7s %-10s %-8s %s%n",
                "k", "name", "want", "got", "diff", "centroidX", "side", "ok?");
        boolean allOk = true;
        for (int k = 0; k < 8; k++) {
            int disc = 0, lit = 0; double sx = 0;
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                if (!inDisc(x, y)) continue;
                disc++;
                if (isLit(x, y, k)) { lit++; sx += x; }
            }
            double want = (1 + Math.cos(2 * Math.PI * k / 8)) / 2;
            double got = (double) lit / disc;
            double cx = lit == 0 ? -1 : sx / lit;
            String side = cx < 0 ? "none" : (cx < 7.3 ? "LEFT" : (cx > 7.7 ? "RIGHT" : "center"));
            boolean ok = Math.abs(got - want) < 0.07 && side.equals(WANT_SIDE[k]);
            if (!ok) allOk = false;
            System.out.printf("%-3d %-16s %-8.3f %-8.3f %-7.3f %-10.2f %-8s %s%n",
                    k, NAME[k], want, got, got - want, cx, side, ok ? "OK" : "MISMATCH");
        }
        System.out.println(allOk ? ">>> ALL OK：把这套几何搬进 GenPlanetariumArt" : ">>> 仍有不符");
        for (int k : new int[]{0, 1, 2, 3, 4, 5, 6, 7}) {
            System.out.println("\nk=" + k + " (" + NAME[k] + ")");
            for (int y = 0; y < 16; y++) {
                StringBuilder sb = new StringBuilder();
                for (int x = 0; x < 16; x++) sb.append(inDisc(x, y) ? (isLit(x, y, k) ? '#' : '.') : ' ');
                System.out.println(sb);
            }
        }
    }
}
