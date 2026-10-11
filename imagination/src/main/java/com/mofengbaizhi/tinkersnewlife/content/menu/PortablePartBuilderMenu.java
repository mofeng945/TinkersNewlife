package com.mofengbaizhi.tinkersnewlife.content.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import slimeknights.tconstruct.tables.block.entity.table.PartBuilderBlockEntity;
import slimeknights.tconstruct.tables.menu.PartBuilderContainerMenu;

/** §1322 便携台子菜单（PortablePartBuilderMenu）✗ stillValid 恒真 ＋ 关闭时写回**玩家存档** ✓。 */
public class PortablePartBuilderMenu extends slimeknights.tconstruct.tables.menu.PartBuilderContainerMenu {

    private final slimeknights.tconstruct.tables.block.entity.table.PartBuilderBlockEntity ghost;
    private final com.mofengbaizhi.tinkersnewlife.content.item.PortableTableItem.Kind kind;

    public PortablePartBuilderMenu(int id, Inventory inv, slimeknights.tconstruct.tables.block.entity.table.PartBuilderBlockEntity tile) {
        super(id, inv, tile);
        com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携设备] 槽数（菜单）{} side={} slots={}", this.getClass().getSimpleName(), "?" + "", this.slots.size());        this.ghost = tile;
        this.kind = com.mofengbaizhi.tinkersnewlife.content.item.PortableTableItem.Kind.PART_BUILDER;
    }

    public PortablePartBuilderMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, (slimeknights.tconstruct.tables.block.entity.table.PartBuilderBlockEntity) com.mofengbaizhi.tinkersnewlife.content.item.PortableTableItem
                .makeGhost(com.mofengbaizhi.tinkersnewlife.content.item.PortableTableItem.Kind.PART_BUILDER));
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        try {
            super.removed(player);
        } catch (Throwable ignored) {
        }
        try {
            if (ghost != null && !player.level().isClientSide()) {
                com.mofengbaizhi.tinkersnewlife.content.item.PortableTableItem.storeToPlayer(player, ghost, kind);
            }
        } catch (Throwable ignored) {
        }
    }
}