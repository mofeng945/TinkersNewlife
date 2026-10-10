package com.mofengbaizhi.tinkersnewlife.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import slimeknights.tconstruct.tables.block.TinkersAnvilBlock;

/** §1280 完全透明的工匠砧：便携站在使用时把它放在玩家脚下，用完即拆。 */
public class InvisibleStationBlock extends TinkersAnvilBlock {

    public InvisibleStationBlock(Properties builder) {
        super(builder, InvisibleStationBlockEntity.SLOTS);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InvisibleStationBlockEntity(pos, state);
    }
}