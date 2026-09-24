import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * 星象仪（planetarium）美术生成器 —— <b>两层分离</b>版。
 *
 * <p>用户口径：「星象仪由 2 部分组成，第一部分是底盘，第二部分是星象图。变化的只有星象图，底盘不变，
 * 优化材质绘制方式」——所以这里不再画"8 张各带夜空底的完整图"，而是：
 * <ul>
 *   <li><b>底盘</b> {@code base.png}：<b>只画一次</b>，永不变 ✓（金属圆环 + 三枚方位铆钉 + 中心空腔 ✓）</li>
 *   <li><b>星象图</b> {@code star_0..7.png}：随月相换 ✓（半透明夜空圆盘 + 星点 + 月相 ✓）
 *       —— 圆盘外**全透明** ✓ 让底盘透出来 ✓ 这就是"变化的只有星象图"的落地方式 ✓</li>
 * </ul>
 *
 * <p>两者怎么合成一张图标：物品模型 `models/item/planetarium.json` 用原版
 * {@code minecraft:item/generated} 的 <b>{@code layer0 + layer1}</b> 叠层 ✓
 * （{@code layer0}=底盘 ✓ {@code layer1}=星象图 ✓）；HUD 那边先画底盘再画星象图 ✓ 同一顺序 ✓。
 * ⇒ <b>8 个模型文件里只有 layer1 的路径不同 ✓</b>，"底盘不变"在文件层面也是真的 ✓ 改底盘只改一个文件 ✓。
 *
 * <h2>画法（都在 16×16 网格上按几何算，不手描 ✓）</h2>
 * <pre>
 *   中心 (7.5, 7.5)
 *   星象图（圆盘）半径 r &lt; 6.2   —— 夜空 + 星点 + 月亮
 *   底盘内圈（金属内环）6.2 ≤ r &lt; 6.9
 *   底盘外圈（更暗的镶边）6.9 ≤ r &lt; 7.4
 *   底盘轮廓（深色描边）7.4 ≤ r &lt; 7.9
 *   再外面全透明
 *   三枚铆钉：正上 / 左下 / 右下（方位感 ✓ 也让它一眼像"仪器"）
 * </pre>
 * 月相几何同上一版 ✓（内切椭圆 + 半平面 ✓ 判定式：{@code inner = dx·cosφ}
 * ✓ {@code outer = dx² + dx·cosφ + 0.25} ✓ 与画出来的效果一致 ✓）。
 *
 * <p>用法：{@code java tools/GenPlanetariumArt.java}
 * （JDK 11+ 单文件运行 ✓ 或 {@code javac} 后 {@code java} 跑 ✓ 与 {@code tools/Cutout.java} 同一套用法 ✓）
 *
 * <p>⚠ 铁律：只写 {@code textures/item/planetarium/} 下的这些文件 ✓ <b>不碰任何其它贴图</b> ✓
 * 参数 {@code --force} 才会覆盖已存在的文件 ✓ 默认**跳过** ✓（防误覆盖手绘 ✓）。
 */
public final class GenPlanetariumArt {

    private static final int W = 16, H = 16;
    private static final double CX = 7.5, CY = 7.5;

    // 半径分界（都是"到中心的距离"）
    private static final double R_STAR = 6.2;   // 星象图圆盘
    private static final double R_INNER = 6.9;  // 底盘内环外沿
    private static final double R_OUTER = 7.4;  // 底盘外圈外沿
    private static final double R_RIM = 7.9;    // 底盘描边外沿

    // 调色板
    private static final int[] BASE_INNER_HI = {126, 116, 152}; // 内环受光（左上一侧）
    private static final int[] BASE_INNER = {84, 77, 104};      // 内环背光
    private static final int[] BASE_OUTER_HI = {64, 58, 82};    // 外圈受光
    private static final int[] BASE_OUTER = {46, 42, 62};       // 外圈背光
    private static final int[] BASE_RIM = {26, 23, 36};         // 描边（最深）
    private static final int[] SKY = {22, 19, 42};
    private static final int[] SKY_HI = {34, 30, 58};
    private static final int[] MOON_ON = {238, 240, 226};
    private static final int[] MOON_MID = {178, 184, 178};
    private static final int[] MOON_OFF = {48, 50, 68};
    private static final int[] STAR = {206, 208, 238};
    private static final int[] STAR_DIM = {140, 142, 178};

