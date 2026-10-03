package com.mofengbaizhi.tinkersnewlife.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 墨封白织fufu 的方块实体（§880）：本身不存数据 ✓ 只是给"玩家模型渲染器"当载体 ✓ */
public class FumoMoBlockEntity extends BlockEntity {

    public FumoMoBlockEntity(BlockPos pos, BlockState state) {
        super(FumoMoDoll.FUMO_MO_BE.get(), pos, state);
    }
}
