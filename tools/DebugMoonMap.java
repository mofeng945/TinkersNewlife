/**
 * 月相几何调试：打印月盘像素数 + 每个相位的字符画（'#'=受光 '.'=暗面 ' '=盘外），
 * 一眼看出判据到底画出了什么。
 */
public class DebugMoonMap {
    static boolean isLit(double dx, double cos) { return (dx + cos) <= 0.0; }

    public static void main(String[] args) {
        for (int k = 0; k < 8; k++) {
            double cos = Math.cos(2 * Math.PI * k / 8);
            int disc = 0, lit = 0;
            StringBuilder sb = new StringBuilder();
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    double dx = (x + 0.5) - 7.5, dy = (y + 0.5) - 7.5;
                    double r = Math.sqrt(dx * dx + dy * dy);
                    if (r < 5.4) {
                        disc++;
                        if (isLit(dx, cos)) { lit++; sb.append('#'); } else sb.append('.');
                    } else sb.append(' ');
                }
                sb.append('\n');
            }
            System.out.println("k=" + k + "  cos=" + String.format("%+.3f", cos)
                    + "  月盘像素=" + disc + "  受光=" + lit + "  占比=" + String.format("%.3f", (double) lit / disc));
            System.out.print(sb);
            System.out.println();
        }
    }
}
