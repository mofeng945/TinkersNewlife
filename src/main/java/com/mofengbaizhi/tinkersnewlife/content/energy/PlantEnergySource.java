package com.mofengbaizhi.tinkersnewlife.content.energy;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>来源 ②「植物」：以"凋灵度"慢慢榨干身边的草木</b>（用户口径 §545 全数值 ✓）。
 *
 * <h2>规则（用户给的原话 → 实现）</h2>
 * <ul>
 *   <li><b>范围</b>：以台座为心的<b>球半径 5</b> ✓（{@link #scan} 用平方距离判球，不是立方体 ✗）；</li>
 *   <li><b>每秒</b>给范围内<b>每一株</b>植物各 <b>+1 点凋灵度</b> ✓；</li>
 *   <li><b>1 点 = 0.5 EE</b> ⇒ 也就是<b>每株植物每存活一秒给 0.5 EE</b> ✓；</li>
 *   <li><b>上限</b>：草 <b>5</b> / 花 <b>20</b> / 树苗 <b>30</b> ⇒ 攒到上限那一秒该植物<b>消失</b> ✓
 *       （累计产出：草 2.5 EE、花 10 EE、树苗 15 EE ✓）；</li>
 *   <li><b>作物（{@code #minecraft:crops}）按"花"档</b>（上限 20 ✓ 用户明确"不排除作物"✓）；</li>
 *   <li><b>凋零玫瑰排除</b> ✓（{@code minecraft:wither_rose} 直接返回 0 ⇒ 既不计凋灵度也不消失 ✓）；</li>
 *   <li>凋灵度存<b>内存 Map</b>（方块位置 → 点数）✓ <b>不持久化</b> ✓（重启清零，用户已确认可接受 ✓）。</li>
 * </ul>
 *
 * <h2>标签口径（原版标签，用户建议的那套）</h2>
 * <pre>
 *   树苗 30 ：#minecraft:saplings
 *   花   20 ：#minecraft:flowers（= small_flowers + tall_flowers + 花叶…） 以及 #minecraft:crops
 *   草    5 ：#minecraft:replaceable（原版"可被替换"标签：草/蕨/枯木/高草/海草/菌索…）
 * </pre>
 * ⚠ 诚实记两笔：
 * <ol>
 *   <li>1.20.1 <b>没有</b>用户笔记里猜的 {@code #minecraft:replaceable_plants} 这个标签 ✗
 *       （那一版的原版标签叫 {@code #minecraft:replaceable} / {@code #minecraft:replaceable_by_trees} ✓
 *       —— 已从官方 sources jar 的 {@code net/minecraft/tags/BlockTags.java} 与
 *       {@code data/minecraft/tags/blocks/replaceable.json} 核对 ✓）⇒ 这里用 {@code #minecraft:replaceable}
 *       再<b>扣掉</b>里面明显不是植物的那些（空气/水/岩浆/火/雪/光/结构空位…✓ 见 {@link #isPlantish} ✓）；</li>
 *   <li>顺序是 <b>树苗 → 花 → 草</b>（先特殊后通用 ✓）：{@code azalea} 同时属于 {@code #saplings} 与
 *       {@code #flowers}，按先命中算<b>树苗</b>（30）✓ —— 用户口径没写它归哪档，这是<b>我做的取舍</b>，
 *       要改就调换 {@link #plantCap} 里前两个 {@code if} 的顺序 ✓。</li>
 * </ol>
 *
 * <h2>高草/大花之类的"双高层"</h2>
 * 原版"高草/大蕨/向日葵/丁香/玫瑰丛/牡丹/瓶子草"都是 {@link DoublePlantBlock} —— 上下两半<b>同一株</b> ✓
 * ⇒ 只认 {@code half=lower} 那一半（上半跳过 ✗）否则会被算成两株、产出翻倍 ✗。
 *
 * <h2>⚠ 内存与性能</h2>
 * <ul>
 *   <li>Map 按<b>维度</b>分桶（{@code 维度 → 位置 → 点数}）✓ 某个维度一旦超过 {@value #MAX_ENTRIES_PER_LEVEL} 条
 *       （异常情况：玩家满世界种草 ✗）就<b>整桶丢掉</b> ✓ —— 凋灵度本来就是"内存态、丢了无所谓" ✓
 *       绝不让它变成内存泄漏 ✗；</li>
 *   <li>扫描只有 {@code 11×11×11} 的立方体里做一次球判 ⇒ 约 500 格方块状态查询 / 秒 / 台座 ✓
 *       （只在<b>台座还能装得下 EE</b> 时才跑 ✓ 见台座 {@code settle()} ✓）。</li>
 * </ul>
 *
 * <p>{@code simulate == true}（查速率 / tooltip）<b>绝不</b>涨凋灵度、<b>绝不</b>删方块 ✓
 * 只有台座每秒那次真结算（{@code simulate == false}）才动世界 ✓。
 */
public final class PlantEnergySource implements AmbientEnergySource {

    /** 配置允许清单里写的 id */
    public static final String ID = "plant";

    /** 每个维度最多记多少条凋灵度（超了整桶丢掉 ⇒ 防内存泄漏 ✓） */
    private static final int MAX_ENTRIES_PER_LEVEL = 100_000;

    /** 凋灵度：<b>维度 → 方块位置 → 已攒点数</b> ✓（内存态、不持久化 ✓ 玩家确认可接受 ✓） */
    private static final Map<ResourceKey<Level>, Map<BlockPos, Integer>> WITHERS = new ConcurrentHashMap<>();

    /** {@code half} 属性只需查一次（每个方块状态都有一份同名属性对象 ✓）缓存在这里 ✓ */
    private static Property<DoubleBlockHalf> halfProperty = null;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String shortName() {
        return "植物（凋灵度）";
    }

    @Override
    public String summary() {
        return "sphere r=5; +1 wither per plant per second; 1 point = 0.5 EE; cap grass 5 / flower 20 / sapling 30"
                + " -> the plant vanishes at its cap; crops count as flowers; wither rose excluded";
    }

    @Override
    public double eePerSecond(Level level, BlockPos pos, boolean simulate) {
        if (!(level instanceof ServerLevel server) || pos == null) return 0.0D;

        int radius = ModConfig.plantRadius();
        double perPoint = ModConfig.plantEePerPoint();
        if (radius <= 0 || perPoint <= 0.0D) return 0.0D;

        ResourceKey<Level> dimension = server.dimension();
        Map<BlockPos, Integer> withers = WITHERS.computeIfAbsent(dimension, k -> new HashMap<>());
        pruneIfHuge(dimension);
        int found = 0;

        // 球半径判定：整数平方比较 ⇒ 不用 sqrt、不产生浮点误差 ✓
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                int dx2dy2 = dx * dx + dy * dy;
                if (dx2dy2 > r2) continue;                     // 这一整条 y 都不在球里 ⇒ 连 dz 都不用试 ✓
                int maxDz = (int) Math.floor(Math.sqrt(r2 - dx2dy2));
                for (int dz = -maxDz; dz <= maxDz; dz++) {
                    BlockPos at = pos.offset(dx, dy, dz);
                    BlockState state = server.getBlockState(at);
                    int cap = plantCap(state);
                    if (cap <= 0) continue;
                    if (isUpperHalfOfDoublePlant(state)) continue;   // 双高层只算下半 ✓
                    found++;
                    if (!simulate) {
                        // 攒满 ⇒ 这一株消失（掉落物不掉 ✗：它是"被榨干"不是"被挖掉"✓）
                        if (tickWither(server, withers, at.immutable(), cap)) {
                            server.destroyBlock(at, false);
                        }
                    }
                }
            }
        }

        if (found == 0) return 0.0D;
        // 每秒每株 = +1 点凋灵度；1 点 = plant_ee_per_point（默认 0.5 EE）✓ 这里**不做任何持久化** ✓
        return perPoint * found;
    }

    /**
     * 给一株植物 +1 点凋灵度。
     *
     * @return {@code true} = 这一株刚好攒到上限（调用方负责让它消失 ✓）
     */
    private static boolean tickWither(ServerLevel level, Map<BlockPos, Integer> withers, BlockPos at, int cap) {
        int point = withers.getOrDefault(at, 0) + 1;
        if (point >= cap) {
            withers.remove(at);
            // 小反馈：一缕灵魂烟（只 3 颗、只在"消失"这一下 ✓ 不刷屏 ✗）
            level.sendParticles(ParticleTypes.SOUL,
                    at.getX() + 0.5D, at.getY() + 0.4D, at.getZ() + 0.5D,
                    3, 0.25D, 0.25D, 0.25D, 0.01D);
            return true;
        }
        withers.put(at, point);
        return false;
    }

    /**
     * 这一格是不是"可计凋灵度的植物"，是的话归哪一档（= 它的上限）。
     *
     * @return 上限点数（草 5 / 花 20 / 树苗 30）；{@code 0} = 不算植物 ✓
     */
    private static int plantCap(BlockState state) {
        if (state == null || state.isAir()) return 0;
        if (state.is(Blocks.WITHER_ROSE)) return 0;                  // 凋零玫瑰排除 ✓ 用户明确 ✓
        if (state.is(BlockTags.SAPLINGS)) return 30;                 // 树苗档 ✓
        if (state.is(BlockTags.FLOWERS)) return 20;                  // 花档（含小/高花、花叶…）✓
        if (state.is(BlockTags.CROPS)) return 20;                    // 作物算花档 ✓ 用户明确 ✓
        if (state.is(BlockTags.REPLACEABLE) && isPlantish(state)) return 5;   // 草档 ✓
        return 0;
    }

    /**
     * {@code #minecraft:replaceable} 里明显<b>不是</b>植物的那几类：空气 / 流体 / 火 / 雪 / 光 / 结构空位 / 藤蔓×
     * <p>⚠ 不用 {@code state.getFluidState().isEmpty()} 判流体：草方块之类的 {@code getFluidState()} 也是空 ✓
     * 但水/岩浆本身用 {@code is(Blocks.WATER/LAVA)} 更直白 ✓；也不用 {@code asItem().isEmpty()}
     * （水/岩浆有桶物品 ⇒ 判不出来 ✗）。
     */
    private static boolean isPlantish(BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR
                || block == Blocks.WATER || block == Blocks.LAVA
                || block == Blocks.FIRE || block == Blocks.SOUL_FIRE
                || block == Blocks.SNOW || block == Blocks.LIGHT
                || block == Blocks.STRUCTURE_VOID || block == Blocks.BUBBLE_COLUMN) {
            return false;
        }
        // 藤蔓/发光地衣/垂根：长在别的东西上的"附生物"，用户口径的"草 5"没点名它们 ⇒ 保守排除 ✓（诚实项 ✓）
        return block != Blocks.VINE && block != Blocks.GLOW_LICHEN && block != Blocks.HANGING_ROOTS;
    }

    /**
     * 是不是"双高层植物的上半"（高草/大蕨/向日葵/丁香/玫瑰丛/牡丹/瓶子草 ✓）。
     * <p>下半/普通方块 ⇒ {@code false} ✓；认不出来（属性对不上）⇒ 也当 {@code false} ✓
     * （宁可多算一株，也不要因为属性名变了就整条来源罢工 ✗）。
     */
    private static boolean isUpperHalfOfDoublePlant(BlockState state) {
        if (!(state.getBlock() instanceof DoublePlantBlock)) return false;
        Property<DoubleBlockHalf> property = halfProperty;
        if (property == null) {
            for (Property<?> candidate : state.getProperties()) {
                if (candidate.getValueClass() == DoubleBlockHalf.class) {
                    @SuppressWarnings("unchecked")
                    Property<DoubleBlockHalf> cast = (Property<DoubleBlockHalf>) candidate;
                    halfProperty = cast;
                    property = cast;
                    break;
                }
            }
        }
        if (property == null || !state.hasProperty(property)) return false;
        return state.getValue(property) == DoubleBlockHalf.UPPER;
    }

    // ============================================================
    //  调试 / 维护
    // ============================================================

    /** 当前记着的凋灵度条数（调试用 ✓ 手册/日志里可以拿它证明"内存态"✓） */
    public static int tracked() {
        int total = 0;
        for (Map<BlockPos, Integer> one : WITHERS.values()) total += one.size();
        return total;
    }

    /** 某个维度的桶是否已经超限 ⇒ 整桶丢掉（防内存泄漏 ✓ 丢的只是"内存态"数据 ✓） */
    static void pruneIfHuge(ResourceKey<Level> dimension) {
        Map<BlockPos, Integer> one = WITHERS.get(dimension);
        if (one != null && one.size() > MAX_ENTRIES_PER_LEVEL) {
            one.clear();
            TinkersNewlife.LOGGER.warn("[魔力台座] 维度 {} 的植物凋灵度记录超过 {} 条 ⇒ 已整桶清空（内存态数据，丢了无害 ✓）",
                    dimension.location(), MAX_ENTRIES_PER_LEVEL);
        }
    }

    /** 维度卸载 / 服务器停止时丢掉那一桶（由使用方按需调用 ✓ 目前只在超限时自动清 ✓） */
    public static void clear(ResourceKey<Level> dimension) {
        WITHERS.remove(dimension);
    }
}
