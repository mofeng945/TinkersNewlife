package com.mofengbaizhi.tinkersnewlife.content.energy;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.config.ModConfig;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * <b>环境能量来源的小注册表</b>（可插拔 ✓ 台座每秒问它"现在该用哪个来源"✓）。
 *
 * <h2>为什么不用 Forge 的注册表 / 数据包</h2>
 * 来源是<b>纯逻辑</b>（一段算法），不是数据 ✗ ⇒ 用不着 {@code DeferredRegister} 那一套；
 * 真正需要"数据驱动"的只有<b>速率与阈值</b>，那些已经全部在 {@link ModConfig} 的
 * {@code [elder_crystal]} 段里 ✓。所以这里就是一个绑定在类初始化上的小 Map ✓：
 * <ul>
 *   <li>{@link #register(AmbientEnergySource)} —— 注册/覆盖一个来源（同 id 后注册的赢 ✓）；</li>
 *   <li>{@link #byId(String)} —— 按 id 取（取不到 = {@code null} ✓ 不抛 ✗）；</li>
 *   <li>{@link #selected()} —— <b>按配置</b>{@code elder_crystal.pedestal_source} 取当前来源 ✓
 *       id 写错 / 来源没注册 ⇒ <b>安静地退回默认的亮度来源</b> ✓（绝不让台座因此罢工 ✗）；</li>
 *   <li>{@link #all()} —— 只读集合（手册/调试列出所有可用来源 ✓）。</li>
 * </ul>
 *
 * <h2>默认内容</h2>
 * 只有一条：{@link LightLevelEnergySource}，id {@code "light_level"}（用户口径：亮度越低越快 ✓）。
 * <p>⚠ id 一律小写比较（{@link #normalize}）⇒ 配置里写 {@code Light_Level} 也能命中 ✓。
 *
 * <h2>线程安全</h2>
 * 注册只应该发生在初始化阶段（静态初始化 / mod 构造）✓ 之后就是只读查询 ✓
 * —— 台座每秒查一次、可能很多台座一起查 ⇒ 这里刻意<b>不加锁</b> ✗ 也<b>不分配</b> ✗（LinkedHashMap 只读遍历是安全的 ✓）。
 */
public final class AmbientEnergySources {

    private AmbientEnergySources() {}

    /** 已注册的来源（按注册顺序 ✓ 便于手册稳定展示） */
    private static final Map<String, AmbientEnergySource> SOURCES = new LinkedHashMap<>();

    /** 默认来源：配置写错 / 找不到时用它 ✓（用户口径的"亮度越低越快"✓） */
    private static final AmbientEnergySource DEFAULT = new LightLevelEnergySource();

    static {
        register(DEFAULT);
    }

    /** 注册（或覆盖同 id 的）来源 ✓ */
    public static void register(AmbientEnergySource source) {
        if (source == null || source.id() == null) return;
        String id = normalize(source.id());
        if (id.isEmpty()) return;
        SOURCES.put(id, source);
        TinkersNewlife.LOGGER.debug("[魔力台座] 已注册环境能量来源 '{}'", id);
    }

    /** 按 id 取来源（不存在 ⇒ {@code null} ✓） */
    public static AmbientEnergySource byId(String id) {
        AmbientEnergySource source = SOURCES.get(normalize(id));
        return source == null ? null : source;
    }

    /**
     * 当前生效的来源 = 配置 {@code elder_crystal.pedestal_source} 指定的那个 ✓
     * <p>配置 id 未知（写错/来源没注册）⇒ 退回 {@link #DEFAULT} 并只 WARN 一次（不刷屏 ✗ 也不罢工 ✗）。
     */
    public static AmbientEnergySource selected() {
        String id = normalize(ModConfig.pedestalSourceId());
        AmbientEnergySource source = SOURCES.get(id);
        if (source != null) return source;
        warnOnce(id);
        return DEFAULT;
    }

    /** 所有已注册来源（只读 ✓ 顺序 = 注册顺序 ✓） */
    public static Collection<AmbientEnergySource> all() {
        return Collections.unmodifiableCollection(SOURCES.values());
    }

    /** 默认来源（配置无关的那个 ✓ 手册里说明"写错会退回哪个"时用它 ✓） */
    public static AmbientEnergySource defaultSource() {
        return DEFAULT;
    }

    private static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    // ---- "未知 id 只提醒一次"（避免每秒一条日志把日志刷爆 ✗）----
    private static volatile String warnedId = null;

    private static void warnOnce(String id) {
        if (id.equals(warnedId)) return;
        warnedId = id;
        TinkersNewlife.LOGGER.warn("[魔力台座] 配置里的 pedestal_source='{}' 没有对应的来源，已退回默认的 '{}'。"
                        + "可用来源：{}", id, DEFAULT.id(),
                SOURCES.keySet().toString());
    }
}
