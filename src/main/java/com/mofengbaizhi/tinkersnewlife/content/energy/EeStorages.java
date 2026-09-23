package com.mofengbaizhi.tinkersnewlife.content.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import javax.annotation.Nullable;

/**
 * {@link EeStorage} 的<b>邻居扫描 / 搬运</b>助手（§557）。
 *
 * <h2>为什么单独一个类</h2>
 * 抽取方块与转化器都要做同样三件事：<b>按固定顺序遍历邻格 → 找到 EE 容器 → 抽/推</b> ✓
 * 写成一份，两个方块（以及以后任何"EE 管道"）都调它 ✓ 免得两边各写一套后行为悄悄分叉 ✗。
 *
 * <h2>方向顺序</h2>
 * 与魔力台座 §523 起就有的 {@code NEIGHBOURS} <b>逐字一致</b> ✓：
 * <b>上 → 下 → 北 → 南 → 西 → 东</b>（上/下优先 = "离台座上的水晶最近"那个直觉 ✓）。
 * 固定顺序的意义：玩家可预期、调试可复现 ✓（不排序、不随机 ✗）。
 */
public final class EeStorages {

    private EeStorages() {
    }

    /** 邻格遍历顺序（与 {@code ElderManaPedestalBlockEntity#NEIGHBOURS} 同一份口径 ✓） */
    public static final Direction[] NEIGHBOURS = {
            Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    /**
     * §587 <b>这格是不是"万用能量转化器"</b>（靠它独有的 {@code converter_facing} 属性判定 ✓ 不引用方块实体类型 ✓）。
     * <p>用于：EE 只在转化器的**顶面 + 底面**进出 ✓（用户六面口径 ✓）。
     */
    public static boolean isConverter(Level level, BlockPos pos) {
        return level != null && level.getBlockState(pos)
                .hasProperty(com.mofengbaizhi.tinkersnewlife.content.block.EnergyConverterBlock.FACING);
    }

    /** 邻格的 EE 容器（不是 EE 容器 ⇒ null ✓；未加载/客户端也安全 ✓） */
    @Nullable
    public static EeStorage at(Level level, BlockPos pos) {
        if (level == null) return null;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof EeStorage storage) return storage;
        // §563 转化器的 BE 本身不是 EeStorage（只有内部 core 是 ✗）⇒ 中立兜底 ✓
        if (be instanceof com.mofengbaizhi.tinkersnewlife.content.block.ConverterCoreHolder holder) {
            return holder.converterCore();
        }
        return null;
    }

