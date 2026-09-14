package com.mofengbaizhi.tinkersnewlife.content.curse.technique;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 拟造蓝本·<b>兼容判定</b>。
 *
 * <h3>为什么需要它</h3>
 * 蓝本代理把 Item API 全量转发给目标物品实例，所以"目标物品自己的代码"照常工作；
 * 但<b>别的代码对"手上这个栈的物品类"做的 {@code instanceof} / {@code stack.is(X)} 判定转发不到</b>
 * （转发时 {@code stack.getItem()} 是蓝本物品）。最典型：
 * <ul>
 *   <li>匠魂工具：{@code stack.getItem() instanceof IModifiable} + 需要 {@code ToolDefinition}；</li>
 *   <li>AE2 部件：{@code instanceof IPartItem} 才能装在线上；</li>
 *   <li>Curios 饰品：需要 {@code ICurioItem} 才能佩戴。</li>
 * </ul>
 * 这类物品做成蓝本会"看着有、用不了"，所以默认<b>退回真副本</b>
 * （真副本由既有的"离手即散 / 容器立即清除 / 槽位拒绝"三层防线兜底，防自动化照样有效）。
 *
 * <h3>三层判定</h3>
 * <ol>
 *   <li><b>配置豁免</b> {@code blueprint_exempt}：物品 id / {@code @模组} / {@code #标签}（支持 {@code *} 通配）→ 真副本；</li>
 *   <li><b>已知接口</b>（内置 + 配置追加 {@code blueprint_risky_interfaces}）：实现即真副本；</li>
 *   <li><b>风险模组</b> {@code blueprint_risky_mods}：这些模组的物品默认真副本（可删条目放开）。</li>
 * </ol>
 * 想"什么都用蓝本"就把 {@code blueprint_risky_legacy} 设 false；想反过来"保守一点"就往
 * {@code blueprint_exempt} 里加东西 —— 都不用改代码。
 */
public final class BlueprintCompat {

    private BlueprintCompat() {}

    /** 内置：玩法依赖"物品自身类型/接口"的接口实现（按类名软依赖探测，模组不在就自动跳过） */
    /**
     * 已知接口：{@code {接口类名, 说明, 归属模组 id}}。
     * <p>⭐ 归属模组不在场时直接跳过探测（存在性判定走 {@code ModList}，不再靠 {@code Class.forName} 失败来推断）。
     */
    private static final String[][] KNOWN_INTERFACES = {
            {"slimeknights.tconstruct.library.tools.item.IModifiable",
                    "匠魂工具（需要 IModifiable + ToolDefinition）",
                    com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader.TCONSTRUCT},
            {"appeng.api.parts.IPartItem", "AE2 部件（需要 IPartItem 才能装机）",
                    com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader.AE2},
            {"top.theillusivec4.curios.api.type.capability.ICurioItem", "Curios 饰品（需要 ICurioItem 才能佩戴）",
                    com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader.CURIOS},
            {"top.theillusivec4.curios.api.type.capability.ICurio", "Curios 饰品（旧接口）",
                    com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader.CURIOS},
    };

    /**
     * <b>内置"必须真副本"的物品命名空间（永远生效，与配置的 {@code blueprint_risky_mods} 相加）</b>。
     *
     * <p>为什么不只靠配置：配置是<b>首次生成时写死的快照</b>，之后改默认值也不会同步到老整合包
     * （实测就踩到了：老配置里写的是 {@code @goetyrevelation}，而真正的物品命名空间是
     * {@code goety_revelation}——差一个下划线，整条规则等于没写，于是启示录的"神灵金盔甲"
     * 被做成了蓝本代理物：穿上没属性、模型也渲染不出来，看着就像一件"拟造空气"）。
     * 这类"能一眼看错、错了就完全失效"的关键项必须写死在代码里。
     *
     * <p>判定按物品命名空间（{@link ResourceLocation#getNamespace()}），不是 modid——
     * 因为有些"修复/扩展"模组把物品注册进被修复模组的命名空间
     * （例：RevelationFix 把 {@code goety_revelation:apocalyptium_*} 注册进 {@code goety_revelation}）。
     */
    private static final String[][] BUILTIN_RISKY_NAMESPACES = {
            {"goety_revelation", "启示录内容（含 RevelationFix 注册进该命名空间的物品：神灵金盔甲等）"},
            {"revelationfix", "启示录修复（RevelationFix 自身命名空间）"},
            {"ending_library", "终焉图书馆（护甲/物品依赖它自己的 API 与自定义渲染，蓝本转发不到）"},
    };

    /** 类名 → Class 缓存；缺失的类名记进 MISSING，避免反复 Class.forName */
    private static final Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> MISSING = ConcurrentHashMap.newKeySet();

    // ============================================================
    //  判定
    // ============================================================

    /** 该目标物品是否应当走<b>真副本</b> */
    public static boolean useLegacyFor(ItemStack target) {
        return effectiveReason(target) != null;
    }

    /**
     * 最终生效的"退真副本"原因（报告与实际行为都用它）：
     * <ol>
     *   <li>配置豁免 {@code blueprint_exempt} —— <b>无条件</b>退真副本；</li>
     *   <li>已知接口 / 风险模组 —— 仅当 {@code blueprint_risky_legacy=true}（默认）时退真副本。</li>
     * </ol>
     */
    @Nullable
    public static String effectiveReason(ItemStack target) {
        String exempt = exemptReason(target);
        if (exempt != null) return exempt;
        if (!riskyLegacyEnabled()) return null;
        return riskReason(target);
    }

    /** 配置豁免（物品 id / {@code @模组} / {@code #标签}，支持 {@code *} 通配）；null = 未豁免 */
    @Nullable
    public static String exemptReason(ItemStack target) {
        if (target == null || target.isEmpty()) return null;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(target.getItem());
        if (id == null) return null;
        if (matchesAny(exemptPatterns(), id, target.getItem())) return "配置豁免（blueprint_exempt）";
        return null;
    }

    /** 已知接口 / 风险模组命中原因；null = 可以安全做成蓝本 */
    @Nullable
    public static String riskReason(ItemStack target) {
        if (target == null || target.isEmpty()) return null;
        Item item = target.getItem();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null) return null;
        // 1) 已知接口（内置 + 配置追加）；内置项要求归属模组在场
        for (String[] entry : KNOWN_INTERFACES) {
            if (entry.length > 2 && !IntegrationLoader.isLoaded(entry[2])) continue;
            if (implementsInterface(item, entry[0])) return entry[1];
        }
        for (String iface : riskyInterfaces()) {
            if (iface == null || iface.isBlank()) continue;
            if (implementsInterface(item, iface)) return "配置标记的接口：" + iface;
        }
        // 2) 自带 Forge 能力（能量/流体/物品存储/饰品…）：能力是"按物品类"注册的，蓝本转发不了
        if (capabilityLegacy() && providesCapability(item)) {
            return "自带 Forge 能力（能量/流体/存储/饰品等，蓝本无法提供）";
        }
        // 3) 风险模组
        for (String mod : riskyMods()) {
            if (mod == null || mod.isBlank()) continue;
            String m = mod.trim().toLowerCase(Locale.ROOT);
            if (m.startsWith("@")) m = m.substring(1);
            if (m.equals(id.getNamespace().toLowerCase(Locale.ROOT))) {
                return "风险模组（blueprint_risky_mods：" + m + "）";
            }
        }
        // 4) 内置关键项（永远生效：老配置里的写法错了也照样退真副本）
        String ns = id.getNamespace().toLowerCase(Locale.ROOT);
        for (String[] entry : BUILTIN_RISKY_NAMESPACES) {
            if (entry[0].equals(ns)) return "内置关键项（" + entry[1] + "）";
        }
        return null;
    }

    private static boolean capabilityLegacy() {
        try {
            return com.mofengbaizhi.tinkersnewlife.config.ModConfig.CONSTRUCT_BLUEPRINT_CAPABILITY_LEGACY.get();
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * 该物品会不会给它自己的栈挂 Forge 能力（能量/流体/物品存储/饰品等）。
     * <p>
     * 探测方式：直接调用目标物品的 {@code initCapabilities(新栈, null)}，返回非 null 即说明
     * "这个物品的能力是它自己提供的"。我们的代理物品<b>不能</b>转发 {@code initCapabilities}
     * （能力按物品类注册，转发会破坏语义），所以这类物品做成蓝本会丢掉能力（如充能工具、储罐、背包）。
     */
    private static boolean providesCapability(Item item) {
        try {
            ItemStack probe = new ItemStack(item);
            return item.initCapabilities(probe, null) != null;
        } catch (Throwable t) {
            // 探测抛异常：保守当作"有特殊能力"，退真副本更安全
            return true;
        }
    }

    /** 该物品是否实现了指定接口（class 名，软依赖；接口不存在返回 false） */
    private static boolean implementsInterface(Item item, String className) {
        Class<?> c = resolve(className);
        if (c == null) return false;
        try {
            return c.isInstance(item);
        } catch (Throwable t) {
            return false;
        }
    }

    @Nullable
    private static Class<?> resolve(String className) {
        if (MISSING.contains(className)) return null;
        Class<?> cached = CLASS_CACHE.get(className);
        if (cached != null) return cached;
        try {
            Class<?> c = Class.forName(className);
            CLASS_CACHE.put(className, c);
            return c;
        } catch (Throwable t) {
            MISSING.add(className);
            return null;
        }
    }

    // ============================================================
    //  配置读取（全部容错：配置没加载也能跑）
    // ============================================================

    private static boolean riskyLegacyEnabled() {
        try {
            return com.mofengbaizhi.tinkersnewlife.config.ModConfig.CONSTRUCT_BLUEPRINT_RISKY_LEGACY.get();
        } catch (Throwable t) {
            return true;
        }
    }

    private static List<? extends String> exemptPatterns() {
        try {
            return com.mofengbaizhi.tinkersnewlife.config.ModConfig.CONSTRUCT_BLUEPRINT_EXEMPT.get();
        } catch (Throwable t) {
            return List.of();
        }
    }

    private static List<? extends String> riskyInterfaces() {
        try {
            return com.mofengbaizhi.tinkersnewlife.config.ModConfig.CONSTRUCT_BLUEPRINT_RISKY_INTERFACES.get();
        } catch (Throwable t) {
            return List.of();
        }
    }

    private static List<? extends String> riskyMods() {
        try {
            return com.mofengbaizhi.tinkersnewlife.config.ModConfig.CONSTRUCT_BLUEPRINT_RISKY_MODS.get();
        } catch (Throwable t) {
            return List.of();
        }
    }

    /** 模式匹配：{@code id} / {@code @模组} / {@code #标签}，支持 {@code *} 通配 */
    private static boolean matchesAny(List<? extends String> patterns, ResourceLocation id, Item item) {
        if (patterns == null || patterns.isEmpty()) return false;
        String idStr = id.toString().toLowerCase(Locale.ROOT);
        String ns = id.getNamespace().toLowerCase(Locale.ROOT);
        for (String raw : patterns) {
            if (raw == null) continue;
            String p = raw.trim().toLowerCase(Locale.ROOT);
            if (p.isEmpty()) continue;
            if (p.startsWith("@")) {
                String m = p.substring(1);
                if (!m.isEmpty() && (m.equals(ns) || globMatch(m, ns))) return true;
            } else if (p.startsWith("#")) {
                ResourceLocation tagId = ResourceLocation.tryParse(p.substring(1));
                if (tagId != null && item.builtInRegistryHolder()
                        .is(TagKey.create(Registries.ITEM, tagId))) {
                    return true;
                }
            } else if (globMatch(p, idStr)) {
                return true;
            }
        }
        return false;
    }

    private static boolean globMatch(String pattern, String value) {
        if (pattern.indexOf('*') < 0) return pattern.equals(value);
        String[] parts = pattern.split("\\*", -1);
        int pos = 0;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;
            int at = value.indexOf(part, pos);
            if (at < 0) return false;
            if (i == 0 && !pattern.startsWith("*") && at != 0) return false;
            pos = at + part.length();
        }
        if (!pattern.endsWith("*") && !value.endsWith(parts[parts.length - 1])) return false;
        return true;
    }

    // ============================================================
    //  报告（检测用）
    // ============================================================

    /** 报告结果：总数 / 可蓝本 / 退真副本 的分类统计 */
    public record Report(File file, int total, int blueprint, int legacy,
                         Map<String, Integer> reasonCounts, Map<String, Integer> riskyModCounts) {}

    /**
     * 遍历全部已注册物品，逐个体检"能不能做成蓝本"，把结果写到
     * {@code config/mofengbaizhi/construct/blueprint_report.txt}。
     */
    public static Report generateReport() {
        Map<String, Integer> reasons = new LinkedHashMap<>();
        Map<String, Integer> riskyMods = new LinkedHashMap<>();
        List<String> legacySamples = new ArrayList<>();
        java.util.Set<String> namespaces = new java.util.HashSet<>();
        AtomicInteger total = new AtomicInteger();
        AtomicInteger blueprint = new AtomicInteger();
        AtomicInteger legacy = new AtomicInteger();

        for (Item item : ForgeRegistries.ITEMS) {
            total.incrementAndGet();
            ItemStack probe = new ItemStack(item);
            String reason = effectiveReason(probe);
            if (reason == null) {
                blueprint.incrementAndGet();
            } else {
                legacy.incrementAndGet();
                reasons.merge(reason, 1, Integer::sum);
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                if (id != null) riskyMods.merge(id.getNamespace(), 1, Integer::sum);
                if (legacySamples.size() < 400) {
                    legacySamples.add(String.valueOf(id) + "    <- " + reason);
                }
            }
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            if (itemId != null) namespaces.add(itemId.getNamespace().toLowerCase(Locale.ROOT));
        }
        // ⭐ 自检：清单条目"匹配不到任何已注册物品命名空间"，**但去下划线/忽略大小写后能对上某个已加载模组**
        //    —— 这就是"id 写错了"（实测踩过：@goetyrevelation 少了条下划线，真正的命名空间是 goety_revelation，
        //    于是整条规则静默失效，启示录的神灵金盔甲被做成了蓝本代理物）。
        //    ⚠ 必须带上"模组已加载"这个前提：开发环境/精简包里那些模组根本没装，
        //    无条件报"匹配不到命名空间"会刷出一整屏误报（实测 dev 里刷了 43 条）。
        List<String> deadEntries = new ArrayList<>();
        for (String mod : riskyMods()) {
            if (mod == null || mod.isBlank()) continue;
            String m = mod.trim().toLowerCase(Locale.ROOT);
            if (m.startsWith("@")) m = m.substring(1);
            if (namespaces.contains(m)) continue;
            String normalized = m.replace("_", "");
            boolean typo = false;
            for (var info : loadedModIdsNormalized().entrySet()) {
                if (info.getKey().equals(normalized)) {
                    typo = true;
                    break;
                }
            }
            if (typo) deadEntries.add(mod.trim());
        }
        if (!deadEntries.isEmpty()) {
            TinkersNewlife.LOGGER.warn("[构筑] blueprint_risky_mods 里有 {} 条 id 写错了"
                    + "（模组在场、但物品命名空间对不上，条目会静默失效）：{}", deadEntries.size(), deadEntries);
        }
        File file = writeReport(total.get(), blueprint.get(), legacy.get(), reasons, riskyMods, legacySamples, deadEntries);
        return new Report(file, total.get(), blueprint.get(), legacy.get(), reasons, riskyMods);
    }

    /** 已加载模组 id（去掉下划线、小写）→ 原 id；仅用于"id 写错了"的自检 */
    private static Map<String, String> loadedModIdsNormalized() {
        Map<String, String> map = new LinkedHashMap<>();
        try {
            net.minecraftforge.fml.ModList list = net.minecraftforge.fml.ModList.get();
            if (list == null) return map;
            for (var info : list.getMods()) {
                String id = info.getModId();
                if (id == null || id.isEmpty()) continue;
                map.putIfAbsent(id.toLowerCase(Locale.ROOT).replace("_", ""), id);
            }
        } catch (Throwable ignored) {
            // 自检失败不影响报告生成
        }
        return map;
    }

    private static File writeReport(int total, int blueprint, int legacy,
                                    Map<String, Integer> reasons, Map<String, Integer> riskyMods,
                                    List<String> samples, List<String> deadEntries) {
        File dir = new File("config/mofengbaizhi/construct");
        if (!dir.exists() && !dir.mkdirs()) {
            TinkersNewlife.LOGGER.warn("[构筑] 无法创建报告目录 {}", dir.getPath());
        }
        File file = new File(dir, "blueprint_report.txt");
        StringBuilder sb = new StringBuilder();
        sb.append("拟造蓝本兼容报告（自动生成）\n");
        sb.append("========================================\n");
        sb.append("已注册物品总数: ").append(total).append('\n');
        sb.append("可安全做成蓝本: ").append(blueprint).append('\n');
        sb.append("退回真副本    : ").append(legacy).append('\n').append('\n');
        sb.append("-- 退回原因统计 --\n");
        reasons.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, Integer> e) -> e.getValue()).reversed())
                .forEach(e -> sb.append(String.format("%6d  %s%n", e.getValue(), e.getKey())));
        sb.append('\n').append("-- 按模组统计（退回数量，前 60）--\n");
        riskyMods.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, Integer> e) -> e.getValue()).reversed())
                .limit(60)
                .forEach(e -> sb.append(String.format("%6d  %s%n", e.getValue(), e.getKey())));
        sb.append('\n').append("-- 样例（前 400 条）--\n");
        for (String s : samples) {
            sb.append(s).append('\n');
        }
        if (!deadEntries.isEmpty()) {
            sb.append('\n').append("⚠ 以下 blueprint_risky_mods 条目 id 写错了（模组在场但物品命名空间对不上，规则已静默失效）：\n");
            for (String d : deadEntries) {
                sb.append("    ").append(d).append('\n');
            }
        }
        sb.append('\n').append("提示：想让某个物品/模组用蓝本 → 从 blueprint_exempt / blueprint_risky_mods 里删掉；\n");
        sb.append("      想无条件全用蓝本 → blueprint_risky_legacy = false。\n");
        try {
            Files.writeString(file.toPath(), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            TinkersNewlife.LOGGER.warn("[构筑] 写蓝本兼容报告失败: {}", e.toString());
        }
        return file;
    }
}
