import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * 生成墨默对话用的**九宫格圆角气泡**贴图（纯色版 ✓ 用户先用着 ✓ 以后有手绘九宫格直接换文件即可 ✓）。
 *
 * <p>九宫格（9-slice）贴图规格：**圆角半径 R**，图整体 = {@code 2R+1} × {@code 2R+1} ⇒ 中间那 1 行/1 列**可拉伸** ✓
 * ⇒ 任意长宽的气泡都能用它拼出来 ✓ 四角不拉伸 ⇒ 圆角永远不变形 ✓。
 *
 * <p>输出（**新文件** ✓ 不覆盖任何既有贴图 ✓）：
 * <ul>
 *   <li>{@code bubble_momo.png} —— 墨默说的话（奶白 ✓ 深色字看得清 ✓）；</li>
 *   <li>{@code bubble_option.png} —— 选项按钮（略深一档 ✓ 与对话气泡区分 ✓）。</li>
 * </ul>
 *
 * <p>用法：{@code java tools/GenMomoBubble.java}（JDK 11+ 单文件运行 ✓ 不需要编译 ✓）
 */
public final class GenMomoBubble {

    public static void main(String[] args) throws Exception {
        String dir = args.length > 0 ? args[0]
                : "src/main/resources/assets/tinkersnewlife/textures/gui/momo";
        int r = 9;                                  // 圆角半径
        int size = r * 2 + 1;                       // 19×19：中间 1 行/列可拉伸

        // 墨默的气泡：奶白底 + 暖灰描边 + 顶部一道极淡高光
        write(new File(dir, "bubble_momo.png"), size, r,
                new Color(0xF7, 0xF3, 0xE7), new Color(0x8C, 0x7F, 0x63), new Color(0xFF, 0xFF, 0xFF, 90));
        // 选项按钮：略深一档的米灰底（悬停时我会在代码里再叠一层亮色）
        write(new File(dir, "bubble_option.png"), size, r,
                new Color(0xE6, 0xDF, 0xCC), new Color(0x8C, 0x7F, 0x63), new Color(0xFF, 0xFF, 0xFF, 70));

        System.out.println("done: " + dir + "  (" + size + "x" + size + ", radius " + r + ")");
    }

    private static void write(File out, int size, int r, Color fill, Color border, Color gloss)
            throws Exception {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // 底：圆角矩形铺满整张图（内缩 0.75px 给描边留位置）
        g.setColor(fill);
        g.fillRoundRect(0, 0, size - 1, size - 1, r * 2, r * 2);
        // 顶部高光（只在最上一行，拉伸时不会花）
        g.setColor(gloss);
        g.fillRect(r, 1, size - r * 2 - 1, 1);
        // 描边
        g.setStroke(new BasicStroke(1.0f));
        g.setColor(border);
        g.drawRoundRect(0, 0, size - 1, size - 1, r * 2, r * 2);

        g.dispose();
        File parent = out.getParentFile();
        if (parent != null) parent.mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("  " + out.getName() + "  " + img.getWidth() + "x" + img.getHeight());
    }
}
