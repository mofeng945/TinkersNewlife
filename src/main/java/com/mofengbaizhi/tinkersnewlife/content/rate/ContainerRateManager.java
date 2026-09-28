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
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongConsumer;
import java.util.function.ObjLongConsumer;

/**
 * <b>容器产率统计引擎</b>（§735 建 · §736「全优化」✓ · §737 起<b>物品 / 流体 / 能量三种都统计</b> ✓）
 * —— 只提供<b>方法接口</b> ✓（无命令/界面/导出 ✗）。
 *
 * <h2>口径（用户拍板 ✓）</h2>
 * 整个维度的量先按类型求和（物品按 {@code Item} ✓ 流体按 {@code Fluid} ✓ 能量就一个总数 ✓）
 * ⇒ 与上一次采样比 ⇒ <b>净差 ÷ 统计时间</b> ✓；周期 <b>10 分钟</b> ✓；
 * 单位：<b>个/时</b>、<b>mB/时</b>、<b>FE/时</b> ✓；只对"两次都观测到"的来源求和 ✓
 * （搬运抵消 ✓ / 卸载不产生假净减 ✓）。
 *
 * <h2>能量口径 ⚠ 必须说清</h2>
 * 能量统计的是"<b>这一维度所有来源当前存着的 FE 之和</b>"的变化速率 ✓
 * ⇒ 它衡量的是"有没有**攒下来**"✓，<b>不是发电机的输出功率</b> ✗
 * （发电机发多少、机器就吃多少 ⇒ 净差 ≈ 0 ✓ 这是口径本身决定的 ✓ 与物品那套完全同源 ✓）。
 *
 * <h2>§736 性能优化（保留 ✓）</h2>
 * ①分帧采样（每 tick {@link #TOTAL_SOURCE_BUDGET} 个来源 ✓ 共享预算 ✓）
 * ②自适应预算 ③安全阀（平均 tick 偏高就暂停推进 ✓）④多维度错峰
 * ⑤按区块缓存来源（能力只在首次解析 ✓）⑥空来源哨兵 ⑦fastutil 免装箱
 * ⑧AE2 改为<b>按磁盘读</b>（不查网络 ✓ 见 {@code Ae2RateProvider} ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ContainerRateManager {

    /** 采样周期：<b>10 分钟</b> ✓（20 tick/秒 × 600 秒 ✓ 用户口径 ✓） */
    public static final int INTERVAL_TICKS = 12_000;

    /** 每 tick 处理的来源数上限（**所有维度共享** ✓） */
    private static final int TOTAL_SOURCE_BUDGET = 256;
    /** 自适应预算下限 */
    private static final int MIN_SOURCE_BUDGET = 16;
    /** 单片耗时超过它 ⇒ 下次预算减半 ✓ */
    private static final long SLICE_WARN_MS = 10L;
    /** 服务器平均 tick 超过它 ⇒ 本轮不推进（安全阀 ✓） */
    private static final double LAG_SKIP_TICK_MS = 100.0D;
    /** 一整轮超过它就打一行 warn ✓ */
    private static final long SWEEP_WARN_MS = 1_000L;
    /** 多维度错峰间隔 ✓ */
    private static final long DIM_STAGGER_TICKS = 200L;

    /** 空来源哨兵 ✓（共享不可变空表 ✓ 差分里"两边都是它 ⇒ 跳过"快路径 ✓） */
    private static final Sample EMPTY_SAMPLE =
            new Sample(Object2LongMaps.emptyMap(), Object2LongMaps.emptyMap(), 0L);

    /** 各维度已加载区块（{@code ChunkEvent} 维护 ✓） */
    private static final Map<ResourceKey<Level>, Set<Long>> LOADED_CHUNKS = new ConcurrentHashMap<>();

    /** 上一次采样：来源 id → 三类快照 ✓（**只在内存** ✓ 不落盘 ✓） */
    private static final Map<ResourceKey<Level>, Map<String, Sample>> LAST_SNAPSHOT = new ConcurrentHashMap<>();

    /** 正在进行的分帧采样 ✓ */
    private static final Map<ResourceKey<Level>, Sweep> SWEEPS = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Long> NEXT_SWEEP = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Integer> BUDGETS = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, SampleStats> LAST_STATS = new ConcurrentHashMap<>();

    /** 来源提供方（原版容器恒在 ✓；AE2 / Mekanism 由 {@code IntegrationLoader} 挂 ✓） */
    private static final List<RateSourceProvider> PROVIDERS = new CopyOnWriteArrayList<>();
    private static final RateSourceProvider VANILLA = new VanillaContainerRateProvider();

    private static long lastLagLogTick = Long.MIN_VALUE;

    private ContainerRateManager() {
    }

    // ============================================================
    //  注册
    // ============================================================

    public static void registerProvider(RateSourceProvider provider) {
        if (provider == null) return;
        PROVIDERS.add(provider);
        TinkersNewlife.LOGGER.info("[产率] 已挂载来源提供方：{} ✓", provider.modId().isEmpty() ? "原版容器" : provider.modId());
    }

    // ============================================================
    //  事件
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
        VanillaContainerRateProvider.dropChunk(chunkKey);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
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

        double avgTick = 0.0D;
        try {
            avgTick = server.getAverageTickTime();
        } catch (Throwable ignored) {
        }
        if (avgTick > LAG_SKIP_TICK_MS) {                                   // ③ 安全阀 ✓
            if (now - lastLagLogTick > INTERVAL_TICKS) {
                lastLagLogTick = now;
                TinkersNewlife.LOGGER.info("[产率] 服务器平均 tick {} ms 偏高 ⇒ 本轮采样暂停推进（恢复后自动继续 ✓）",
                        String.format(Locale.ROOT, "%.1f", avgTick));
            }
            return;
        }

        int index = 0;                                                      // ④ 多维度错峰 ✓
        for (ServerLevel level : server.getAllLevels()) {
            try {
                if (NEXT_SWEEP.get(level.dimension()) == null) {
                    NEXT_SWEEP.put(level.dimension(), now + index * DIM_STAGGER_TICKS);
                }
            } catch (Throwable ignored) {
            }
            index++;
        }

        int remaining = TOTAL_SOURCE_BUDGET;                               // ① 共享预算 ✓
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

    private static int tickDimension(ServerLevel level, long now, int available) {
        ResourceKey<Level> dimension = level.dimension();
        Sweep sweep = SWEEPS.get(dimension);
        if (sweep == null) {
            Long next = NEXT_SWEEP.get(dimension);
            if (next == null || now < next) return 0;
            sweep = startSweep(level);
            if (sweep.sources.isEmpty()) {
                // §738 ⚠ 这里**故意不推区间** ✗ —— 该维度此刻一个来源都没有（区块全卸载 / 没人来过 ✓），
                //   若照推一个"0 增量"的区间，就会把"最近 1 小时"慢慢填满 0 ✓
                //   ⇒ 绑定了这个维度的「工业开拓之证」加成会凭空掉光 ✗（那不是用户要的口径 ✓）。
                //   正确语义：**没观测到 = 没数据** ✓（时间轴留着空档 ✓ 旧数据不会被冲掉 ✓）。
                NEXT_SWEEP.put(dimension, now + INTERVAL_TICKS);
                LAST_STATS.put(dimension, new SampleStats(0, 0, 0, 0, loadedChunkCount(level), 0, 0, 0L, 0L));
                return 0;
            }
            SWEEPS.put(dimension, sweep);
        }
        return advance(level, sweep, available);
    }

    private static int advance(ServerLevel level, Sweep sweep, int available) {
        ResourceKey<Level> dimension = level.dimension();
        int budget = Math.min(available, BUDGETS.getOrDefault(dimension, TOTAL_SOURCE_BUDGET));
        if (budget <= 0) return 0;

        long start = System.nanoTime();
        int used = 0;
        while (used < budget && sweep.index < sweep.sources.size()) {
            RateSource source = sweep.sources.get(sweep.index++);
            Object2LongOpenHashMap<Item> items = new Object2LongOpenHashMap<>();
            Object2LongOpenHashMap<Fluid> fluids = new Object2LongOpenHashMap<>();
            long[] energy = new long[1];
            ObjLongConsumer<ItemStack> itemSink = (stack, count) -> {
                if (count <= 0L || stack == null || stack.isEmpty()) return;
                items.addTo(stack.getItem(), count);
            };
            ObjLongConsumer<FluidStack> fluidSink = (stack, amount) -> {
                if (amount <= 0L || stack == null || stack.isEmpty()) return;
                fluids.addTo(stack.getFluid(), amount);
            };
            LongConsumer energySink = value -> energy[0] += value;
            try {
                source.forEachAll(itemSink, fluidSink, energySink);         // 一次报全三类 ✓
            } catch (Throwable t) {
                sweep.failed++;
                continue;
            }
            // ⑥ 空来源哨兵 ✓
            sweep.now.put(source.id(),
                    (items.isEmpty() && fluids.isEmpty() && energy[0] == 0L)
                            ? EMPTY_SAMPLE
                            : new Sample(items, fluids, energy[0]));
            used++;
        }
        long sliceNanos = System.nanoTime() - start;
        sweep.busyNanos += sliceNanos;

        long sliceMs = sliceNanos / 1_000_000L;                            // ② 自适应预算 ✓
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

    private static void finishSweep(ServerLevel level, Sweep sweep) {
        ResourceKey<Level> dimension = level.dimension();
        Map<String, Sample> previous = LAST_SNAPSHOT.get(dimension);
        int skipped = 0;
        Object2LongOpenHashMap<Item> deltaItems = new Object2LongOpenHashMap<>();
        Object2LongOpenHashMap<Fluid> deltaFluids = new Object2LongOpenHashMap<>();
        long netEnergy = 0L;

        if (previous != null) {
            for (Map.Entry<String, Sample> entry : sweep.now.entrySet()) {
                Sample before = previous.get(entry.getKey());
                if (before == null) {
                    skipped++;                                          // 新来源 ⇒ 只立基线 ✓
                    continue;
                }
                Sample after = entry.getValue();
                if (before == after) continue;                          // 两边都是空哨兵 ✓
                diff(before.items(), after.items(), deltaItems);
                diff(before.fluids(), after.fluids(), deltaFluids);
                netEnergy += after.energy() - before.energy();
            }
            for (String id : previous.keySet()) {
                if (!sweep.now.containsKey(id)) skipped++;               // 卸载/被拆 ⇒ 整条跳过 ✓
            }
            ContainerRateData.get(level).pushInterval(deltaItems, deltaFluids, netEnergy);
        }
        LAST_SNAPSHOT.put(dimension, sweep.now);
        SWEEPS.remove(dimension);
        NEXT_SWEEP.put(dimension, (level.getServer() == null ? level.getGameTime()
                : level.getServer().getTickCount()) + INTERVAL_TICKS);

        long busyMs = sweep.busyNanos / 1_000_000L;
        LAST_STATS.put(dimension, new SampleStats(sweep.sources.size(), sweep.now.size(), skipped, sweep.failed,
                loadedChunkCount(level), deltaItems.size(), deltaFluids.size(), netEnergy, busyMs));

        TinkersNewlife.LOGGER.info(
                "[产率] 维度 {} 采样完成：来源 {}（区块 {} / 失败 {} / 跳过 {}）⇒ 变动 物品 {} 种 / 流体 {} 种 / 能量 {} FE，累计 {} ms{}",
                dimension.location(), sweep.sources.size(), loadedChunkCount(level), sweep.failed, skipped,
                deltaItems.size(), deltaFluids.size(), netEnergy, busyMs,
                busyMs >= SWEEP_WARN_MS ? " ⚠ 偏慢" : "");
    }

    /** 把"这一格对上一格"的差累加进 delta ✓（并集都要看 ✓） */
    private static <T> void diff(Object2LongMap<T> before, Object2LongMap<T> after, Object2LongOpenHashMap<T> delta) {
        if (before.isEmpty() && after.isEmpty()) return;
        for (Object2LongMap.Entry<T> one : after.object2LongEntrySet()) {
            long d = one.getLongValue() - before.getLong(one.getKey());
            if (d != 0L) delta.addTo(one.getKey(), d);
        }
        for (Object2LongMap.Entry<T> one : before.object2LongEntrySet()) {
            if (!after.containsKey(one.getKey())) delta.addTo(one.getKey(), -one.getLongValue());
        }
    }

    // ============================================================
    //  采样入口
    // ============================================================

    /** 手动催一次（<b>会一口气跑完</b> ✓ 别在服务器卡的时候调 ✓） */
    public static void sample(ServerLevel level) {
        Sweep sweep = startSweep(level);
        int guard = 0;
        while (sweep.index < sweep.sources.size() && guard++ < 100_000) {
            advance(level, sweep, TOTAL_SOURCE_BUDGET);
        }
    }

    /**
     * <b>催一次"分帧采样"</b>（§744 ✓）：把这一维度的下次采样时刻<b>提前到现在</b> ✓
     * —— 真正的扫描仍然走 {@link #advance} 那套**每 tick 一个预算**的分帧机制 ✓
     * ⇒ **不会**因为"玩家点了一下"就把几万来源挤进一个 tick ✗（那正是 §736 花力气消掉的尖峰 ✓）。
     *
     * <p>典型用途：玩家右键打开产率界面时 ✓ —— 界面先把"当前（可能旧）数据"画出来 ✓，
     * 几秒后这次分帧采样跑完 ✓ 客户端下一次刷新就能看到**刚采的**数 ✓（用户口径"动态变化"✓）。
     *
     * <p>⚠ 已经在扫了就直接返回 ✓（不排队、不叠加 ✓）。
     */
    public static void forceSweep(ServerLevel level) {
        ResourceKey<Level> dimension = level.dimension();
        if (SWEEPS.containsKey(dimension)) return;
        NEXT_SWEEP.put(dimension, level.getServer() == null ? level.getGameTime()
                : level.getServer().getTickCount());
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
    //  对外方法接口（本轮交付物 ✓）
    // ============================================================

    // ---- 物品（个/时 ✓）----

    public static double netPerHourRecent(ServerLevel level, Item item) {
        return ContainerRateData.get(level).netPerHour(item, 1);
    }

    public static double netPerHour(ServerLevel level, Item item, int intervals) {
        return ContainerRateData.get(level).netPerHour(item, intervals);
    }

    public static long lastIntervalNet(ServerLevel level, Item item) {
        return ContainerRateData.get(level).lastIntervalNet(item);
    }

    public static Map<Item, Double> netPerHourAll(ServerLevel level, int intervals) {
        return ContainerRateData.get(level).netPerHourAll(intervals);
    }

    // ---- 流体（mB/时 ✓）----

    public static double fluidNetPerHourRecent(ServerLevel level, Fluid fluid) {
        return ContainerRateData.get(level).netPerHour(fluid, 1);
    }

    public static double fluidNetPerHour(ServerLevel level, Fluid fluid, int intervals) {
        return ContainerRateData.get(level).netPerHour(fluid, intervals);
    }

    public static long lastIntervalFluidNet(ServerLevel level, Fluid fluid) {
        return ContainerRateData.get(level).lastIntervalNet(fluid);
    }

    public static Map<Fluid, Double> fluidNetPerHourAll(ServerLevel level, int intervals) {
        return ContainerRateData.get(level).fluidNetPerHourAll(intervals);
    }

    // ---- 能量（FE/时 ✓）----

    /** 能量净产率（FE/时 ✓）：最近 1 个区间 ✓ */
    public static double energyNetPerHourRecent(ServerLevel level) {
        return ContainerRateData.get(level).energyNetPerHour(1);
    }

    /** 能量净产率（FE/时 ✓）：最近 n 个区间 ✓ */
    public static double energyNetPerHour(ServerLevel level, int intervals) {
        return ContainerRateData.get(level).energyNetPerHour(intervals);
    }

    /** 最近一个区间的能量净增量（FE ✓） */
    public static long lastIntervalEnergyNet(ServerLevel level) {
        return ContainerRateData.get(level).lastIntervalEnergyNet();
    }

    // ---- 便利入口 / 诊断 ----

    /**
     * <b>目前总量</b>（§743 ✓）：把"最近一次采样"里该维度<b>所有来源</b>的每种物品总数加起来 ✓
     * —— 也就是"这个维度现在一共有多少个这种东西" ✓（按 {@code Item} 归并 ✓ 忽略 NBT ✓）。
     *
     * <p>⚠ 三个如实说明（别当成实时读数 ✗）：
     * <ul>
     *   <li>它是<b>最近一次采样那一刻</b>的数 ✓（采样周期 10 分钟 ⇒ 最多旧 10 分钟 ✓）；</li>
     *   <li>还没跑完第一轮采样 ⇒ <b>返回空表</b> ✓（不是"0 个" ✗ 调用方要分开处理 ✓）；</li>
     *   <li>只包含<b>被观测到的来源</b> ✓（区块已卸载的容器不在内 ✓ 与产率同一个口径 ✓）。</li>
     * </ul>
     */
    public static Map<Item, Long> totalNow(ServerLevel level) {
        Map<String, Sample> snapshot = LAST_SNAPSHOT.get(level.dimension());
        Map<Item, Long> out = new HashMap<>();
        if (snapshot == null) return out;
        for (Sample sample : snapshot.values()) {
            for (Object2LongMap.Entry<Item> entry : sample.items().object2LongEntrySet()) {
                long value = entry.getLongValue();
                if (value != 0L) out.merge(entry.getKey(), value, Long::sum);
            }
        }
        return out;
    }

    /** 这个维度是否已经完成过至少一轮采样 ✓（判断"有没有数据"而不是"数据是不是 0" ✓） */
    public static boolean hasSnapshot(ServerLevel level) {
        return LAST_SNAPSHOT.containsKey(level.dimension());
    }

    public static Item itemById(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
    }

    public static Fluid fluidById(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : ForgeRegistries.FLUIDS.getValue(rl);
    }

    public static ResourceKey<Level> dimensionKey(String path) {
        ResourceLocation rl = ResourceLocation.tryParse(path);
        return rl == null ? null : ResourceKey.create(Registries.DIMENSION, rl);
    }

    public static SampleStats lastStats(ServerLevel level) {
        return LAST_STATS.get(level.dimension());
    }

    public static int providerCount() {
        return PROVIDERS.size() + 1;
    }

    public static boolean sweeping(ServerLevel level) {
        return SWEEPS.containsKey(level.dimension());
    }

    /** 一次采样的诊断快照 ✓（§737 起含流体与能量 ✓） */
    public record SampleStats(int sources, int observed, int skipped, int failed, int chunks,
                              int changedItems, int changedFluids, long netEnergy, long millis) {
    }

    /** 一个来源的三类快照 ✓（只存非零项 ✓ 空来源共享 {@link #EMPTY_SAMPLE} ✓） */
    private record Sample(Object2LongMap<Item> items, Object2LongMap<Fluid> fluids, long energy) {
    }

    /** 一轮分帧采样的进行态 ✓ */
    private static final class Sweep {
        final List<RateSource> sources;
        final Map<String, Sample> now = new HashMap<>();
        int index;
        int failed;
        long busyNanos;

        Sweep(List<RateSource> sources) {
            this.sources = sources;
        }
    }
}
