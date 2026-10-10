package com.mofengbaizhi.tinkersnewlife.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity;

/** §1280 便携工匠站的"实体化"方块实体：真在世界里（客户端能按坐标找到它）。 */
public class InvisibleStationBlockEntity extends TinkerStationBlockEntity {

    public static final int SLOTS = 6;

    public InvisibleStationBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state, SLOTS);
    }

    @Override
    public AbstractContainerMenu createMenu(int menuId, Inventory playerInventory, Player playerEntity) {
        return new com.mofengbaizhi.tinkersnewlife.content.menu.PortableStationMenu(menuId, playerInventory, this);
    }
}