    /**
     * 把 {@code pos} 周围（上/下/四邻）能给的 EE <b>抽进</b> {@code baseSink}，
     * <b>总共最多抽 {@code totalLimit}</b> 点（跨多个邻居合计 ✓ 固定顺序 ⇒ 可预期 ✓）。
     *
     * @param sinkFor 决定"这一次实际往哪个容器里放"的函数：入参
     *                {@code (邻居坐标, baseSink)}，出参是真正执行 {@code insertEe} 的那个容器；
     *                返回 null = 这个邻居跳过 ✓。
     *                <p>为什么需要它：抽取方块有一条"<b>跳过这一步刚抽过的那个邻居</b>"的防空转规则
     *                （见 {@code EeExtractorBlockEntity}）⇒ 它需要一个<b>回调点</b>知道
     *                "这批电是从哪来的" ✓。把 {@link BlockPos} 塞进 {@link EeStorage} 接口是不行的 ✗
     *                （接口要跟世界解耦 ✓）⇒ 于是把这点差异挤到这一个函数参数里 ✓，
     *                普通调用方直接用 {@link #DIRECT} 即可 ✓（转化器就是这么用的）。
     * @return 实际抽到的总量（EE）
     */
    public static int pullAround(Level level, BlockPos pos, EeStorage baseSink, int totalLimit,
                                 BiFunction<BlockPos, EeStorage, EeStorage> sinkFor) {
        if (level == null || baseSink == null || totalLimit <= 0) return 0;
        // §599 性能：把"我自己是不是转化器 / 我自己的状态"提到循环外 ✓
        //   （原来这两句在 6 个方向上各查一遍 ✗ = 12 次区块查询/tick/方块 ✗）
        final boolean selfIsConverter = isConverter(level, pos);
        final net.minecraft.world.level.block.state.BlockState selfState =
                selfIsConverter ? level.getBlockState(pos) : null;
        int left = totalLimit;
        int moved = 0;
        for (Direction d : NEIGHBOURS) {
            // §591 **我自己是转化器 ⇒ 只能用我自己的顶面/底面** ✗ —— 用户实测「抽取器放侧面，EE 还是被推进/抽进去了」✗
    //   根因：§587 只挡住了"别人往转化器里推"✗，没挡"**转化器主动从相邻容器抽**"✗
    //   （转化器每 tick 会 `pullAround` ✓ 六个面都抽 ✗）⇒ 这里按**我自己**的面角色再挡一道 ✓。
    if (selfIsConverter
            && !com.mofengbaizhi.tinkersnewlife.content.block.EnergyConverterFaces.allowsEe(selfState, d)) continue;    // §587 邻居是转化器 ⇒ EE 只在它的顶面/底面进出 ✓（方向要取反 ✓ 从邻居视角看）
            if (left <= 0) break;
            BlockPos at = pos.relative(d);
            // §599 性能：这一格的状态**只查一次** ✓（原来"判邻居是不是转化器"和"取状态喂给 allowsEe"各查一次 ✗）
            final net.minecraft.world.level.block.state.BlockState nState = level.getBlockState(at);
            if (nState.hasProperty(com.mofengbaizhi.tinkersnewlife.content.block.EnergyConverterBlock.FACING)
                    && !com.mofengbaizhi.tinkersnewlife.content.block.EnergyConverterFaces.allowsEe(nState, d.getOpposite())) continue;
            EeStorage src = at(level, at);   /* §563 统一走查找 ✓ */ if (src == null || src == baseSink) continue;
            if (src.isEmpty()) continue;
            EeStorage sink = sinkFor == null ? baseSink : sinkFor.apply(at, baseSink);
            if (sink == null) continue;
            // 先问"你能给多少、我这还装得下多少"，两者取小 ⇒ 不会出现"抽出来装不下"的空中蒸发 ✗
            int want = Math.min(left, src.extractEe(left, true));
            if (want <= 0) continue;
            int room = sink.insertEe(want, true);
            int take = Math.min(want, room);
            if (take <= 0) continue;
            int got = src.extractEe(take, false);
            if (got <= 0) continue;
            int accepted = sink.insertEe(got, false);
            if (accepted < got) {
                // 理论上不会发生（上面 simulate 过）⇒ 真发生了就把多出来的还回去 ✓ 绝不吞 ✗
                src.insertEe(got - accepted, false);
            }
            moved += accepted;
            left -= accepted;
        }
        return moved;
    }

    /** {@link #pullAround} 的"不关心来源"版本（放进去就等于直接进 baseSink ✓ 转化器用的就是它） */
    public static final BiFunction<BlockPos, EeStorage, EeStorage> DIRECT = (at, sink) -> sink;

