package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModBlocks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 调试指令：<b>找最近的古老者水晶矿</b>（用户口径：「能不能给个指令让我快速定位最近的古老者水晶矿」✓）。
 *
 * <pre>
 * /tinkersnewlife locate elder_crystal [半径]      // 半径默认 48 格，上限 200
 * </pre>
 *
 * <h2>为什么原版没法用</h2>
 * 原版 `/locate` 只有 {@code structure} / {@code biome} / {@code poi} ✗ —— 找不了方块 ✗。
 * 所以这里自己扫：以执行者所在位置为中心，**只扫已生成的区块**（{@code hasChunkAt} ⇒ **不会触发区块生成** ✓ 不会卡服 ✓），
 * Y 只扫 {@code minBuildHeight ~ 64}（我们的矿只在 −64 ~ −1 ✓ 往上扫是白费 ✗），取最近的一块。
 *
 * <h2>找不到时给的路子</h2>
 * 提示玩家：矿石**只在深暗之域**生成 ⇒ 先用 {@code /locate biome minecraft:deep_dark} 找群系 ✓
 * （那条是原版指令，找群系比扫方块快得多 ✓ 两者配合最省事 ✓）。
 *
 * <p>权限：**一律要权限 2（OP）** ✓（§507 用户口径：所有指令都要权限 ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ElderCrystalLocateCommand {

    private ElderCrystalLocateCommand() {}

    /** 默认扫描半径（格）—— 48 格 ≈ 3 个区块，够近又不至于太慢 ✓ */
    private static final int DEFAULT_RADIUS = 48;
    /** 半径上限 —— 扫方块是 O(r²) ⇒ 封顶防手滑卡顿 ✓ */
    private static final int MAX_RADIUS = 200;
    /** Y 扫描上限：我们的矿只在 −64 ~ −1 生成 ⇒ 64 已经留足余量 ✓ */
    private static final int Y_SCAN_TOP = 64;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tinkersnewlife")
                .then(Commands.literal("locate")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("elder_crystal")
                                .executes(ctx -> locate(ctx.getSource(), DEFAULT_RADIUS))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(8, MAX_RADIUS))
                                        .executes(ctx -> locate(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "radius")))))));
    }

    private static int locate(CommandSourceStack source, int radius) {
        ServerLevel level = source.getLevel();
        BlockPos center = BlockPos.containing(source.getPosition());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        int scannedColumns = 0;
        int yTop = Math.min(level.getMaxBuildHeight() - 1, Y_SCAN_TOP);

        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                // 只扫已生成的区块 ⇒ 绝不触发区块生成（否则一条指令就能把服务器卡住 ✗）
                if (!level.hasChunkAt(cursor.set(x, center.getY(), z))) continue;
                scannedColumns++;
                for (int y = level.getMinBuildHeight(); y <= yTop; y++) {
                    cursor.set(x, y, z);
                    if (!level.getBlockState(cursor).is(ModBlocks.ELDER_CRYSTAL_ORE.get())) continue;
                    double d = center.distSqr(cursor);
                    if (d < bestSq) {
                        bestSq = d;
                        best = cursor.immutable();
                    }
                }
            }
        }

        // lambda 捕获要求 effectively final ✗ ⇒ 先落地一个 final 副本（循环里改过的是 scannedColumns ✓）
        final int scanned = scannedColumns;

        if (best == null) {
            source.sendSuccess(() -> Component.literal("§b[古老者水晶] §f在 " + radius
                    + " 格内（只算已生成的区块，" + scanned + " 列）§c没找到矿石§f。"
                    + "它**只在深暗之域生成** ⇒ 先用 §e/locate biome minecraft:deep_dark§f 找群系，"
                    + "或把半径调大（最大 " + MAX_RADIUS + "）"), false);
            return 0;
        }

        BlockPos found = best;
        int dist = (int) Math.round(Math.sqrt(bestSq));
        source.sendSuccess(() -> Component.literal("§b[古老者水晶] §f最近的水晶矿：§e"
                + found.getX() + " " + found.getY() + " " + found.getZ()
                + "§f（§a" + dist + " 格§f，方向 §e" + direction(center, found) + "§f；已扫 "
                + scanned + " 列）"), false);
        return dist;
    }

    /** 八向中文方位（看水平偏移就够 ⇒ 不看 Y ✓） */
    private static String direction(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double angle = Math.toDegrees(Math.atan2(dx, -dz));   // 0 = 北（-Z ✓）
        if (angle < 0) angle += 360.0D;
        String[] names = { "北", "东北", "东", "东南", "南", "西南", "西", "西北" };
        int index = (int) Math.round(angle / 45.0D) % 8;
        return names[index];
    }
}
