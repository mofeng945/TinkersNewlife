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
import net.minecraft.server.level.ServerPlayer;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
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

    /** 空来源哨兵用的空表 ✓（共享不可变 ✓；哨兵对象本身**每轮一个** ✓ 因为它要带本轮的轮号 ✓） */
    private static final Object2LongMap<Item> EMPTY_ITEMS = Object2LongMaps.emptyMap();
    private static final Object2LongMap<Fluid> EMPTY_FLUIDS = Object2LongMaps.emptyMap();

    /** 各维度已加载区块（{@code ChunkEvent} 维护 ✓） */
    private static final Map<ResourceKey<Level>, Set<Long>> LOADED_CHUNKS = new ConcurrentHashMap<>();

    /** 上一次采样：来源 id → 三类快照 ✓（**只在内存** ✓ 不落盘 ✓） */
    private static final Map<ResourceKey<Level>, Map<String, Sample>> LAST_SNAPSHOT = new ConcurrentHashMap<>();

    /** §749：上一次采样时"每个来源在哪个区块" ✓（用来分辨"区块卸载"与"方块被拆" ✓） */
    private static final Map<ResourceKey<Level>, Map<String, Long>> LAST_CHUNKS = new ConcurrentHashMap<>();

    /**
     * §788：每个维度"已经跑到第几轮" ✓ —— 快照里带上它是为了判断
     * <b>"这个来源上上一轮还在不在"</b> ✓（中间断过 ⇒ 不能求差 ✗ 只能重新立基线 ✓）。
     */
    private static final Map<ResourceKey<Level>, Long> SWEEP_SEQ = new ConcurrentHashMap<>();

    /**
     * §788：<b>这个维度以前扫过的区块</b> ✓（<b>只在内存</b> ✓ 服务器一重启就清空 ✓）。
     *
     * <p>用途：分辨"**玩家在自家基地新放的箱子/新加的机器**"（区块以前扫过 ⇒
     * 首次见到算一次流入 ✓）与"**新加载区块里的老容器**"（区块没扫过 ⇒ 首次见到只立基线 ✗
     * 不刷假产率 ✓）。
     * <p>⚠ 为什么"服务器一重启就清空"正是我们想要的 ✗：重启后第一轮把当时加载的区块全部记上 ✓
     * ⇒ 那些区块里的老容器**只立基线** ✓ ⇒ 首次扫描的表是干净的 ✓（用户口径 ✓）。
     */
    private static final Map<ResourceKey<Level>, Set<Long>> SWEPT_CHUNKS = new ConcurrentHashMap<>();

    /** §788：一条旧基线"连续多少轮没被观测到"就丢掉 ✓（24 轮 = 4 小时 ✓ 纯粹为了不让内存无限涨 ✓） */
    private static final long BASELINE_TTL_SWEEPS = 24L;

    /** 正在进行的分帧采样 ✓ */
    private static final Map<ResourceKey<Level>, Sweep> SWEEPS = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Long> NEXT_SWEEP = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Integer> BUDGETS = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, SampleStats> LAST_STATS = new ConcurrentHashMap<>();

    /** 来源提供方（原版容器恒在 ✓；AE2 / Mekanism 由 {@code IntegrationLoader} 挂 ✓） */
    private static final List<RateSourceProvider> PROVIDERS = new CopyOnWriteArrayList<>();
    private static final RateSourceProvider VANILLA = new VanillaContainerRateProvider();

    /** 玩家附近的补扫半径（区块 ✓ 8 = 17×17 ✓ 与"构筑"那套同量级 ✓） */
    private static final int PLAYER_CHUNK_RADIUS = 8;

    /** 观察者超时（tick ✓）：界面 5 秒问一次 ⇒ 10 秒没动静就当关了 ✓ */
    private static final long WATCH_TIMEOUT_TICKS = 200L;

    /** §745：谁"正在看"这个维度的界面 ⇒ 维度 → (玩家 UUID → 最后一次请求的 tick) ✓ */
    private static final Map<ResourceKey<Level>, Map<UUID, Long>> WATCHERS = new ConcurrentHashMap<>();

    /** 一轮采样跑完后的回调（由 {@code IndustrialPioneerHandler} 装上 ⇒ 立刻把新数据推给正在看的人 ✓） */
    private static volatile Consumer<ServerLevel> sweepListener;

    private static long lastLagLogTick = Long.MIN_VALUE;

    private ContainerRateManager() {
    }

    // ============================================================
    //  注册
    // ============================================================

    /** §745：装"采样完成"回调 ✓（只装一次 ✓ 见 {@code IndustrialPioneerHandler} 的静态块 ✓） */
    public static void setSweepListener(Consumer<ServerLevel> listener) {
        sweepListener = listener;
    }

    /** §745：客户端来要数据 ⇒ 记一笔"他正在看这个维度"✓（界面每 5 秒要一次 ⇒ 这个标记会自然续期 ✓） */
    public static void watch(ServerLevel level, ServerPlayer player) {
        if (level == null || player == null) return;
        long now = level.getServer() == null ? level.getGameTime() : level.getServer().getTickCount();
        WATCHERS.computeIfAbsent(level.dimension(), key -> new ConcurrentHashMap<>())
                .put(player.getUUID(), now);
    }

    /** §745：最近 {@link #WATCH_TIMEOUT_TICKS} 之内要过数据、且还在线的玩家 ✓（= 界面大概还开着 ✓） */
    public static List<ServerPlayer> watchers(ServerLevel level) {
        List<ServerPlayer> out = new ArrayList<>();
        Map<UUID, Long> map = WATCHERS.get(level.dimension());
        if (map == null || map.isEmpty() || level.getServer() == null) return out;
        long now = level.getServer().getTickCount();
        for (Map.Entry<UUID, Long> entry : map.entrySet()) {
            if (now - entry.getValue() > WATCH_TIMEOUT_TICKS) continue;      // 早关界面了 ⇒ 丢掉 ✓
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) out.add(player);
        }
        return out;
    }

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
        WATCHERS.clear();
        LAST_CHUNKS.clear();
        // §788：轮号与"以前扫过的区块"也清空 ✓ —— 重启后第一轮 = 全部重新立基线 ✓
        //   （这正是用户口径：**首次扫描只建基线**，不产出假产率 ✓）
        SWEEP_SEQ.clear();
        SWEPT_CHUNKS.clear();
        // ⚠ §745：这里**不能**清 LOADED_CHUNKS ✗ —— 出生点/生成器区块是在 ServerStartedEvent
        //   **之前**就加载好的 ✗，清了它们就再也不会补发 ChunkEvent.Load ✗
        //   ⇒ 玩家"站在出生点旁边放个箱子"永远扫不到 ✗（这正是用户实测报的"箱子没被统计"✓）。
        //   留着不清理是**安全**的：坐标是复用的 ✓ 每次真取块都用 getChunkNow 复核 ✓
        //   （没加载就返回 null ⇒ 自然跳过 ✓），换存档后旧键也不会造成误统计 ✓。
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
                // §788：但**区块照样记成"扫过"** ✓ —— 这些区块里现在没有来源 ✓，
                //   以后玩家在里面放个箱子 ⇒ 那才算"真·新出现"✓（否则会被当成老容器只立基线 ✗）。
                markSwept(dimension, sweep);
                NEXT_SWEEP.put(dimension, now + INTERVAL_TICKS);
                LAST_STATS.put(dimension, new SampleStats(0, 0, 0, 0, loadedChunkCount(level), 0, 0, 0L, 0L,
                        0, 0, 0, 0));
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

        // §788：这一轮的轮号 ✓ —— 只在这里取（"来源列表为空"那条早退路径**不占号** ✓
        //   否则会凭空多出一个空档，让所有来源都被当成"中间断过"⇒ 白丢一个区间 ✗）
        if (sweep.seq == 0L) {
            sweep.seq = SWEEP_SEQ.merge(dimension, 1L, Long::sum);
            sweep.emptySample = new Sample(EMPTY_ITEMS, EMPTY_FLUIDS, 0L, sweep.seq);
        }

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
            // ⑥ 空来源哨兵 ✓（§788：哨兵也带本轮轮号 ✓ 否则"空箱子 → 放东西"会被当成断档 ✗）
            sweep.now.put(source.id(),
                    (items.isEmpty() && fluids.isEmpty() && energy[0] == 0L)
                            ? sweep.emptySample
                            : new Sample(items, fluids, energy[0], sweep.seq));
            // §749：顺手记下它所在的区块（来源"消失"时用来分辨是否只是区块卸载 ✓）
            try {
                sweep.chunks.put(source.id(), source.chunkKey());
            } catch (Throwable ignored) {
            }
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
        Map<String, Long> previousChunks = LAST_CHUNKS.get(dimension);
        Set<Long> loaded = LOADED_CHUNKS.get(dimension);
        Set<Long> sweptBefore = SWEPT_CHUNKS.computeIfAbsent(dimension, key -> new HashSet<>());
        long seq = sweep.seq;

        int skipped = 0;
        int newInflow = 0;                       // 首见 ⇒ 算一次流入（区块以前扫过 ✓ = 真·新东西 ✓）
        int newBaseline = 0;                     // 首见 ⇒ 只立基线（新加载区块里的老容器 ✓）
        int rebased = 0;                         // 上一轮没观测到 ⇒ 重新立基线（不结算 ✓）
        int dropped = 0;                         // 太久没观测到 ⇒ 丢掉旧基线（释放内存 ✓）
        int vanished = 0;
        Object2LongOpenHashMap<Item> deltaItems = new Object2LongOpenHashMap<>();
        Object2LongOpenHashMap<Fluid> deltaFluids = new Object2LongOpenHashMap<>();
        long netEnergy = 0L;
        List<String> inflowIds = new ArrayList<>();     // 诊断：首见就按下流入结算的来源 ✓

        // §788：基线**粘住** ✓（不再"这一轮没看到就忘掉"✗）——
        //   否则区块一卸载，老容器就从记忆里消失 ✗ ⇒ 回来时被当成"新来源"⇒
        //   **整块存量又被算一遍流入** ✗✗（NL 包日志实证：每轮 `新增 1 / 跳过 1 ⇒ 变动 800+ 种` ✗）
        Map<String, Sample> baseline = previous == null ? new HashMap<>() : new HashMap<>(previous);
        Map<String, Long> baselineChunks = previousChunks == null ? new HashMap<>() : new HashMap<>(previousChunks);

        if (previous != null) {
            for (Map.Entry<String, Sample> entry : sweep.now.entrySet()) {
                String id = entry.getKey();
                Sample after = entry.getValue();
                Sample before = baseline.get(id);
                if (before == null) {
                    // §788③ **第一次**见到这个来源：
                    //   · 它所在区块**以前扫过** ⇒ 说明这是"新出现的东西"✓
                    //     （玩家在自家基地新放的箱子 / 新加的机器 ✓）⇒ **算一次流入** ✓（§748 用户口径 ✓）；
                    //   · 区块是**这一轮才加载**的 ⇒ 里面多半是**世界本来就有的老容器** ✗
                    //     ⇒ **只立基线** ✓（否则全世界的存量都会被算成"这一轮产出的"✗✗ ——
                    //        用户实测："首次扫描"动辄 +20000 个/时 ✗）。
                    Long chunkKey = sweep.chunks.get(id);
                    if (chunkKey != null && chunkKey != Long.MIN_VALUE && sweptBefore.contains(chunkKey)) {
                        inflow(after, deltaItems, deltaFluids);
                        netEnergy += after.energy();
                        newInflow++;
                        if (inflowIds.size() < 5) inflowIds.add(id);
                    } else {
                        newBaseline++;
                    }
                    continue;
                }
                if (before.seq() != seq - 1L) {
                    // §788② 中间断过（区块卸载 / 一时扫不到 ✗）⇒ 这段变化**没法归到"一个区间"里** ✗
                    //   ⇒ 重新立基线、不结算 ✓（既不假暴涨 ✓ 也不假暴跌 ✓）
                    rebased++;
                    continue;
                }
                if (before == after) continue;                          // 两边都是空哨兵 ✓
                diff(before.items(), after.items(), deltaItems);
                diff(before.fluids(), after.fluids(), deltaFluids);
                netEnergy += after.energy() - before.energy();
            }
            // §749 消失的来源：**能确认方块真没了 ⇒ 结算净减** ✓；只是区块卸载 ⇒ **不结算** ✓
            for (Iterator<Map.Entry<String, Sample>> it = baseline.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Sample> entry = it.next();
                String id = entry.getKey();
                if (sweep.now.containsKey(id)) continue;
                Sample gone = entry.getValue();
                if (gone.seq() != seq - 1L) {
                    // 上一轮它也不在观测集合里 ✓ ⇒ 这不算"刚被拆"✗
                    //   基线**留着**（回来后按"断档重建"处理 ✓）；太久没见到才丢掉 ✓（防内存无限涨 ✓）
                    if (seq - gone.seq() > BASELINE_TTL_SWEEPS) {
                        it.remove();
                        baselineChunks.remove(id);
                        dropped++;
                    } else {
                        skipped++;
                    }
                    continue;
                }
                Long chunkKey = baselineChunks.get(id);
                if (chunkKey == null || chunkKey == Long.MIN_VALUE
                        || loaded == null || !loaded.contains(chunkKey)
                        || !chunkStillLoaded(level, chunkKey)) {
                    skipped++;                        // 无法确认（多半是区块卸载 ✓）⇒ 不结算 ✓（防假暴跌 ✗）
                    continue;
                }
                // 上一轮还在、这一轮没了、区块也还在 ⇒ 方块确实被拆/被换 ⇒ 它原本的内容算**净减** ✓
                outflow(gone, deltaItems, deltaFluids);
                netEnergy -= gone.energy();
                vanished++;
                it.remove();                          // 确认没了 ⇒ 基线也一起清掉 ✓
                baselineChunks.remove(id);
            }
            ContainerRateData.get(level).pushInterval(deltaItems, deltaFluids, netEnergy);
        }
        // 本轮观测到的（含空来源哨兵 ✓）覆盖进基线 ✓ ⇒ 下一轮才有东西可比 ✓
        baseline.putAll(sweep.now);
        baselineChunks.putAll(sweep.chunks);
        LAST_SNAPSHOT.put(dimension, baseline);
        LAST_CHUNKS.put(dimension, baselineChunks);
        markSwept(dimension, sweep);                                       // ⚠ 判定之后才记 ✓
        SWEEPS.remove(dimension);
        // §745：这一轮刚采完 ⇒ 立刻把新数据推给"正在看这个维度界面"的玩家 ✓
        //   （不用等客户端下一次 5 秒轮询 ✓ 用户体感就是"开完界面数字自己就变新了"✓）
        try {
            Consumer<ServerLevel> listener = sweepListener;
            if (listener != null) listener.accept(level);
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[产率] 采样完成回调出错：{}", t.toString());
        }
        NEXT_SWEEP.put(dimension, (level.getServer() == null ? level.getGameTime()
                : level.getServer().getTickCount()) + INTERVAL_TICKS);

        long busyMs = sweep.busyNanos / 1_000_000L;
        LAST_STATS.put(dimension, new SampleStats(sweep.sources.size(), sweep.now.size(), skipped, sweep.failed,
                loadedChunkCount(level), deltaItems.size(), deltaFluids.size(), netEnergy, busyMs,
                newInflow, newBaseline, rebased, dropped));

        TinkersNewlife.LOGGER.info(
                "[产率] 维度 {} 采样完成：来源 {}（区块 {} / 失败 {} / 首见·算流入 {} / 首见·立基线 {}"
                        + " / 断档重建 {} / 结算消失 {} / 跳过 {} / 淘汰旧基线 {}）"
                        + "⇒ 变动 物品 {} 种 / 流体 {} 种 / 能量 {} FE，累计 {} ms{}",
                dimension.location(), sweep.sources.size(), loadedChunkCount(level), sweep.failed,
                newInflow, newBaseline, rebased, vanished, skipped, dropped,
                deltaItems.size(), deltaFluids.size(), netEnergy, busyMs,
                busyMs >= SWEEP_WARN_MS ? " ⚠ 偏慢" : "");
        if (!inflowIds.isEmpty()) {
            TinkersNewlife.LOGGER.info("[产率] 维度 {} 首见就按流入结算的来源（最多 5 条）：{}",
                    dimension.location(), String.join(" / ", inflowIds));
        }
        logFluidContributors(dimension, sweep);
    }

    /** 把这个快照里的东西按**流入**（正）累加进 delta ✓ */
    private static void inflow(Sample sample, Object2LongOpenHashMap<Item> deltaItems,
                               Object2LongOpenHashMap<Fluid> deltaFluids) {
        for (Object2LongMap.Entry<Item> one : sample.items().object2LongEntrySet()) {
            if (one.getLongValue() != 0L) deltaItems.addTo(one.getKey(), one.getLongValue());
        }
        for (Object2LongMap.Entry<Fluid> one : sample.fluids().object2LongEntrySet()) {
            if (one.getLongValue() != 0L) deltaFluids.addTo(one.getKey(), one.getLongValue());
        }
    }

    /** 把这个快照里的东西按**流出**（负）累加进 delta ✓ */
    private static void outflow(Sample sample, Object2LongOpenHashMap<Item> deltaItems,
                                Object2LongOpenHashMap<Fluid> deltaFluids) {
        for (Object2LongMap.Entry<Item> one : sample.items().object2LongEntrySet()) {
            if (one.getLongValue() != 0L) deltaItems.addTo(one.getKey(), -one.getLongValue());
        }
        for (Object2LongMap.Entry<Fluid> one : sample.fluids().object2LongEntrySet()) {
            if (one.getLongValue() != 0L) deltaFluids.addTo(one.getKey(), -one.getLongValue());
        }
    }

    /**
     * §788：把这一轮扫过的区块记成"已扫过" ✓ —— ⚠ <b>必须在判定之后调</b> ✓，
     * 否则本轮首次见到的容器会被当成"以前扫过 ⇒ 算流入"✗。
     */
    private static void markSwept(ResourceKey<Level> dimension, Sweep sweep) {
        try {
            SWEPT_CHUNKS.computeIfAbsent(dimension, key -> new HashSet<>()).addAll(sweep.chunkKeys);
        } catch (Throwable ignored) {
        }
    }

    /**
     * §788 诊断口子：<b>同一种流体被好几个来源上报</b> ⇒ 大概率是"同一个底层储罐被数了好几遍" ✗
     * （典型：多方块储罐，结构里每个方块实体都把同一份库存报一遍 ✗）。
     *
     * <p>只打"来源 ≥ 2 且合计 ≥ 1000 mB"的，最多 3 种流体、每种最多 6 条 ✓（不至于刷屏 ✓）。
     * 每条 = 来源 id（里面带方块坐标与方块实体类型 ✓）＋ 它报了多少 mB ✓。
     */
    private static void logFluidContributors(ResourceKey<Level> dimension, Sweep sweep) {
        try {
            Map<Fluid, List<String>> byFluid = new HashMap<>();
            Map<Fluid, Long> totals = new HashMap<>();
            for (Map.Entry<String, Sample> entry : sweep.now.entrySet()) {
                for (Object2LongMap.Entry<Fluid> one : entry.getValue().fluids().object2LongEntrySet()) {
                    long amount = one.getLongValue();
                    if (amount <= 0L) continue;
                    byFluid.computeIfAbsent(one.getKey(), key -> new ArrayList<>())
                            .add(entry.getKey() + "=" + amount + "mB");
                    totals.merge(one.getKey(), amount, Long::sum);
                }
            }
            int logged = 0;
            for (Map.Entry<Fluid, List<String>> entry : byFluid.entrySet()) {
                List<String> list = entry.getValue();
                if (list.size() < 2) continue;                       // 只有一个来源报 ⇒ 没嫌疑 ✓ 不打
                if (totals.getOrDefault(entry.getKey(), 0L) < 1000L) continue;
                if (logged++ >= 3) break;
                List<String> show = list.size() > 6 ? list.subList(0, 6) : list;
                TinkersNewlife.LOGGER.info("[产率] ⚠ 流体 {} 被 {} 个来源分别上报，合计 {} mB ⇒ {}",
                        ForgeRegistries.FLUIDS.getKey(entry.getKey()), list.size(),
                        totals.getOrDefault(entry.getKey(), 0L), String.join(" / ", show));
            }
        } catch (Throwable ignored) {
        }
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
        Sweep sweep = new Sweep(sources);
        for (LevelChunk chunk : chunks) {                 // §788：记下"这一轮扫过哪些区块"✓
            try {
                sweep.chunkKeys.add(chunk.getPos().toLong());
            } catch (Throwable ignored) {
            }
        }
        return sweep;
    }

    /**
     * 这一轮要扫的区块 = <b>事件登记的已加载区块</b> ∪ <b>玩家附近的区块</b> ✓（§745 ✓）
     *
     * <p>为什么要并"玩家附近"：{@code ChunkEvent.Load} 这套登记**可能漏**（最典型的就是
     * 出生点/生成器区块在服务器启动前就加载好了 ✗ ⇒ 那条 Load 事件发生在我们登记之前 ✓）。
     * 漏掉的后果很直观：玩家"就站在自己刚放的箱子旁边"却统计不到 ✗。
     * <p>⇒ 每轮都用玩家坐标按半径 {@link #PLAYER_CHUNK_RADIUS} 主动补一遍 ✓
     * （`getChunkNow` 取不到就说明没加载 ✓ 跳过 ✓）—— 成本是每人 17×17 次哈希查找 ✓ 可忽略 ✓。
     */
    private static List<LevelChunk> loadedChunks(ServerLevel level) {
        Map<Long, LevelChunk> out = new HashMap<>();
        Set<Long> keys = LOADED_CHUNKS.get(level.dimension());
        if (keys != null) {
            for (Long key : keys) {
                if (key == null) continue;
                LevelChunk chunk = chunkAt(level, ChunkPos.getX(key), ChunkPos.getZ(key));
                if (chunk != null) out.put(key, chunk);
            }
        }
        int radius = PLAYER_CHUNK_RADIUS;
        for (var player : level.players()) {
            int cx = player.chunkPosition().x;
            int cz = player.chunkPosition().z;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    LevelChunk chunk = chunkAt(level, cx + dx, cz + dz);
                    if (chunk != null) out.put(chunk.getPos().toLong(), chunk);
                }
            }
        }
        return new ArrayList<>(out.values());
    }

    /** §749：这个区块 key 对应的区块现在还加载着吗 ✓（用来确认"来源消失"不是卸载造成的 ✓） */
    private static boolean chunkStillLoaded(ServerLevel level, long chunkKey) {
        try {
            return level.getChunkSource().hasChunk(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static LevelChunk chunkAt(ServerLevel level, int x, int z) {
        try {
            return level.getChunkSource().getChunkNow(x, z);
        } catch (Throwable ignored) {
            return null;
        }
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
        long latest = SWEEP_SEQ.getOrDefault(level.dimension(), 0L);
        for (Sample sample : snapshot.values()) {
            // §788 ⚠ 基线现在是"粘住"的（里面还留着区块已卸载的旧来源 ✗）
            //   ⇒ 总量**只能算最近这一轮真的观测到的** ✓（否则会把卸载前的旧数一直算进去 ✗）
            if (sample.seq() != latest) continue;
            for (Object2LongMap.Entry<Item> entry : sample.items().object2LongEntrySet()) {
                long value = entry.getLongValue();
                if (value != 0L) out.merge(entry.getKey(), value, Long::sum);
            }
        }
        return out;
    }

    /** <b>流体目前总量</b>（mB ✓）：同 {@link #totalNow}，只是换成流体 ✓（§746 ✓） */
    public static Map<Fluid, Long> fluidTotalNow(ServerLevel level) {
        Map<String, Sample> snapshot = LAST_SNAPSHOT.get(level.dimension());
        Map<Fluid, Long> out = new HashMap<>();
        if (snapshot == null) return out;
        long latest = SWEEP_SEQ.getOrDefault(level.dimension(), 0L);
        for (Sample sample : snapshot.values()) {
            if (sample.seq() != latest) continue;
            for (Object2LongMap.Entry<Fluid> entry : sample.fluids().object2LongEntrySet()) {
                long value = entry.getLongValue();
                if (value != 0L) out.merge(entry.getKey(), value, Long::sum);
            }
        }
        return out;
    }

    /**
     * <b>当前储能</b>（FE ✓ §746）：把"最近一次采样"里该维度所有来源的 FE 加起来 ✓。
     * <p>⚠ 与物品/流体同一个口径 ✓：是**最近一次采样那一刻**的数 ✓ 不是实时读数 ✗。
     */
    public static long energyNow(ServerLevel level) {
        Map<String, Sample> snapshot = LAST_SNAPSHOT.get(level.dimension());
        if (snapshot == null) return 0L;
        long latest = SWEEP_SEQ.getOrDefault(level.dimension(), 0L);
        long sum = 0L;
        for (Sample sample : snapshot.values()) {
            if (sample.seq() != latest) continue;             // §788 同上：只算最近一轮观测到的 ✓
            sum += sample.energy();
        }
        return sum;
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

    /** 一次采样的诊断快照 ✓（§737 起含流体与能量 ✓；§788 起含"首见/断档/淘汰"四类计数 ✓） */
    public record SampleStats(int sources, int observed, int skipped, int failed, int chunks,
                              int changedItems, int changedFluids, long netEnergy, long millis,
                              int newInflow, int newBaseline, int rebased, int dropped) {
    }

    /**
     * 一个来源的三类快照 ✓（只存非零项 ✓）
     *
     * <p>§788：多带一个 {@code seq}（这是<b>第几轮</b>采的 ✓）—— 用来判断"上一轮它到底在不在" ✓：
     * 只有<b>连续两轮</b>都被观测到的来源才允许求差 ✓（中间断过 ⇒ 只能重新立基线 ✓）。
     */
    private record Sample(Object2LongMap<Item> items, Object2LongMap<Fluid> fluids, long energy, long seq) {
    }

    /** 一轮分帧采样的进行态 ✓ */
    private static final class Sweep {
        final List<RateSource> sources;
        final Map<String, Sample> now = new HashMap<>();
        /** 来源 id → 它所在区块 key ✓（§749 ✓） */
        final Map<String, Long> chunks = new HashMap<>();
        /** §788：这一轮扫过的区块 ✓（用来更新"以前扫过的区块"✓） */
        final Set<Long> chunkKeys = new HashSet<>();
        /** §788：本轮轮号 ✓（第一次 {@code advance} 时取号 ✓ 空来源的早退路径不取 ✓） */
        long seq;
        /** §788：本轮的空来源哨兵 ✓（带本轮轮号 ✓ 这样"空箱子 → 放东西"能正常求差 ✓） */
        Sample emptySample;
        int index;
        int failed;
        long busyNanos;

        Sweep(List<RateSource> sources) {
            this.sources = sources;
        }
    }
}
