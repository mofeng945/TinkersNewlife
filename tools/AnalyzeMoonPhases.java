import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** 量一下每张星象图：亮面像素数 + 亮面重心在哪一侧，用来核对"图名 ↔ 实际月相" */
public class AnalyzeMoonPhases {
    public static void main(String[] args) throws Exception {
        File dir = new File("src/main/resources/assets/tinkersnewlife/textures/item/planetarium");
        String[] names = {"满月", "亏凸月", "下弦月", "残月", "新月", "娥眉月", "上弦月", "盈凸月"};
        System.out.printf("%-4s %-8s %-7s %-7s %-9s %-8s %s%n", "相位", "名字", "月盘像素", "亮面", "亮面占比", "亮面重心X", "判定");
        for (int p = 0; p < 8; p++) {
            BufferedImage img = ImageIO.read(new File(dir, "star_" + p + ".png"));
            int disc = 0, lit = 0; double sumX = 0;
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int c = img.getRGB(x, y);
                if ((c >>> 24) == 0) continue;
                int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
                boolean isMoon = !(b > r + 20 && b > g + 20);      // 夜空偏蓝紫，月亮是灰白
                if (!isMoon) continue;
                boolean dark = (r + g + b) < 260;                  // 暗面
                disc++;
                if (!dark) { lit++; sumX += x; }
            }
            double ratio = disc == 0 ? 0 : (double) lit / disc;
            double cx = lit == 0 ? -1 : sumX / lit;
            String side = cx < 0 ? "无" : (cx < 7.5 ? "左" : (cx > 7.6 ? "右" : "居中"));
            String verdict;
            if (ratio > 0.92) verdict = "整个月盘都亮 ⇒ 满月";
            else if (ratio < 0.10) verdict = "几乎全暗 ⇒ 新月";
            else if (ratio > 0.5) verdict = (side.equals("右") ? "右亮大半 ⇒ 盈凸" : "左亮大半 ⇒ 亏凸");
            else if (ratio < 0.5) verdict = (side.equals("右") ? "右亮细弯 ⇒ 娥眉(盈)" : "左亮细弯 ⇒ 残月(亏)");
            else verdict = (side.equals("右") ? "右半亮 ⇒ 上弦" : "左半亮 ⇒ 下弦");
            System.out.printf("%-4d %-8s %-9d %-8d %-10s %-10s %s%n", p, names[p], disc, lit,
                    String.format("%.2f", ratio), String.format("%.1f(%s)", cx, side), verdict);
        }
    }
}
