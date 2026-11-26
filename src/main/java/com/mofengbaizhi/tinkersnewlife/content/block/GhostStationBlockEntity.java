package com.mofengbaizhi.tinkersnewlife.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity;

/**
 * §1314 便携工匠站的"幽灵方块实体"（★真实体 ✗ ⭐ 不入世界 ✓）。
 *
 * <p>为什么要这个子类：菜单每 tick 都会校验 {@code stillValid(player)}，
 * 而父类按"玩家到方块的距离"判断 ⇒ 幽灵在 (0,-1024,0) ⇒ 距离 1024 格 ⇒ 永远 false
 * ⇒ 服务端立刻关闭界面（用户看到的就是"界面只开一瞬间"）。
 *
 * <p>这里恒返回 true ⇒ 界面才能开住 ✓（★仅用于便携站 ✗ ⭐ 不影响世界里真实的工匠站 ✓）
 */
public class GhostStationBlockEntity extends TinkerStationBlockEntity {

    /** 与真实工匠站一致：6 个槽（★工具 ＋ 5 个材料 ✓） */
    public static final int SLOTS = 6;

    private static boolean warned = false;

    public GhostStationBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state, SLOTS);
    }

    @Override
    public boolean stillValid(Player player) {
        if (!warned) {
            warned = true;
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携站·菜单] 幽灵实体 stillValid 被调用 ⇒ 恒真 ✓");
        }
        return true;
    }
}