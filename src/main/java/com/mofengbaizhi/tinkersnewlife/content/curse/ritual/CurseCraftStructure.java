package com.mofengbaizhi.tinkersnewlife.content.curse.ritual;

import com.mofengbaizhi.tinkersnewlife.content.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * 咒力合成仪式的多方块结构（5×5×3，以「格赫罗斯矿石」为第二层中心）。
 *
 * <p>符号：a = 中心，b = 石砖墙（焦黑/焦褐皆可），c = 灵魂火，d = 灵魂沙，e = 任意/空。
 *
 * <pre>
 * 第一层（矿石下方 1 格，中心是强化调配台）：   第二层（矿石所在层）：
 *   e d c d e                                  e c b c e
 *   d c b c d                                  c b e b c
 *   c b a b c                                  b e a e b
 *   d c b c d                                  c b e b c
 *   e d c d e                                  e c b c e
 * </pre>
 *
 * 第三层：所有石砖墙与矿石正上方各放一盏焦黑（或焦褐）灯笼，共 9 盏
 * —— 中间那盏是"核心位"（放古神物品 / 取产物），周围 8 盏是"材料位"。
 */
public final class CurseCraftStructure {

    private CurseCraftStructure() {
    }

    /** 焦黑/焦褐灯笼 */
    public static boolean isLantern(BlockState state) {
        return isBlock(state, "tconstruct", "seared_lantern")
                || isBlock(state, "tconstruct", "scorched_lantern");
    }

    /** 焦黑/焦褐砖墙 */
    public static boolean isBrickWall(BlockState state) {
        return isBlock(state, "tconstruct", "seared_bricks_wall")
                || isBlock(state, "tconstruct", "scorched_bricks_wall");
    }

    private static boolean isBlock(BlockState state, String namespace, String path) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(namespace, path));
        return block != null && state.is(block);
    }

    private static boolean isBlock(Level level, BlockPos pos, String namespace, String path) {
        return isBlock(level.getBlockState(pos), namespace, path);
    }

    // ============================================================
    //  两层图案
    // ============================================================

    /** 第一层（矿石下方 1 格）期望的方块；null = 不约束（四角 e） */
    private static Block layer1Block(int dx, int dz) {
        int adx = Math.abs(dx);
        int adz = Math.abs(dz);
        if (adx == 0 && adz == 0) return null;                                                   // a：调配台单独判定
        if ((adx == 1 && adz == 0) || (adx == 0 && adz == 1)) return Blocks.OBSIDIAN;            // b
        if (adx == adz) return Blocks.CRYING_OBSIDIAN;                                           // c（内对角 (1,1)）
        if ((adx == 2 && adz == 0) || (adx == 0 && adz == 2)) return Blocks.CRYING_OBSIDIAN;     // c（边中点）
        if ((adx == 1 && adz == 2) || (adx == 2 && adz == 1)) return Blocks.SOUL_SAND;           // d
        return null;                                                                             // e（四角）
    }

    private enum Kind { ORE, WALL, SOUL_FIRE, AIR }

    /** 第二层（矿石所在层）期望的类型 */
    private static Kind layer2Kind(int dx, int dz) {
        int adx = Math.abs(dx);
        int adz = Math.abs(dz);
        if (adx == 0 && adz == 0) return Kind.ORE;
        if ((adx == 2 && adz == 0) || (adx == 0 && adz == 2) || (adx == 1 && adz == 1)) return Kind.WALL;
        if ((adx == 1 && adz == 2) || (adx == 2 && adz == 1) || (adx == 2 && adz == 2)) return Kind.SOUL_FIRE;
        return Kind.AIR;
    }

    /** 第三层需要灯笼的偏移（矿石正上方 + 各石砖墙上方） */
    public static List<int[]> lanternOffsets() {
        List<int[]> list = new ArrayList<>();
        list.add(new int[]{0, 0});
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (layer2Kind(dx, dz) == Kind.WALL) list.add(new int[]{dx, dz});
            }
        }
        return list;
    }

    /** 该偏移是不是核心位（矿石正上方那盏灯） */
    public static boolean isCoreOffset(int dx, int dz) {
        return dx == 0 && dz == 0;
    }

    /**
     * 从灯笼坐标反推矿石坐标：灯笼恒在矿石上方 2 格，水平偏移必是
     * {@link #lanternOffsets()} 之一，逐个候选试即可（并要求那里确实是格赫罗斯矿石）。
     */
    public static BlockPos oreFromLantern(Level level, BlockPos lanternPos) {
        for (int[] off : lanternOffsets()) {
            BlockPos candidate = lanternPos.offset(-off[0], -2, -off[1]);
            if (level.getBlockState(candidate).is(ModBlocks.GHELOTH_ORE.get())) return candidate;
        }
        return null;
    }

    /** 结构是否成型 */
    public static boolean isFormed(Level level, BlockPos orePos) {
        if (!level.getBlockState(orePos).is(ModBlocks.GHELOTH_ORE.get())) return false;

        BlockPos below = orePos.below();
        if (!isBlock(level, below, "tconstruct", "modifier_worktable")) return false;   // 强化调配台
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Block expected = layer1Block(dx, dz);
                if (expected == null) continue;
                if (!level.getBlockState(below.offset(dx, 0, dz)).is(expected)) return false;
            }
        }

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockState state = level.getBlockState(orePos.offset(dx, 0, dz));
                switch (layer2Kind(dx, dz)) {
                    case WALL -> {
                        if (!isBrickWall(state)) return false;
                    }
                    case SOUL_FIRE -> {
                        if (!state.is(Blocks.SOUL_FIRE)) return false;
                    }
                    default -> {
                        if (!state.isAir()) return false;
                    }
                }
            }
        }

        for (int[] off : lanternOffsets()) {
            if (!isLantern(level.getBlockState(orePos.offset(off[0], 2, off[1])))) return false;
        }
        return true;
    }

    /** 材料位灯笼坐标（不含核心位） */
    public static List<BlockPos> materialLanterns(BlockPos orePos) {
        List<BlockPos> list = new ArrayList<>();
        for (int[] off : lanternOffsets()) {
            if (isCoreOffset(off[0], off[1])) continue;
            list.add(orePos.offset(off[0], 2, off[1]));
        }
        return list;
    }

    /** 核心位灯笼坐标 */
    public static BlockPos coreLantern(BlockPos orePos) {
        return orePos.offset(0, 2, 0);
    }

    /** 聚合物中心：矿石上方 3 格 */
    public static Vec3 convergePoint(BlockPos orePos) {
        return new Vec3(orePos.getX() + 0.5, orePos.getY() + 3.2, orePos.getZ() + 0.5);
    }

    /** 结构检测范围（用于找仪式里的悬浮物等） */
    public static net.minecraft.world.phys.AABB bounds(BlockPos orePos) {
        return new net.minecraft.world.phys.AABB(orePos).inflate(6.0);
    }
}