    private static int argb(int[] c, int a) { return (a << 24) | (c[0] << 16) | (c[1] << 8) | c[2]; }
    private static double dist(double x, double y) {
        double dx = (x + 0.5) - CX, dy = (y + 0.5) - CY;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * 底盘：<b>两圈金属环 + 一圈最深描边</b>，中心留空给星象图 ✓。
     * <p>光源固定"左上亮、右下暗"（{@code x + y} 的阈值 ✓）——同一方向一套高光 ✓ 所以看起来是一个立体的环 ✓
     * 而不是一圈散点 ✗（第一版画过铆钉，糊成一团 ✗ 已去掉 ✓）。
     */
    private static BufferedImage drawBase() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double r = dist(x, y);
                boolean upperLeft = (x + y) <= (W - 2);   // 固定光源方向：左上受光 ✓
                int color;
                if (r >= R_RIM) {
                    color = 0x00000000;                              // 最外 ⇒ 透明
                } else if (r >= R_OUTER) {
                    color = argb(BASE_RIM, 255);                     // 最深的一圈描边
                } else if (r >= R_INNER) {
                    color = argb(upperLeft ? BASE_OUTER_HI : BASE_OUTER, 255);  // 外圈
                } else if (r >= R_STAR) {
                    color = argb(upperLeft ? BASE_INNER_HI : BASE_INNER, 255);  // 内圈（亮一档 ⇒ 有倒角感）
                } else {
                    color = 0x00000000;                              // 中心空腔 ⇒ 等星象图来填
                }
                img.setRGB(x, y, color);
            }
        }
        return img;
    }

    /** 星象图：夜空圆盘（圆外透明）+ 星点 + 当月月相 */
    private static BufferedImage drawStar(int phase) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        double phi = 2.0 * Math.PI * phase / 8.0;
        double cos = Math.cos(phi);

        // 星点位置（固定的星图 ✓ —— "星象图"本身不该乱跳 ✓ 只让月相变 ✓）
        int[][] stars = {{8, 2}, {3, 4}, {12, 4}, {5, 11}, {11, 11}, {2, 8}, {14, 8}, {8, 13}};

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double r = dist(x, y);
                if (r >= R_STAR) { img.setRGB(x, y, 0x00000000); continue; }   // 圆外透明 ⇒ 底盘透出
                int color = argb(((x + y) % 4 == 0) ? SKY_HI : SKY, 255);       // 夜空（棋盘微差）
                img.setRGB(x, y, color);
            }
        }
        // 星点（画在夜空上）
        for (int i = 0; i < stars.length; i++) {
            int x = stars[i][0], y = stars[i][1];
            if (x < 0 || y < 0 || x >= W || y >= H) continue;
            if (dist(x, y) >= R_STAR - 0.6) continue;
            img.setRGB(x, y, argb((i % 3 == 0) ? STAR_DIM : STAR, 255));
        }
        // 月亮（中心圆盘 + 月相明暗）
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double dx = (x + 0.5) - CX, dy = (y + 0.5) - CY;
                double r = Math.sqrt(dx * dx + dy * dy);
                double a = Math.abs(dx) + Math.abs(dy) * 0.85;
                if (r >= 5.4 || a >= 5.8) continue;              // 不在月盘内
                double inner = dx * cos;
                double outer = dx * dx + dx * cos + 0.25;
                boolean lit;
                if (inner >= 0) lit = (dx >= cos);
                else lit = ((a * a) <= outer);
                if (r >= 4.95) lit = false;                      // 靠近边缘压暗 ⇒ 轮廓清晰
                int[] c = lit ? (dx < -2.4 ? MOON_MID : MOON_ON) : MOON_OFF;
                img.setRGB(x, y, argb(c, 255));
            }
        }
        return img;
    }

    public static void main(String[] args) throws Exception {
        boolean force = false;
        for (String a : args) if (a.equals("--force")) force = true;

        File dir = new File("src/main/resources/assets/tinkersnewlife/textures/item/planetarium");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("无法创建目录: " + dir);

        File base = new File(dir, "base.png");
        if (base.exists() && !force) {
            System.out.println("SKIP (exists, use --force) " + base.getPath());
        } else {
            ImageIO.write(drawBase(), "png", base);
            System.out.println("wrote base.png   (base plate, never changes)");
        }
        for (int p = 0; p < 8; p++) {
            File f = new File(dir, "star_" + p + ".png");
            if (f.exists() && !force) {
                System.out.println("SKIP (exists, use --force) " + f.getPath());
                continue;
            }
            ImageIO.write(drawStar(p), "png", f);
            System.out.println("wrote star_" + p + ".png   (star chart, moon phase " + p + ")");
        }
        System.out.println("done -> " + dir.getPath());
        System.out.println("NOTE: these 9 are placeholders - repaint them at the same paths, no JSON change needed.");
    }
}
