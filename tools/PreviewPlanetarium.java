import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** 临时预览：把 base.png 与 star_0..7.png 逐个叠合，排成一行放大输出（验证两层对得上） */
public class PreviewPlanetarium {
    public static void main(String[] args) throws Exception {
        File dir = new File("src/main/resources/assets/tinkersnewlife/textures/item/planetarium");
        int scale = 6, gap = 3;
        BufferedImage base = ImageIO.read(new File(dir, "base.png"));
        BufferedImage out = new BufferedImage(8 * (16 * scale + gap) + gap, 16 * scale + 2 * gap, BufferedImage.TYPE_INT_ARGB);
        // 深灰棋盘底，方便看出"透明"
        for (int y = 0; y < out.getHeight(); y++)
            for (int x = 0; x < out.getWidth(); x++)
                out.setRGB(x, y, (((x / 8) + (y / 8)) % 2 == 0) ? 0xFF303038 : 0xFF3A3A44);
        for (int p = 0; p < 8; p++) {
            BufferedImage star = ImageIO.read(new File(dir, "star_" + p + ".png"));
            BufferedImage cell = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int b = base.getRGB(x, y);
                int s = star.getRGB(x, y);
                cell.setRGB(x, y, ((s >>> 24) == 0) ? b : s);   // 星象图不透明处盖住底盘（与游戏里叠层一致）
            }
            int ox = gap + p * (16 * scale + gap), oy = gap;
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int c = cell.getRGB(x, y);
                if ((c >>> 24) == 0) continue;
                for (int dy = 0; dy < scale; dy++) for (int dx = 0; dx < scale; dx++)
                    out.setRGB(ox + x * scale + dx, oy + y * scale + dy, c);
            }
        }
        File o = new File("build/planetarium-preview/two_layer_x6.png");
        o.getParentFile().mkdirs();
        ImageIO.write(out, "png", o);
        System.out.println("preview -> " + o.getPath());
    }
}
