package com.mofengbaizhi.tinkersnewlife.content.rate;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
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
 * <b>容器产率统计引擎</b>（§735）—— 本轮只交付<b>方法接口</b> ✓，不做命令/界面/导出 ✗。
 *
 * <h2>口径（用户拍板 ✓）</h2>
 * <ul>
 *   <li>把<b>整个维度</b>里所有容器的同一物品<b>先求和</b> ✓，再看这个总和随时间的变化 ✓；
 *   <li>速率 = <b>净差 ÷ 统计时间</b> ✓，周期 <b>10 分钟</b>一次 ✓，单位 <b>个/时</b> ✓；
 *   <li>按 {@code Item} <b>归并</b>（忽略 NBT ✓）；<b>不做</b>离线扫描 ✗（未加载区块读不到 ✓）；
 *   <li>⇒ 维度内"箱子 → 箱子"的搬运<b>自动抵消</b> ✓（这正是用户要解决的"搬运刷产率" ✓
 *       —— 逐容器只加正增量那套会把搬运算成产出 ✗ 所以不采用 ✓）。</li>
 * </ul>
 *
 * <h2>基线规则（防假尖峰 / 防假下降 ✓ 必须写死 ✗）</h2>
 * <ol>
 *   <li><b>只对"两次采样都观测到"的来源求和</b> ✓ —— 新放下的容器第一次只立基线 ✓（否则"放一箱满的"会刷出巨量产出 ✗）；
 *   <li>卸载/被拆的来源<b>整条跳过</b> ✓（否则它的内容消失会被算成净减 ✗✗ —— 这是"净差"口径最大的坑 ✓）；</li>
 *   <li>代价：来源在"没人看着"期间的产出会<b>丢失</b> ✗ ⇒ 记进诊断数据 ✓ 如实标注 ✓ 不谎报 ✓。</li>
 * </ol>
 *
 * <h2>性能</h2>
 * 10 分钟才跑一次 ✓；采样只在<b>服务端主线程</b>做 ✓；单个来源炸了只跳过它 ✓；
 * 超过 {@link #SLOW_SAMPLE_WARN_MS} 会打一行 warn ✓（便于以后加大网络时排查 ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ContainerRateManager {

    /** 采样周期：<b>10 分钟</b> ✓（20 tick/秒 × 600 秒 ✓ 用户口径 ✓） */
    public static final int INTERVAL_TICKS = 12_000;

    /** 慢采样告警阈值（毫秒 ✓） */
    private static final long SLOW_SAMPLE_WARN_MS = 250L;

    /** 各维度<b>已加载区块</b>（{@code ChunkEvent} 维护 ✓ 与 §构筑 的 {@code ConstructTechnique} 同一套思路 ✓）
     *  —— 因为 MC 1.20.1 <b>没有</b>公开的"枚举已加载区块"接口 ✗（{@code ChunkMap#getChunks()} 是 protected ✗）。 */
    private static final Map<ResourceKey<Level>, Set<Long>> LOADED_CHUNKS = new ConcurrentHashMap<>();

    /** 上一次采样时"每来源 → (物品 → 数量)" ✓（**只在内存** ✓ 不落盘 ✓ 见 {@link ContainerRateData} 的说明 ✓） */
    private static final Map<ResourceKey<Level>, Map<String, Map<Item, Long>>> LAST_SNAPSHOT = new ConcurrentHashMap<>();

    /** 各维度上一次采样的诊断数据 ✓（方法接口的一部分 ✓） */
    private static final Map<ResourceKey<Level>, SampleStats> LAST_STATS = new ConcurrentHashMap<>();

    /** 来源提供方（原版容器恒在 ✓；AE2 / Mekanism 由 {@code IntegrationLoader} 按"模组在不在"挂进来 ✓） */
    private static final List<RateSourceProvider> PROVIDERS = new CopyOnWriteArrayList<>();
    private static final RateSourceProvider VANILLA = new VanillaContainerRateProvider();

    /** 下一次采样的服务器 tick（-1 = 还没排 ✓） */
    private static long nextSampleTick = -1L;

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
    //  事件：区块登记 / 定时采样 / 服务器启动
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
        Set<Long> set = LOADED_CHUNKS.get(level.dimension());
        if (set != null) set.remove(event.getChunk().getPos().toLong());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // 换存档/重启 ⇒ 基线快照作废 ✓（统计数据本身在 SavedData 里 ✓ 会保留 ✓）
        LAST_SNAPSHOT.clear();
        LAST_STATS.clear();
        nextSampleTick = -1L;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;
        long now = server.getTickCount();
        if (nextSampleTick < 0L) {
            nextSampleTick = now + INTERVAL_TICKS;
            return;
        }
        if (now < nextSampleTick) return;
        nextSampleTick = now + INTERVAL_TICKS;
        for (ServerLevel level : server.getAllLevels()) {
            try {
                sample(level);
            } catch (Throwable t) {
                TinkersNewlife.LOGGER.warn("[产率] 维度 {} 采样失败：{}", level.dimension().location(), t.toString());
            }
        }
    }

    // ============================================================
    //  核心：采样一次 —— 全维度求和 → 与上次比 → 推入净增量区间
    // ============================================================

    /**
     * 对某一维度做一次采样 ✓（<b>只读</b> ✗ 不改任何容器 ✓）。
     * <p>公开出来是为了让以后的命令/接口能"手动催一次" ✓（本轮没有命令 ✓）。
     */
    public static void sample(ServerLevel level) {
        long start = System.nanoTime();
        List<LevelChunk> chunks = loadedChunks(level);

        // ① 收集来源：原版（IItemHandler）＋ 各联动提供方 ✓
        List<RateSource> sources = new ArrayList<>(VANILLA.sourcesFor(level, chunks));
        for (RateSourceProvider provider : PROVIDERS) {
            try {
                List<RateSource> more = provider.sourcesFor(level, chunks);
                if (more != null) sources.addAll(more);
            } catch (Throwable t) {
                TinkersNewlife.LOGGER.warn("[产率] 来源提供方 {} 报错（已跳过）：{}", provider.modId(), t.toString());
            }
        }

        // ② 逐来源做快照（按 Item 归并 ✓）
        Map<String, Map<Item, Long>> now = new HashMap<>();
        int failed = 0;
        for (RateSource source : sources) {
            Map<Item, Long> counts = new HashMap<>();
            ObjLongConsumer<net.minecraft.world.item.ItemStack> collector = (stack, count) -> {
                if (count <= 0L || stack == null || stack.isEmpty()) return;
                counts.merge(stack.getItem(), count, Long::sum);
            };
            try {
                source.forEachStored(collector);
            } catch (Throwable t) {
                failed++;
                continue;                                  // 单个来源炸了不影响整体 ✓
            }
            now.put(source.id(), counts);
        }

        // ③ 与上一次比：只对"两次都观测到"的来源求和 ⇒ 搬运抵消 ✓ / 卸载不产生假跌落 ✓
        Map<String, Map<Item, Long>> prev = LAST_SNAPSHOT.get(level.dimension());
        int skipped = 0;
        Map<Item, Long> delta = new HashMap<>();
        if (prev != null) {
            for (Map.Entry<String, Map<Item, Long>> entry : now.entrySet()) {
                Map<Item, Long> before = prev.get(entry.getKey());
                if (before == null) {
                    skipped++;                            // 新来源 ⇒ 只立基线 ✓ 不算产出 ✓
                    continue;
                }
                Map<Item, Long> after = entry.getValue();
                for (Map.Entry<Item, Long> one : after.entrySet()) {
                    long d = one.getValue() - before.getOrDefault(one.getKey(), 0L);
                    if (d != 0L) delta.merge(one.getKey(), d, Long::sum);
                }
                for (Map.Entry<Item, Long> one : before.entrySet()) {
                    if (!after.containsKey(one.getKey())) delta.merge(one.getKey(), -one.getValue(), Long::sum);
                }
            }
            for (String id : prev.keySet()) {
                if (!now.containsKey(id)) skipped++;       // 卸载/被拆 ⇒ 整条跳过 ✓（它的消失不算净减 ✓）
            }
            ContainerRateData.get(level).pushInterval(delta);   // ④ 落一个 10 分钟区间 ✓
        }

        LAST_SNAPSHOT.put(level.dimension(), now);

        long ms = (System.nanoTime() - start) / 1_000_000L;
        LAST_STATS.put(level.dimension(), new SampleStats(sources.size(), now.size(), skipped, failed,
                chunks.size(), delta.size(), ms));

        TinkersNewlife.LOGGER.info(
                "[产率] 维度 {} 采样完成：来源 {}（区块 {} / 失败 {} / 跳过 {}）⇒ 本次物品变动 {} 种，耗时 {} ms{}",
                level.dimension().location(), sources.size(), chunks.size(), failed, skipped, delta.size(), ms,
                ms >= SLOW_SAMPLE_WARN_MS ? " ⚠ 偏慢，考虑降低来源数或延长时间隔" : "");
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

    /** 全维度净产率快照（个/时 ✓）；{@code intervals} 同上 ✓ */
    public static Map<Item, Double> netPerHourAll(ServerLevel level, int intervals) {
        return ContainerRateData.get(level).netPerHourAll(intervals);
    }

    /** 按注册名查物品（方便调用方从字符串来 ✓ 取不到返回 null ✓） */
    public static Item itemById(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
    }

    /** 维度 key（调用方通常直接用 {@code level.dimension()} ✓ 这里给个按路径取的便利方法 ✓） */
    public static ResourceKey<Level> dimensionKey(String path) {
        ResourceLocation rl = ResourceLocation.tryParse(path);
        return rl == null ? null : ResourceKey.create(Registries.DIMENSION, rl);
    }

    /** 上次采样的诊断数据 ✓（来源数 / 跳过数 / 耗时……✓） */
    public static SampleStats lastStats(ServerLevel level) {
        return LAST_STATS.get(level.dimension());
    }

    /** 已登记的来源提供方数量（含原版那个 ✓） */
    public static int providerCount() {
        return PROVIDERS.size() + 1;
    }

    /** 一次采样的诊断快照 ✓ */
    public record SampleStats(int sources, int observed, int skipped, int failed,
                              int chunks, int changedItems, long millis) {
    }
}
