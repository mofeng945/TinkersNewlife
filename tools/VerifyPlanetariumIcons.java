import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 星象仪**图标选择**核对器 —— 离线模拟原版 {@code ItemProperties} + 模型 {@code overrides} 的选取规则：
 *
 * <pre>
 *   谓词值 v = 月相 / 10
 *   从上到下遍历 overrides，取【阈值 ≤ v】的最后一个（原版语义：列表后面的覆盖前面的）
 *   一条都没命中 ⇒ 用 base 层的 layer1
 * </pre>
 * 期望：月相 0 ⇒ star_0（满月 ✓ 走 base）· 1 ⇒ star_1 · … · 7 ⇒ star_7 ✓ 八张**张张可达** ✓。
 *
 * <p>⚠ 这个核对器就是冲着 §621 那个 bug 建的：旧谓词 {@code (月相+1)/10} 让月相 0 算出 0.1
 * ⇒ 命中第一条 override ⇒ 满月显示成 star_1、而 star_0 永不出现 ✗。
 */
public class VerifyPlanetariumIcons {

    public static void main(String[] args) throws Exception {
        String modelsDir = "src/main/resources/assets/tinkersnewlife/models/item/";
        String root = new String(Files.readAllBytes(new File(modelsDir, "planetarium.json").toPath()), StandardCharsets.UTF_8);

        // 极简解析：只取 layer1 与 overrides 里的 [阈值, 模型名]（保证顺序 = 文件顺序 ✓ 顺序对选取规则至关重要 ✓）
        String baseLayer1 = extract(root, "\"layer1\"");
        java.util.List<double[]> thresholds = new java.util.ArrayList<>();
        java.util.List<String> modelNames = new java.util.ArrayList<>();
        int idx = 0;
        while (true) {
            int p = root.indexOf("tinkersnewlife:planetarium\"", idx);
            int t = root.indexOf("\"tinkersnewlife:planetarium\":", idx);
            int mpos = root.indexOf("\"model\":", idx);
            if (mpos < 0) break;
            int end = root.indexOf('\n', mpos);
            String line = root.substring(mpos, end < 0 ? root.length() : end);
            String model = line.substring(line.indexOf('"', line.indexOf(':') + 1) + 1);
            model = model.substring(0, model.lastIndexOf('"'));
            // 阈值在同一条 override 块里、"model" 之前
            int blockStart = root.lastIndexOf('{', mpos);
            String block = root.substring(blockStart, mpos);
            int c = block.lastIndexOf(':');
            double thr = Double.parseDouble(block.substring(c + 1).replaceAll("[^0-9.]", ""));
            thresholds.add(new double[]{thr});
            modelNames.add(model);
            idx = end < 0 ? root.length() : end;
        }

        System.out.println("base layer1 = " + baseLayer1 + "   共 " + thresholds.size() + " 条 override");
        System.out.println();
        System.out.printf("%-5s %-8s %-6s %-42s %s%n", "月相", "名字", "谓词值", "命中的模型(layer1)", "结果");
        String[] nm = {"满月", "亏凸月", "下弦月", "残月", "新月", "娥眉月", "上弦月", "盈凸月"};
        java.util.Set<String> hit = new java.util.HashSet<>();
        boolean allOk = true;
        for (int phase = 0; phase < 8; phase++) {
            double v = phase / 10.0;
            String chosen = baseLayer1;            // 没命中 override ⇒ base 层
            for (int i = 0; i < thresholds.size(); i++) {
                if (thresholds.get(i)[0] <= v + 1e-9) chosen = modelNames.get(i);   // 后面的覆盖前面的 ✓
            }
            hit.add(chosen);
            String expect = "star_" + phase;
            boolean ok = chosen.endsWith(expect);
            if (!ok) allOk = false;
            System.out.printf("%-5d %-8s %-6.2f %-42s %s%n", phase, nm[phase], v, chosen, ok ? "OK" : "★错(应为 " + expect + ")");
        }
        System.out.println();
        System.out.println("八张里被命中的不同模型数 = " + hit.size() + " / 8 " + (hit.size() == 8 ? "⇒ 张张可达 ✓" : "⇒ ★有图永远显示不到!"));
        System.out.println(allOk && hit.size() == 8 ? ">>> ALL OK" : ">>> 还有问题");
    }

    /** 从 JSON 文本里取 "<key>": "<值>" 的值 */
    private static String extract(String text, String key) {
        int i = text.indexOf(key);
        int s = text.indexOf('"', text.indexOf(':', i) + 1);
        int e = text.indexOf('"', s + 1);
        return text.substring(s + 1, e);
    }
}