    /**
     * 把 {@code source} 里最多 {@code totalLimit} 点 EE <b>推给</b>周围（上/下/四邻）的 EE 容器。
     *
     * @param skip 要跳过的方块实体（抽取方块用它排掉"这一步刚抽过的那些邻居"——
     *             "从 A 抽出来、又还回 A"是纯粹的空转 ✗）；可为 null
     * @return 实际推出去的 EE
     */
    public static int pushAround(Level level, BlockPos pos, EeStorage source, int totalLimit,
                                 @Nullable Predicate<BlockEntity> skip) {
        if (level == null || source == null || totalLimit <= 0) return 0;
        // §599 性能：同 pullAround ✓（自我判定提到循环外 ✓）
        final boolean selfIsConverter = isConverter(level, pos);
        final net.minecraft.world.level.block.state.BlockState selfState =
                selfIsConverter ? level.getBlockState(pos) : null;
        int left = Math.min(totalLimit, source.getEe());
        int moved = 0;
        for (Direction d : NEIGHBOURS) {
            // §591 **我自己是转化器 ⇒ 只能用我自己的顶面/底面** ✗ —— 用户实测「抽取器放侧面，EE 还是被推进/抽进去了」✗
    //   根因：§587 只挡住了"别人往转化器里推"✗，没挡"**转化器主动从相邻容器抽**"✗
    //   （转化器每 tick 会 `pullAround` ✓ 六个面都抽 ✗）⇒ 这里按**我自己**的面角色再挡一道 ✓。
    if (selfIsConverter
            && !com.mofengbaizhi.tinkersnewlife.content.block.EnergyConverterFaces.allowsEe(selfState, d)) continue;    // §587 邻居是转化器 ⇒ EE 只在它的顶面/底面进出 ✓（方向要取反 ✓ 从邻居视角看）
            if (left <= 0) break;
            BlockPos at = pos.relative(d);
            final net.minecraft.world.level.block.state.BlockState nState = level.getBlockState(at);   // §599 只查一次 ✓
            if (nState.hasProperty(com.mofengbaizhi.tinkersnewlife.content.block.EnergyConverterBlock.FACING)
                    && !com.mofengbaizhi.tinkersnewlife.content.block.EnergyConverterFaces.allowsEe(nState, d.getOpposite())) continue;
            BlockEntity be = level.getBlockEntity(at);   // §599 这个 `be` 是给下面的 skip 用的 ✓（必需 ✓）
            EeStorage dst = at(level, at);   /* §563 同上 ✓ */ if (dst == null || dst == source) continue;
            if (skip != null && skip.test(be)) continue;   // §557 防空转：刚抽过的那些不还回去 ✓
            if (dst.getSpace() <= 0) continue;
            int want = Math.min(left, source.extractEe(left, true));
            if (want <= 0) break;                         // 自己也没货了 ⇒ 后面不用再看 ✓
            int accepted = dst.insertEe(want, true);
            if (accepted <= 0) continue;
            int got = source.extractEe(accepted, false);
            if (got <= 0) continue;
            int really = dst.insertEe(got, false);
            if (really < got) source.insertEe(got - really, false);   // 还回去 ✓
            moved += really;
            left -= really;
        }
        return moved;
    }

    /** 周围（上/下/四邻）所有 EE 容器（**按固定顺序** ✓ 只读快照 ✓ 供 tooltip / 调试用 ✓） */
    public static List<EeStorage> neighbours(Level level, BlockPos pos) {
        List<EeStorage> found = new ArrayList<>(NEIGHBOURS.length);
        if (level == null) return found;
        // §599 性能：自我判定提到循环外 ✓
        final boolean selfIsConverter = isConverter(level, pos);
        final net.minecraft.world.level.block.state.BlockState selfState =
                selfIsConverter ? level.getBlockState(pos) : null;
        for (Direction d : NEIGHBOURS) {
            // §591 **我自己是转化器 ⇒ 只能用我自己的顶面/底面** ✗ —— 用户实测「抽取器放侧面，EE 还是被推进/抽进去了」✗
    //   根因：§587 只挡住了"别人往转化器里推"✗，没挡"**转化器主动从相邻容器抽**"✗
    //   （转化器每 tick 会 `pullAround` ✓ 六个面都抽 ✗）⇒ 这里按**我自己**的面角色再挡一道 ✓。
    if (selfIsConverter
            && !com.mofengbaizhi.tinkersnewlife.content.block.EnergyConverterFaces.allowsEe(selfState, d)) continue;    // §587 邻居是转化器 ⇒ EE 只在它的顶面/底面进出 ✓（方向要取反 ✓ 从邻居视角看）
            BlockPos at = pos.relative(d);
            final net.minecraft.world.level.block.state.BlockState nState = level.getBlockState(at);   // §599 只查一次 ✓
            if (nState.hasProperty(com.mofengbaizhi.tinkersnewlife.content.block.EnergyConverterBlock.FACING)
                    && !com.mofengbaizhi.tinkersnewlife.content.block.EnergyConverterFaces.allowsEe(nState, d.getOpposite())) continue;
            EeStorage s = at(level, at);
            if (s != null) found.add(s);
        }
        return found;
    }
}
