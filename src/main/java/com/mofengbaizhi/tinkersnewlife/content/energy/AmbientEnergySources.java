package com.mofengbaizhi.tinkersnewlife.content.energy;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * <b>环境能量来源的小注册表</b> —— §545 起从"<b>单选一条</b>"改成"<b>多条并行累加</b>"✓。
 *
 * <h2>改了什么（前后对比）</h2>
 * <pre>
 *   §545 之前： pedestal → AmbientEnergySources.selected()  → 配置 pedestal_source 指定的那<b>一条</b> → 速率
 *   §545 之后： pedestal → AmbientEnergySources.eePerSecond(level, pos, simulate)
 *                          → 遍历<b>所有启用</b>的来源 → 逐条 eePerSecond(...) → <b>全部相加</b>
 * </pre>
 * 台座的调用点因此从"取一条再问它"变成"问注册表要总数"——台座<b>不需要</b>知道有哪几条来源 ✓。
 *
 * <h2>"启用"由什么决定（两条都要满足）</h2>
 * <ol>
 *   <li><b>配置里的来源清单</b> {@code elder_crystal.pedestal_source}：
 *       <ul>
 *         <li>{@code "*"}（<b>默认</b>）⇒ <b>所有</b>注册在册的来源都启用 ✓（§545 的五条并行 ✓）；</li>
 *         <li>写成逗号分隔的 id 清单 ⇒ <b>只</b>启用列出的那几条 ✓
 *             （例：{@code "light_level,plant"} ⇒ 关掉燃料/灵魂/玩家三条 ✓ 不想动那么多开关时的快捷方式 ✓）；</li>
 *         <li>⚠ <b>兼容旧档</b>：老配置里写的是单条 id（如 {@code "light_level"}）——按新语义它就变成
 *             "只启用亮度这一条"，正好是它原来在做的事 ✓ 不会突然多出四条把速度抬上去 ✓。</li>
 *       </ul></li>
 *   <li><b>每源自己的开关</b>（{@link AmbientEnergySource#id()} 对应的 {@code *_enabled}）✓
 *       关掉任一条 = 那条永远不进累加 ✓（哪怕清单里有它 ✓）。</li>
 * </ol>
 *
 * <h2>为什么不设总上限</h2>
 * 用户口径 §545：「<b>不设</b>总上限，每秒汲取多少就是多少，并行叠加」✓
 * ⇒ 这里<b>只做加法</b>，没有任何 clamp ✗（每源自己可以对内做限额，例如植物上限就是植物株数 ✓）。
 *
 * <h2>性能</h2>
 * 每秒每台座调用一次 ✓ 遍历前先取一次"启用清单"（{@link #activeSources()}，每条只在第一次取用时读一次配置，
 * 之后命中缓存 ✓）⇒ 稳态下就是一次 List 遍历 + 每条一个 {@code double} 累加 ✓
 * —— <b>不</b>每秒解析配置字符串 ✗、<b>不</b>每秒新建集合 ✗（热路径上零分配 ✓）。
 *
 * <h2>线程安全</h2>
 * 注册只应该发生在类初始化阶段（静态初始化 / mod 构造）✓ 之后就是只读查询 ✓
 * —— 台座每秒查一次、可能很多台座一起查 ⇒ 这里刻意<b>不加锁</b> ✗（LinkedHashMap 只读遍历是安全的 ✓）。
 */
public final class AmbientEnergySources {

    private AmbientEnergySources() {}

    /** 已注册的来源（按注册顺序 ✓ 便于手册稳定展示 ✓） */
    private static final Map<String, AmbientEnergySource> SOURCES = new LinkedHashMap<>();

    /** 默认来源（"写错会退回哪条"只在文档/调试里用 ✓） */
    private static final AmbientEnergySource DEFAULT = new LightLevelEnergySource();

    // 静态初始化：**顺序就是手册/config 注释里的顺序** ✓（亮度 → 植物 → 燃料 → 灵魂 → 玩家 ✓）
    static {
        register(DEFAULT);
        register(new PlantEnergySource());
        register(new TConFuelEnergySource());
        register(new SoulDeathEnergySource());
        register(new DemigodPlayerEnergySource());
    }

    /** 注册（或覆盖同 id 的）来源 ✓ */
    public static void register(AmbientEnergySource source) {
        if (source == null || source.id() == null) return;
        String id = normalize(source.id());
        if (id.isEmpty()) return;
        SOURCES.put(id, source);
        invalidateCache();   // 清单可能变了 ⇒ 下次重新解析（初始化阶段，代价可忽略 ✓）
        TinkersNewlife.LOGGER.debug("[魔力台座] 已注册环境能量来源 '{}'", id);
    }

    /** 按 id 取来源（不存在 ⇒ {@code null} ✓） */
    public static AmbientEnergySource byId(String id) {
        AmbientEnergySource source = SOURCES.get(normalize(id));
        return source == null ? null : source;
    }

    /** 所有已注册来源（只读 ✓ 顺序 = 注册顺序 ✓） */
    public static Collection<AmbientEnergySource> all() {
        return Collections.unmodifiableCollection(SOURCES.values());
    }

    /** 注册在册的 id 集合（只读快照 ✓ 手册/日志用 ✓） */
    public static Set<String> allIds() {
        return Collections.unmodifiableSet(new TreeSet<>(SOURCES.keySet()));
    }

    /** 默认来源（配置无关的那条 ✓ 手册里说明"清单写错会退回什么"时用它 ✓） */
    public static AmbientEnergySource defaultSource() {
        return DEFAULT;
    }

    /**
     * <b>§545 的核心：每秒能拿到的总 EE = 所有启用来源之和</b> ✓
     *
     * @param level    台座所在维度（服务端 ✓）
     * @param pos      台座所在坐标 ✓
     * @param simulate {@code true} = 只算不改（tooltip / 调试 ✓）；{@code false} = 台座每秒那一次真结算 ✓
     * @return 各启用来源的 <b>总和</b>；一条都没启用 / 都是 0 ⇒ {@code 0.0} ✓；<b>永不为负</b> ✓
     */
    public static double eePerSecond(Level level, BlockPos pos, boolean simulate) {
        if (level == null || pos == null) return 0.0D;
        List<AmbientEnergySource> active = activeSources();
        if (active.isEmpty()) return 0.0D;

        double total = 0.0D;
        for (int i = 0; i < active.size(); i++) {
            AmbientEnergySource source = active.get(i);
            double got = source.eePerSecond(level, pos, simulate);
            // NaN（`!(got > 0)` 盖住 NaN ✓）/ 负数（实现方违约 ✓）一律当 0，绝不让一条来源把总数污染成 NaN ✗
            if (got > 0.0D) total += got;
        }
        return total;
    }

    /** 只读版本（等价于 {@code simulate = true} ✓ tooltip / 玉 / 手册 / 调试用 ✓ 绝不改世界 ✓） */
    public static double peekEePerSecond(Level level, BlockPos pos) {
        return eePerSecond(level, pos, true);
    }

    /**
     * 旧接口：按配置取"<b>那一条</b>"来源。
     * <p>⚠ §545 后台座已经<b>不再</b>用它（改成 {@link #eePerSecond} 累加 ✓）——
     * 保留只是为了"想查某一条来源"的调试/附属模组别编译不过 ✓
     * （配置写成清单时，这里返回清单里的<b>第一条</b> ✓ 清单为空/写错 ⇒ 退回 {@link #DEFAULT} ✓）。
     */
    public static AmbientEnergySource selected() {
        List<AmbientEnergySource> active = activeSources();
        return active.isEmpty() ? DEFAULT : active.get(0);
    }

    // ============================================================
    //  "哪些来源启用"——解析 + 缓存
    // ============================================================

    /** 启用清单缓存（{@code null} = 还没算 / 被失效了 ✓） */
    private static volatile List<AmbientEnergySource> cache = null;

    /** 注册过东西 ⇒ 缓存作废 ✓（只在初始化阶段发生 ✓） */
    private static void invalidateCache() {
        cache = null;
    }

    /**
     * 当前启用的来源清单（<b>不可变副本</b> ✓ 顺序 = 注册顺序 ✓）。
     * <p>第一次调用时解析配置并缓存 ✓ —— 配置文件被玩家改了以后由 {@code ModConfig} 的配置重载回调调
     * {@link #invalidateCache()} 作废 ✓（见 {@code ModConfig} 的 {@code onLoad} ✓）。
     */
    public static List<AmbientEnergySource> activeSources() {
        List<AmbientEnergySource> cached = cache;
        if (cached != null) return cached;
        synchronized (AmbientEnergySources.class) {
            if (cache == null) {
                List<AmbientEnergySource> resolved = Collections.unmodifiableList(resolveActive());
                if (resolved.isEmpty()) warnEmptyOnce();   // 只提醒一次 ✓ 不刷屏 ✓
                cache = resolved;
            }
            return cache;
        }
    }

    /** 真正去解析配置（只在缓存失效时跑 ✓） */
    private static List<AmbientEnergySource> resolveActive() {
        String raw = ModConfig.pedestalSourceId();
        boolean all = raw == null || raw.isBlank() || raw.trim().equals("*");

        List<AmbientEnergySource> out = new ArrayList<>(SOURCES.size());
        if (all) {
            for (AmbientEnergySource source : SOURCES.values()) {
                if (ModConfig.energySourceEnabled(source.id())) out.add(source);
            }
        } else {
            for (String piece : raw.split(",")) {
                String id = normalize(piece);
                if (id.isEmpty() || id.equals("*")) continue;
                AmbientEnergySource source = SOURCES.get(id);
                if (source == null) {
                    warnUnknownOnce(id);
                    continue;
                }
                if (!ModConfig.energySourceEnabled(id)) continue;   // 清单里有但开关关了 ⇒ 跳过 ✓（不告警 ✗）
                if (!out.contains(source)) out.add(source);
            }
        }
        return out;
    }

    /** 配置重载（玩家在游戏里改了 config）⇒ 作废缓存 ✓ 下次读配置拿到新清单 ✓ */
    public static void onConfigReload() {
        invalidateCache();
        warnedIds.clear();
    }

    private static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    // ---- "未知 id / 全被关掉只提醒一次"（避免每秒一条日志把日志刷爆 ✗）----
    private static final Set<String> warnedIds = Collections.synchronizedSet(new java.util.HashSet<>());

    private static void warnUnknownOnce(String id) {
        if (!warnedIds.add("unknown:" + id)) return;
        TinkersNewlife.LOGGER.warn("[魔力台座] 配置里的 pedestal_source 含有未知来源 '{}'，已忽略它。"
                + "可用来源：{}（\"*\" = 全部启用）", id, allIds());
    }

    /** 给"清单解析出来一条都不剩"准备的提示（由一个静态钩子在首次结算时调 ✓ 只报一次 ✓） */
    static void warnEmptyOnce() {
        if (!warnedIds.add("empty")) return;
        TinkersNewlife.LOGGER.warn("[魔力台座] pedestal_source='{}' 解析后一条来源都没启用"
                + "（清单/开关都关掉了？）⇒ 台座不会充能。可用来源：{}（\"*\" = 全部启用）",
                ModConfig.pedestalSourceId(), allIds());
    }
}
