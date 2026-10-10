package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * <b>fufu 皮肤表</b>（§1079 用户口径：「在材质文件夹中专门开一个文件夹用于存放 fumo 皮肤，
 * 每次运行时自动遍历所有皮肤并注册多个 fumo 物品」✓）。
 *
 * <h3>约定（写死在注释里，方便以后加皮肤 ✓）</h3>
 * <pre>
 *   src/main/resources/assets/tinkersnewlife/textures/fumo/&lt;皮肤名&gt;.png   ⇒ 物品 tinkersnewlife:fumo_&lt;皮肤名&gt;
 * </pre>
 * <ul>
 *   <li>每个 <b>64×64 玩家皮肤样式</b> 的 PNG ＝ 一个皮肤 ✓（与 {@code FumoMoBlockEntityRenderer}
 *       那套「用玩家模型渲染」的 UV 布局一致 ✓）；</li>
 *   <li>文件名 ＝ 皮肤名 ⇒ 物品 id ＝ {@code fumo_<皮肤名>} ✓ ⇒ 物品名走翻译键
 *       {@code item.tinkersnewlife.fumo_<皮肤名>} ✓（见 {@link FumoMoBaseItem#getDescriptionId()}）；</li>
 *   <li><b>默认皮肤 {@link #DEFAULT_SKIN}（＝{@code mo}）不在这个目录里</b> ✗ —— 它就是原来那只
 *       {@code fumo_mo}，贴图仍是 {@code textures/entity/momo_common.png} ✓
 *       ⇒ 现有物品/贴图/行为**零变化** ✓（目录里若放了 {@code mo.png} 也一律忽略，避免重复注册 ✗）。</li>
 * </ul>
 *
 * <h3>怎么"自动遍历"（jar 与开发环境两种形态 ✓）</h3>
 * <ol>
 *   <li>{@code ModList.get().getModFileById(MODID).getFile().findResource("assets","tinkersnewlife","textures","fumo")}
 *       ⇒ 拿到一个 NIO {@link Path} ✓（jar 包走 SecureJar 的虚拟文件系统、开发环境走磁盘目录，
 *       两者都支持 {@code Files.list} ✓）⇒ 直接列目录 ✓；</li>
 *   <li>同一 {@code IModFile#getFilePath()}：是目录就 {@code resolve} 进去列目录 ✓、
 *       是 jar 就 {@link ZipFile} 扫条目 ✓（第 ① 步在个别环境下拿不到目录时的兜底 ✓）；</li>
 *   <li>再兜底一次 {@code getProtectionDomain().getCodeSource()}（同样是"目录/jar"两分支 ✓）。</li>
 * </ol>
 * ⚠ 三条路**全部** try/catch 吞异常 ＋ 记 warn ✓ —— 目录不存在、jar 读不了、文件名不合法……
 * 任何情况都只会"少几个皮肤" ✗，<b>绝不允许把启动搞崩</b> ✗（用户硬规矩 ✓）。
 *
 * <h3>顺序与合法性</h3>
 * 用 {@link TreeSet} 收集 ⇒ <b>按名字排序</b> ⇒ 注册顺序/创造栏顺序稳定 ✓。
 * 只认 {@code .png} ✓；文件名**小写化并把非法字符换成 {@code _}** ⇒ 保证得到的 id 一定是合法
 * {@link ResourceLocation} ✓（中文/大写/空格的名字会被改写并记一行 warn 说明改成了什么 ✓）。
 */
public final class FumoMoSkins {

    /** 内置默认皮肤名（＝现有物品 {@code fumo_mo} 的那一段 ✓） */
    public static final String DEFAULT_SKIN = "mo";

    /** 皮肤目录（相对**模组根**：jar 里是条目前缀 ✓，开发环境里是 {@code build/resources/main} 下的相对路径 ✓） */
    public static final String SKIN_DIR = "assets/tinkersnewlife/textures/fumo";

    /** 默认皮肤用的贴图（原来那只 fumo 一直用的那张 ✓ 不许改 ✗） */
    public static final ResourceLocation DEFAULT_TEXTURE =
            new ResourceLocation(TinkersNewlife.MOD_ID, "textures/entity/momo_common.png");

    private static final Logger LOG = LoggerFactory.getLogger("TinkersNewlife/FumoMo");

    /** 扫描结果（只算一次 ✓ 名字已排序 ✓ 不含默认皮肤 ✓） */
    private static volatile List<String> scanned;

    private FumoMoSkins() {}

    /** 扫描到的皮肤名（已排序、已去重、已剔除默认皮肤 ✓）；只会在第一次调用时真的读盘/读 jar ✓ */
    public static List<String> scanned() {
        List<String> local = scanned;
        if (local == null) {
            synchronized (FumoMoSkins.class) {
                local = scanned;
                if (local == null) {
                    local = List.copyOf(scan());
                    scanned = local;
                }
            }
        }
        return local;
    }

    /** 皮肤名 ⇒ 物品注册名（{@code mo → fumo_mo}、{@code example → fumo_example} ✓） */
    public static String itemPath(String skin) {
        return "fumo_" + skin;
    }

    /** 皮肤名是否可用（合法且已知）—— 渲染时用它兜底 ✓ */
    public static boolean isKnown(String skin) {
        if (skin == null) return false;
        if (DEFAULT_SKIN.equals(skin)) return true;
        return scanned().contains(skin);
    }

    /**
     * 皮肤名 ⇒ 贴图位置 ✓。
     * <ul>
     *   <li>默认皮肤 ⇒ 原来那张 {@link #DEFAULT_TEXTURE} ✓（fumo_mo 外观零变化 ✓）；</li>
     *   <li>其它皮肤 ⇒ {@code tinkersnewlife:textures/fumo/<皮肤名>.png} ✓；</li>
     *   <li>名字不合法/未知（例如手改过的旧存档 NBT）⇒ <b>回退默认贴图</b> ✓ 不抛异常 ✓。</li>
     * </ul>
     */
    public static ResourceLocation texture(String skin) {
        if (DEFAULT_SKIN.equals(skin) || !isSafe(skin)) return DEFAULT_TEXTURE;
        try {
            return new ResourceLocation(TinkersNewlife.MOD_ID, "textures/fumo/" + skin + ".png");
        } catch (Throwable t) {
            return DEFAULT_TEXTURE;
        }
    }

    /** {@code IForgeItem#getArmorTexture} 要的是**路径字符串**（不是 ResourceLocation ✗） */
    public static String texturePath(String skin) {
        if (DEFAULT_SKIN.equals(skin) || !isSafe(skin)) {
            return TinkersNewlife.MOD_ID + ":textures/entity/momo_common.png";
        }
        return TinkersNewlife.MOD_ID + ":textures/fumo/" + skin + ".png";
    }

    // ============================================================
    //  扫描
    // ============================================================

    private static List<String> scan() {
        TreeSet<String> found = new TreeSet<>();
        int sources = 0;

        // ① Forge 官方入口：ModList ⇒ IModFile#findResource（jar / 开发目录统一成 NIO Path ✓）
        try {
            // 用 var 避免对 forgespi 类型写死 import（编译期更稳 ✓）
            var modFileInfo = ModList.get().getModFileById(TinkersNewlife.MOD_ID);
            if (modFileInfo != null) {
                var modFile = modFileInfo.getFile();
                if (modFile != null) {
                    try {
                        Path dir = modFile.findResource("assets", "tinkersnewlife", "textures", "fumo");
                        if (collectDir(dir, found)) sources++;
                    } catch (Throwable t) {
                        LOG.warn("[fufu] 皮肤目录 findResource 读取失败（继续试其它路）：{}", t.toString());
                    }
                    // ② 同一 IModFile 的物理路径：目录 ⇒ 直接列；jar ⇒ ZipFile 扫条目
                    try {
                        if (collectFromRoot(modFile.getFilePath(), found)) sources++;
                    } catch (Throwable t) {
                        LOG.warn("[fufu] 皮肤目录 getFilePath 读取失败（继续试其它路）：{}", t.toString());
                    }
                }
            }
        } catch (Throwable t) {
            LOG.warn("[fufu] 取 IModFile 失败（继续试其它路）：{}", t.toString());
        }

        // ③ 最后兜底：自身 code source（开发环境是 classes 目录 ⇒ 一般扫不到资源；jar 形态能扫到 ✓）
        try {
            java.security.CodeSource cs = FumoMoSkins.class.getProtectionDomain().getCodeSource();
            if (cs != null && cs.getLocation() != null) {
                if (collectFromRoot(Paths.get(cs.getLocation().toURI()), found)) sources++;
            }
        } catch (Throwable t) {
            LOG.warn("[fufu] code source 兜底扫描失败：{}", t.toString());
        }

        found.remove(DEFAULT_SKIN);   // 默认皮肤由内置那张贴图负责 ⇒ 不参与扫描（防止重复注册 ✗）
        if (found.isEmpty()) {
            LOG.warn("[fufu] 没扫到任何皮肤（目录 {}）⇒ 只注册内置默认皮肤 {} ✓", SKIN_DIR, DEFAULT_SKIN);
        } else {
            LOG.info("[fufu] 皮肤扫描完成：{} 个（可用来源 {} 处）：{}", found.size(), sources, found);
        }
        return new ArrayList<>(found);
    }

    /** 列目录（zip 文件系统的 Path 与磁盘目录都支持 Files.list ✓）⇒ 有真列出目录就返回 true */
    private static boolean collectDir(Path dir, TreeSet<String> out) throws Exception {
        if (dir == null || !Files.isDirectory(dir)) return false;
        try (Stream<Path> list = Files.list(dir)) {
            for (Path p : list.collect(Collectors.toList())) {
                try {
                    if (!Files.isRegularFile(p)) continue;
                    String skin = accept(p.getFileName().toString(), out);
                    if (skin != null) checkPngHeader(p, skin);
                } catch (Throwable t) {
                    LOG.warn("[fufu] 皮肤条目 {} 读取失败，已跳过：{}", p, t.toString());
                }
            }
        }
        return true;
    }

    /** 物理路径（目录 / jar 文件）⇒ 有真处理了就返回 true */
    private static boolean collectFromRoot(Path root, TreeSet<String> out) throws Exception {
        if (root == null) return false;
        if (Files.isDirectory(root)) {
            return collectDir(root.resolve(SKIN_DIR), out);
        }
        if (Files.isRegularFile(root)) {
            return collectZip(root, out);
        }
        return false;
    }

    /** jar 形态：直接扫 zip 条目（只认皮肤目录**第一层**的 .png ✓） */
    private static boolean collectZip(Path jar, TreeSet<String> out) throws Exception {
        String prefix = SKIN_DIR + "/";
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (!name.startsWith(prefix)) continue;
                String rest = name.substring(prefix.length());
                if (rest.isEmpty() || rest.indexOf('/') >= 0) continue;   // 只认第一层 ✓
                accept(rest, out);
            }
        }
        return true;
    }

    /**
     * 一个文件名 ⇒ 皮肤名（合法就加进集合，并返回最终皮肤名以便继续做 PNG 尺寸检查；否则返回 null ✓）。
     * <p>只认 {@code .png}（大小写不敏感 ✓）；名字小写化 ＋ 非法字符换成 {@code _} ✓。
     */
    private static String accept(String fileName, TreeSet<String> out) {
        if (fileName == null) return null;
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".png")) return null;
        String raw = lower.substring(0, lower.length() - 4);
        String skin = sanitize(raw);
        if (skin.isEmpty()) {
            LOG.warn("[fufu] 皮肤文件名 {} 无法得到合法名字 ⇒ 已跳过 ✗", fileName);
            return null;
        }
        if (!skin.equals(raw)) {
            LOG.warn("[fufu] 皮肤文件名 {} 不是合法资源名 ⇒ 用 {} 当皮肤名（物品 id 会是 {}）✓",
                    fileName, skin, itemPath(skin));
        }
        if (DEFAULT_SKIN.equals(skin)) return null;   // 内置默认皮肤 ⇒ 忽略（它有自己的贴图 ✓）
        out.add(skin);
        return skin;
    }

    /** 小写化 + 只留 {@code [a-z0-9_.-]}（其余换成 {@code _}）＋ 去掉首尾的 {@code _} */
    private static String sanitize(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-';
            sb.append(ok ? c : '_');
        }
        String s = sb.toString();
        while (s.startsWith("_")) s = s.substring(1);
        while (s.endsWith("_")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /** 名字是否已经是"安全检查过"的形态（渲染兜底用 ✓ 不合法一律回退默认贴图 ✓） */
    private static boolean isSafe(String skin) {
        if (skin == null || skin.isEmpty()) return false;
        for (int i = 0; i < skin.length(); i++) {
            char c = skin.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-';
            if (!ok) return false;
        }
        return true;
    }

    /**
     * 约定的皮肤是 <b>64×64 玩家皮肤样式</b> ⇒ 读 PNG 头（IHDR）核对一下尺寸 ✓：
     * 不是 64×64 只记一行 warn（谁想用别的尺寸也拦不住，但至少日志里有据可查 ✓），
     * 读不到就静静跳过 ✗（绝不因为贴图坏了影响启动 ✗）。
     */
    private static void checkPngHeader(Path p, String skin) {
        try (InputStream in = Files.newInputStream(p)) {
            byte[] head = in.readNBytes(24);
            if (head.length < 24) return;
            boolean png = head[0] == (byte) 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
            if (!png) {
                LOG.warn("[fufu] 皮肤 {} 不是 PNG（只有扩展名是 .png）⇒ 仍按皮肤注册，但渲染可能不对 ✗", skin);
                return;
            }
            int w = ((head[16] & 0xFF) << 24) | ((head[17] & 0xFF) << 16) | ((head[18] & 0xFF) << 8) | (head[19] & 0xFF);
            int h = ((head[20] & 0xFF) << 24) | ((head[21] & 0xFF) << 16) | ((head[22] & 0xFF) << 8) | (head[23] & 0xFF);
            if (w != 64 || h != 64) {
                LOG.warn("[fufu] 皮肤 {} 尺寸是 {}x{}（约定是 64x64 玩家皮肤样式）⇒ 已注册，但贴图可能错位 ✗", skin, w, h);
            }
        } catch (Throwable t) {
            LOG.warn("[fufu] 皮肤 {} 的 PNG 头读取失败（不影响注册）：{}", skin, t.toString());
        }
    }
}
