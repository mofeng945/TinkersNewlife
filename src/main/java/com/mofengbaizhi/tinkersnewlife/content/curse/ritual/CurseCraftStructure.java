package com.mofengbaizhi.tinkersnewlife.content.curse.ritual;

import com.mofengbaizhi.tinkersnewlife.content.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
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
 * 第三层是**紧贴在第二层上方 1 格**：每面砖墙与矿石正上方各放一盏焦黑（或焦褐）灯笼，共 9 盏
 * —— 中间那盏是"核心位"（放古神物品 / 取产物），周围 8 盏是"材料位"。
 *
 * <p>⚠ 历史坑：最初把灯笼放在矿石上方 <b>2</b> 格，导致"从灯笼反推矿石"永远失败，
 * 表现就是"右键灯笼毫无反应"。现在 {@link #LANTERN_DY} = 1，反推时对 1/2 都兼容。
 */
public final class CurseCraftStructure {

    /** 灯笼相对矿石的高度差（第三层紧贴第二层上方） */
    public static final int LANTERN_DY = 1;

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

    /** 第一层（矿石下方 1 格）期望的方块；null = 不约束（中心 a 与四角 e） */
    private static Block layer1Block(int dx, int dz) {
        int adx = Math.abs(dx);
        int adz = Math.abs(dz);
        if (adx == 0 && adz == 0) return null;                                                  // a：调配台单独判定
        if (adx == 2 && adz == 2) return null;                                                  // e：四角任意
        if ((adx == 1 && adz == 0) || (adx == 0 && adz == 1)) return Blocks.OBSIDIAN;           // b：黑曜石
        if ((adx == 1 && adz == 1)
                || (adx == 2 && adz == 0) || (adx == 0 && adz == 2)) return Blocks.CRYING_OBSIDIAN;  // c：哭泣黑曜石
        if ((adx == 1 && adz == 2) || (adx == 2 && adz == 1)) return Blocks.SOUL_SAND;          // d：灵魂沙
        return null;
    }

    private enum Kind { ORE, WALL, SOUL_FIRE, AIR }

    /** 第二层（矿石所在层）期望的类型 */
    private static Kind layer2Kind(int dx, int dz) {
        int adx = Math.abs(dx);
        int adz = Math.abs(dz);
        if (adx == 0 && adz == 0) return Kind.ORE;
        if ((adx == 2 && adz == 0) || (adx == 0 && adz == 2) || (adx == 1 && adz == 1)) return Kind.WALL;   // b
        if ((adx == 1 && adz == 2) || (adx == 2 && adz == 1)) return Kind.SOUL_FIRE;                       // c
        return Kind.AIR;   // e：四角 (2,2) 与 (1,0)/(0,1) 都必须是空
    }

    /** 第三层需要灯笼的偏移（矿石正上方 + 各砖墙上方） */
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

    // ============================================================
    //  从灯笼反推矿石
    // ============================================================

    /** 找到的仪式中心：矿石坐标 + 灯笼相对矿石的高度差 */
    public record Anchor(BlockPos ore, int dy) {
        public BlockPos lantern(int dx, int dz) {
            return ore.offset(dx, dy, dz);
        }

        public BlockPos coreLantern() {
            return lantern(0, 0);
        }
    }

    /**
     * 从灯笼坐标反推矿石：水平偏移必是 {@link #lanternOffsets()} 之一；
     * 高度差按 {@link #LANTERN_DY} 优先，再兼容 2 格（少一层/多垫一层的搭法也能用）。
     */
    @Nullable
    public static Anchor findAnchor(Level level, BlockPos lanternPos) {
        for (int dy : new int[]{LANTERN_DY, 2}) {
            for (int[] off : lanternOffsets()) {
                BlockPos candidate = lanternPos.offset(-off[0], -dy, -off[1]);
                if (level.getBlockState(candidate).is(ModBlocks.GHELOTH_ORE.get())) {
                    return new Anchor(candidate, dy);
                }
            }
        }
        return null;
    }

    // ============================================================
    //  成型判定
    // ============================================================

    /** 结构是否成型（按标准高度差） */
    public static boolean isFormed(Level level, BlockPos orePos) {
        return isFormed(level, orePos, LANTERN_DY);
    }

    /** 结构是否成型（指定灯笼高度差） */
    public static boolean isFormed(Level level, BlockPos orePos, int lanternDy) {
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
            if (!isLantern(level.getBlockState(orePos.offset(off[0], lanternDy, off[1])))) return false;
        }
        return true;
    }

    /** 返回"第一处不符"的可读描述；null = 成型。用于右键灯笼时的排查提示。 */
    @Nullable
    public static String firstProblem(Level level, BlockPos orePos, int lanternDy) {
        if (!level.getBlockState(orePos).is(ModBlocks.GHELOTH_ORE.get())) {
            return "第二层 中心 应为 格赫罗斯矿石（现在是 " + name(level.getBlockState(orePos)) + "）";
        }
        BlockPos below = orePos.below();
        if (!isBlock(level, below, "tconstruct", "modifier_worktable")) {
            return "第一层 中心 应为 强化调配台（现在是 " + name(level.getBlockState(below)) + "）";
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Block expected = layer1Block(dx, dz);
                if (expected == null) continue;
                BlockPos pos = below.offset(dx, 0, dz);
                if (!level.getBlockState(pos).is(expected)) {
                    return "第一层 " + offset(dx, dz) + " 应为 " + expected.getName().getString()
                            + "（现在是 " + name(level.getBlockState(pos)) + "）";
                }
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos pos = orePos.offset(dx, 0, dz);
                BlockState state = level.getBlockState(pos);
                switch (layer2Kind(dx, dz)) {
                    case WALL -> {
                        if (!isBrickWall(state)) {
                            return "第二层 " + offset(dx, dz) + " 应为 焦黑砖墙（现在是 " + name(state) + "）";
                        }
                    }
                    case SOUL_FIRE -> {
                        if (!state.is(Blocks.SOUL_FIRE)) {
                            return "第二层 " + offset(dx, dz) + " 应为 灵魂火（在灵魂沙上点火，现在是 " + name(state) + "）";
                        }
                    }
                    default -> {
                        if (!state.isAir()) {
                            return "第二层 " + offset(dx, dz) + " 应为空（现在是 " + name(state) + "）";
                        }
                    }
                }
            }
        }
        for (int[] off : lanternOffsets()) {
            BlockPos pos = orePos.offset(off[0], lanternDy, off[1]);
            if (!isLantern(level.getBlockState(pos))) {
                boolean core = isCoreOffset(off[0], off[1]);
                return "第三层 " + offset(off[0], off[1]) + (core ? "（矿石正上方，紧贴矿石那一格）" : "")
                        + " 应为 焦黑灯笼（现在是 " + name(level.getBlockState(pos)) + "）";
            }
        }
        return null;
    }

    private static String offset(int dx, int dz) {
        return String.format(java.util.Locale.ROOT, "(%+d,%+d)", dx, dz);
    }

    private static String name(BlockState state) {
        return state.isAir() ? "空气" : state.getBlock().getName().getString();
    }

    /** 材料位灯笼坐标（不含核心位） */
    public static List<BlockPos> materialLanterns(Anchor anchor) {
        List<BlockPos> list = new ArrayList<>();
        for (int[] off : lanternOffsets()) {
            if (isCoreOffset(off[0], off[1])) continue;
            list.add(anchor.lantern(off[0], off[1]));
        }
        return list;
    }

    /** 聚合物中心：矿石上方 3 格 */
    public static Vec3 convergePoint(BlockPos orePos) {
        return new Vec3(orePos.getX() + 0.5, orePos.getY() + 3.2, orePos.getZ() + 0.5);
    }

    /** 结构检测范围（找仪式里的悬浮物等） */
    public static AABB bounds(BlockPos orePos) {
        return new AABB(orePos).inflate(6.0);
    }
}
