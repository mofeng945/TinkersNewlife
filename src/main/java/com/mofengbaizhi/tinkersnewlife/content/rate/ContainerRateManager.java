package com.mofengbaizhi.tinkersnewlife.content.rate;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ObjLongConsumer;

/**
 * <b>容器产率统计引擎</b>（§735 建 · §736「全优化」✓）—— 只提供<b>方法接口</b> ✓（无命令/界面 ✗）。
 *
 * <h2>口径（用户拍板 ✓ 一个字没改）</h2>
 * 整个维度的物品先按 {@code Item} 求和 ⇒ 与上一次比 ⇒ <b>净差 ÷ 统计时间</b> ✓；
 * 周期 <b>10 分钟</b>（{@link #INTERVAL_TICKS} ✓）✓；单位 <b>个/时</b> ✓；
 * 只对"两次都观测到"的来源求和（搬运抵消 ✓ / 卸载不产生假净减 ✓）。
 *
 * <h2>§736 性能优化（用户口径「全优化」✓ 逐条对应）</h2>
 * <ol>
 *   <li><b>分帧采样（治"卡"的关键 ✓）</b>：一轮不再挤在一个 tick 里 ✗，而是每 tick 处理
 *       {@link #TOTAL_SOURCE_BUDGET} 个来源、分若干 tick 扫完 ✓ ⇒ 尖峰摊平 ✓
 *       （10 分钟的指标容得下几秒的扫描窗口 ✓）；</li>
 *   <li><b>自适应预算</b>：某一片耗时 &gt; {@link #SLICE_WARN_MS} ms ⇒ 预算减半 ✓（最低
 *       {@link #MIN_SOURCE_BUDGET} ✓）；很快又逐步加回来 ✓；</li>
 *   <li><b>安全阀</b>：服务器平均 tick 超过 {@link #LAG_SKIP_TICK_MS} ms ⇒ 本轮<b>不推进</b> ✓
 *       （绝不雪上加霜 ✓ 恢复后自动继续 ✓，日志最多 10 分钟一行 ✓）；</li>
 *   <li><b>多维度错峰</b>：各维度首次排期相差 {@link #DIM_STAGGER_TICKS} tick ✓，
 *       且所有维度<b>共享</b>每 tick 的总预算 ✓ ⇒ 不会几个维度挤同一 tick ✓；</li>
 *   <li><b>来源缓存</b>：见 {@link VanillaContainerRateProvider}（按区块缓存能力与来源对象 ✓）；</li>
 *   <li><b>空来源哨兵</b>：空容器直接放共享的 {@link #EMPTY} ✓ 不建 map ✓ 不参与差分 ✓；</li>
 *   <li><b>免装箱</b>：快照/差分全用 fastutil {@code Object2LongOpenHashMap} ✓（大基地省一大截 GC ✓）；</li>
 *   <li><b>AE2 走缓存库存</b>：见 {@code integration/ae2/Ae2RateProvider} ✓
 *       （用 AE2 自己维护的 cached inventory ✓ 不再每轮全量重算 ✓）。</li>
 * </ol>
 *
 * <h2>⚠ 不做的事（都想过了 ✗）</h2>
 * 降频（口径就是 10 分钟 ✗）、"只扫变化过的容器"（读之前无法知道 ✗）、
 * <b>丢到异步线程</b>（读世界状态必须主线程 ✗✗ 异步读容器会崩 ✓）。
 * <p>唯一仍可能拖慢单片的：**单个来源特别大**（例如 AE2 几十万条目的巨型网络 ✗）——
 * {@code forEachStored} 是原子的 ✗ 无法再切 ✓ 只能靠"安全阀 + 慢片日志"兜底 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ContainerRateManager {

    /** 采样周期：<b>10 分钟</b> ✓（20 tick/秒 × 600 秒 ✓ 用户口径 ✓） */
    public static final int INTERVAL_TICKS = 12_000;

    /** 每 tick 处理的来源数上限（**所有维度共享** ✓ §736 第 4 条 ✓） */
    private static final int TOTAL_SOURCE_BUDGET = 256;
    /** 自适应预算下限（再慢也别降到 0 ✓ 否则永远扫不完 ✗） */
    private static final int MIN_SOURCE_BUDGET = 16;
    /** 单片耗时超过它 ⇒ 下次预算减半 ✓ */
    private static final long SLICE_WARN_MS = 10L;
    /** 服务器平均 tick 超过它 ⇒ 本轮不推进（安全阀 ✓） */
    private static final double LAG_SKIP_TICK_MS = 100.0D;
    /** 一整轮超过它就打一行 warn ✓ */
    private static final long SWEEP_WARN_MS = 1_000L;
    /** 多维度错峰间隔（每多一个维度往后推这么多 tick ✓） */
    private static final long DIM_STAGGER_TICKS = 200L;

    /** 空来源哨兵 ✓（共享的不可变空表 ✓ 不分配 ✓ 差分里做"两边都是它 ⇒ 跳过"的快路径 ✓） */
    private static final Object2LongMap<Item> EMPTY = Object2LongMaps.emptyMap();

    /** 各维度<b>已加载区块</b>（{@code ChunkEvent} 维护 ✓ —— MC 1.20.1 没有公开的"枚举已加载区块"接口 ✗） */
    private static final Map<ResourceKey<Level>, Set<Long>> LOADED_CHUNKS = new ConcurrentHashMap<>();

    /** 上一次采样：来源 id → (物品 → 数量) ✓（**只在内存** ✓ 不落盘 ✓） */
    private static final Map<ResourceKey<Level>, Map<String, Object2LongMap<Item>>> LAST_SNAPSHOT =
            new ConcurrentHashMap<>();

    /** 正在进行的采样（分帧 ✓） */
    private static final Map<ResourceKey<Level>, Sweep> SWEEPS = new ConcurrentHashMap<>();
    /** 各维度下一次开始采样的 tick ✓ */
    private static final Map<ResourceKey<Level>, Long> NEXT_SWEEP = new ConcurrentHashMap<>();
    /** 各维度当前预算 ✓ */
    private static final Map<ResourceKey<Level>, Integer> BUDGETS = new ConcurrentHashMap<>();
    /** 各维度上次采样的诊断 ✓ */
    private static final Map<ResourceKey<Level>, SampleStats> LAST_STATS = new ConcurrentHashMap<>();

    /** 来源提供方（原版容器恒在 ✓；AE2 / Mekanism 由 {@code IntegrationLoader} 按"模组在不在"挂进来 ✓） */
    private static final List<RateSourceProvider> PROVIDERS = new CopyOnWriteArrayList<>();
    private static final RateSourceProvider VANILLA = new VanillaContainerRateProvider();

    /** 安全阀日志节流 ✓（别刷屏 ✗） */
    private static long lastLagLogTick = Long.MIN_VALUE;

    private ContainerRateManager() {
    }

    // ============================================================
    //  注册（联动模组调这一个方法就够了 ✓）
    // ============================================================

    /** 注册一个来源提供方 ✓（由 {@code IntegrationLoader} 在"该模组已加载"的分支里调 ✓） */
    public static void registerProvider(RateSourceProvider provider) {
        if (provider == null) return;
        PROVIDERS.add(provider);
        TinkersNewlife.LOGGER.info("[产率] 已挂载来源提供方：{} ✓", provider.modId().isEmpty() ? "原版容器" : provider.modId());
    }

    // ============================================================
    //  事件：区块登记 / 服务器启动 / 定时推进
    // ============================================================

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        LOADED_CHUNKS.computeIfAbsent(level.dimension(), key -> ConcurrentHashMap.newKeySet())
                .add(event.getChunk().getPos().toLong());
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long chunkKey = event.getChunk().getPos().toLong();
        Set<Long> set = LOADED_CHUNKS.get(level.dimension());
        if (set != null) set.remove(chunkKey);
        // 该区块的来源缓存整张丢掉 ✓（缓存随生命周期存在 ⇒ 不需要弱引用也不会泄漏 ✓）
        VanillaContainerRateProvider.dropChunk(chunkKey);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // 换存档/重启 ⇒ 基线快照作废 ✓（统计数据本身在 SavedData 里 ✓ 会保留 ✓）
        LAST_SNAPSHOT.clear();
        SWEEPS.clear();
        NEXT_SWEEP.clear();
        BUDGETS.clear();
        LAST_STATS.clear();
        LOADED_CHUNKS.clear();
        VanillaContainerRateProvider.clearAll();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;
        long now = server.getTickCount();

        // ③ 安全阀：服务器已经在卡 ⇒ 这一 tick 什么都不做 ✓（恢复后自动继续 ✓）
        double avgTick = 0.0D;
        try {
            avgTick = server.getAverageTickTime();
        } catch (Throwable ignored) {
        }
        if (avgTick > LAG_SKIP_TICK_MS) {
            if (now - lastLagLogTick > INTERVAL_TICKS) {
                lastLagLogTick = now;
                TinkersNewlife.LOGGER.info("[产率] 服务器平均 tick {} ms 偏高 ⇒ 本轮采样暂停推进（恢复后自动继续 ✓）",
                        String.format(java.util.Locale.ROOT, "%.1f", avgTick));
            }
            return;
        }

        // ④ 多维度错峰：首次给每个维度排一个错开的时刻 ✓
        int index = 0;
        for (ServerLevel level : server.getAllLevels()) {
            try {
                if (NEXT_SWEEP.get(level.dimension()) == null) {
                    NEXT_SWEEP.put(level.dimension(), now + index * DIM_STAGGER_TICKS);
                }
            } catch (Throwable ignored) {
            }
            index++;
        }

        // ① 分帧推进：所有维度<b>共享</b>这一 tick 的总预算 ✓
        int remaining = TOTAL_SOURCE_BUDGET;
        for (ServerLevel level : server.getAllLevels()) {
            if (remaining <= 0) break;
            try {
                remaining -= tickDimension(level, now, remaining);
            } catch (Throwable t) {
                TinkersNewlife.LOGGER.warn("[产率] 维度 {} 采样出错（已跳过本轮）：{}",
                        level.dimension().location(), t.toString());
                SWEEPS.remove(level.dimension());
                NEXT_SWEEP.put(level.dimension(), now + INTERVAL_TICKS);
            }
        }
    }

    /** 推进一个维度的采样；返回本 tick 用掉的预算 ✓ */
    private static int tickDimension(ServerLevel level, long now, int available) {
        ResourceKey<Level> dimension = level.dimension();
        Sweep sweep = SWEEPS.get(dimension);
        if (sweep == null) {
            Long next = NEXT_SWEEP.get(dimension);
            if (next == null || now < next) return 0;
            sweep = startSweep(level);
            if (sweep.sources.isEmpty()) {
                NEXT_SWEEP.put(dimension, now + INTERVAL_TICKS);
                ContainerRateData.get(level).pushInterval(EMPTY);   // 空维度也推进一个区间 ✓（时间照样在走 ✓）
                LAST_STATS.put(dimension, new SampleStats(0, 0, 0, 0, loadedChunkCount(level), 0, 0L));
                return 0;
            }
            SWEEPS.put(dimension, sweep);
        }
        return advance(level, sweep, available);
    }

    /** 按预算扫一片 ✓ */
    private static int advance(ServerLevel level, Sweep sweep, int available) {
        ResourceKey<Level> dimension = level.dimension();
        int budget = Math.min(available, BUDGETS.getOrDefault(dimension, TOTAL_SOURCE_BUDGET));
        if (budget <= 0) return 0;

        long start = System.nanoTime();
        int used = 0;
        while (used < budget && sweep.index < sweep.sources.size()) {
            RateSource source = sweep.sources.get(sweep.index++);
            Object2LongOpenHashMap<Item> counts = new Object2LongOpenHashMap<>();
            ObjLongConsumer<ItemStack> collector = (stack, count) -> {
                if (count <= 0L || stack == null || stack.isEmpty()) return;
                counts.addTo(stack.getItem(), count);
            };
            try {
                source.forEachStored(collector);
            } catch (Throwable t) {
                sweep.failed++;
                continue;                                   // 单个来源炸了不影响整体 ✓
            }
            // ⑥ 空来源哨兵：不建 map ✓（大基地里空箱子很多 ✓）
            sweep.now.put(source.id(), counts.isEmpty() ? EMPTY : counts);
            used++;
        }
        long sliceNanos = System.nanoTime() - start;
        sweep.busyNanos += sliceNanos;

        // ② 自适应预算 ✓
        long sliceMs = sliceNanos / 1_000_000L;
        if (sliceMs > SLICE_WARN_MS) {
            int next = Math.max(MIN_SOURCE_BUDGET, budget / 2);
            if (next != budget) {
                BUDGETS.put(dimension, next);
                TinkersNewlife.LOGGER.info("[产率] 维度 {} 单片 {} ms（{} 个来源）⇒ 预算降到 {} ✓",
                        dimension.location(), sliceMs, used, next);
            }
        } else if (sliceMs * 4L < SLICE_WARN_MS && budget < TOTAL_SOURCE_BUDGET) {
            BUDGETS.put(dimension, Math.min(TOTAL_SOURCE_BUDGET, budget * 2));
        }

        if (sweep.index >= sweep.sources.size()) finishSweep(level, sweep);
        return used;
    }

    /** 一轮扫完：与上一次比 ⇒ 推入一个 10 分钟区间 ✓ */
    private static void finishSweep(ServerLevel level, Sweep sweep) {
        ResourceKey<Level> dimension = level.dimension();
        Map<String, Object2LongMap<Item>> previous = LAST_SNAPSHOT.get(dimension);
        int skipped = 0;
        Object2LongOpenHashMap<Item> delta = new Object2LongOpenHashMap<>();
        if (previous != null) {
            for (Map.Entry<String, Object2LongMap<Item>> entry : sweep.now.entrySet()) {
                Object2LongMap<Item> before = previous.get(entry.getKey());
                if (before == null) {
                    skipped++;                              // 新来源 ⇒ 只立基线 ✓ 不算产出 ✓
                    continue;
                }
                Object2LongMap<Item> after = entry.getValue();
                if (before == after) continue;              // 两边都是空哨兵 ⇒ 无变化 ✓ 快路径 ✓
                for (Object2LongMap.Entry<Item> one : after.object2LongEntrySet()) {
                    long d = one.getLongValue() - before.getLong(one.getKey());
                    if (d != 0L) delta.addTo(one.getKey(), d);
                }
                for (Object2LongMap.Entry<Item> one : before.object2LongEntrySet()) {
                    if (!after.containsKey(one.getKey())) delta.addTo(one.getKey(), -one.getLongValue());
                }
            }
            for (String id : previous.keySet()) {
                if (!sweep.now.containsKey(id)) skipped++;  // 卸载/被拆 ⇒ 整条跳过 ✓（消失不算净减 ✓）
            }
            ContainerRateData.get(level).pushInterval(delta);   // 落一个 10 分钟区间 ✓
        }
        LAST_SNAPSHOT.put(dimension, sweep.now);
        SWEEPS.remove(dimension);

        long now = level.getGameTime();
        NEXT_SWEEP.put(dimension, level.getServer() == null ? now + INTERVAL_TICKS
                : level.getServer().getTickCount() + INTERVAL_TICKS);

        long busyMs = sweep.busyNanos / 1_000_000L;
        LAST_STATS.put(dimension, new SampleStats(sweep.sources.size(), sweep.now.size(), skipped,
                sweep.failed, loadedChunkCount(level), delta.size(), busyMs));

        TinkersNewlife.LOGGER.info(
                "[产率] 维度 {} 采样完成：来源 {}（区块 {} / 失败 {} / 跳过 {}）⇒ 本次物品变动 {} 种，累计 {} ms{}",
                dimension.location(), sweep.sources.size(), loadedChunkCount(level), sweep.failed, skipped,
                delta.size(), busyMs, busyMs >= SWEEP_WARN_MS ? " ⚠ 偏慢，考虑减少来源或延长时间隔" : "");
    }

    // ============================================================
    //  采样入口
    // ============================================================

    /** 手动催一次（<b>会一口气跑完</b> ✓ 别在服务器卡的时候调 ✓；分帧那套是自动的 ✓） */
    public static void sample(ServerLevel level) {
        Sweep sweep = startSweep(level);
        int guard = 0;
        while (sweep.index < sweep.sources.size() && guard++ < 100_000) {
            advance(level, sweep, TOTAL_SOURCE_BUDGET);
        }
    }

    private static Sweep startSweep(ServerLevel level) {
        List<LevelChunk> chunks = loadedChunks(level);
        List<RateSource> sources = new ArrayList<>(VANILLA.sourcesFor(level, chunks));
        for (RateSourceProvider provider : PROVIDERS) {
            try {
                List<RateSource> more = provider.sourcesFor(level, chunks);
                if (more != null) sources.addAll(more);
            } catch (Throwable t) {
                TinkersNewlife.LOGGER.warn("[产率] 来源提供方 {} 报错（已跳过）：{}", provider.modId(), t.toString());
            }
        }
        return new Sweep(sources);
    }

    private static List<LevelChunk> loadedChunks(ServerLevel level) {
        Set<Long> keys = LOADED_CHUNKS.get(level.dimension());
        if (keys == null || keys.isEmpty()) return List.of();
        List<LevelChunk> out = new ArrayList<>(keys.size());
        for (Long key : keys) {
            if (key == null) continue;
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
            if (chunk != null) out.add(chunk);
        }
        return out;
    }

    private static int loadedChunkCount(ServerLevel level) {
        Set<Long> keys = LOADED_CHUNKS.get(level.dimension());
        return keys == null ? 0 : keys.size();
    }

    // ============================================================
    //  对外方法接口（本轮交付物 ✓ 展示层以后再说 ✓）
    // ============================================================

    /** 净产率（<b>个/时</b> ✓）：最近 1 个 10 分钟区间 ✓ */
    public static double netPerHourRecent(ServerLevel level, Item item) {
        return ContainerRateData.get(level).netPerHour(item, 1);
    }

    /** 净产率（<b>个/时</b> ✓）：最近 {@code intervals} 个区间（1 = 10 分钟，6 = 1 小时 ✓） */
    public static double netPerHour(ServerLevel level, Item item, int intervals) {
        return ContainerRateData.get(level).netPerHour(item, intervals);
    }

    /** 最近一个区间的净增量（个 ✓） */
    public static long lastIntervalNet(ServerLevel level, Item item) {
        return ContainerRateData.get(level).lastIntervalNet(item);
    }

    /** 全维度净产率快照（个/时 ✓） */
    public static Map<Item, Double> netPerHourAll(ServerLevel level, int intervals) {
        return ContainerRateData.get(level).netPerHourAll(intervals);
    }

    /** 按注册名查物品（取不到返回 null ✓） */
    public static Item itemById(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
    }

    /** 按路径取维度 key ✓ */
    public static ResourceKey<Level> dimensionKey(String path) {
        ResourceLocation rl = ResourceLocation.tryParse(path);
        return rl == null ? null : ResourceKey.create(Registries.DIMENSION, rl);
    }

    /** 上次采样的诊断 ✓（来源数 / 跳过 / 失败 / 变了多少种 / 本维度的纯工作耗时 ✓） */
    public static SampleStats lastStats(ServerLevel level) {
        return LAST_STATS.get(level.dimension());
    }

    /** 已登记的来源提供方数量（含原版那个 ✓） */
    public static int providerCount() {
        return PROVIDERS.size() + 1;
    }

    /** 这一维度现在是否正在分帧采样中 ✓（诊断用 ✓） */
    public static boolean sweeping(ServerLevel level) {
        return SWEEPS.containsKey(level.dimension());
    }

    /** 一次采样的诊断快照 ✓ */
    public record SampleStats(int sources, int observed, int skipped, int failed,
                              int chunks, int changedItems, long millis) {
    }

    /** 一轮分帧采样的进行态 ✓ */
    private static final class Sweep {
        final List<RateSource> sources;
        final Map<String, Object2LongMap<Item>> now = new HashMap<>();
        int index;
        int failed;
        long busyNanos;

        Sweep(List<RateSource> sources) {
            this.sources = sources;
        }
    }
}
