package com.mofengbaizhi.tinkersnewlife.content.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import slimeknights.tconstruct.tables.block.entity.table.ModifierWorktableBlockEntity;
import slimeknights.tconstruct.tables.menu.ModifierWorktableContainerMenu;

/** §1322 便携台子菜单（PortableModifierWorktableMenu）✗ stillValid 恒真 ＋ 关闭时写回**玩家存档** ✓。 */
public class PortableModifierWorktableMenu extends slimeknights.tconstruct.tables.menu.ModifierWorktableContainerMenu {

    private final slimeknights.tconstruct.tables.block.entity.table.ModifierWorktableBlockEntity ghost;
    private final com.mofengbaizhi.tinkersnewlife.content.item.PortableTableItem.Kind kind;

    public PortableModifierWorktableMenu(int id, Inventory inv, slimeknights.tconstruct.tables.block.entity.table.ModifierWorktableBlockEntity tile) {
        super(id, inv, tile);
        com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携设备] 槽数（菜单）{} side={} slots={}", this.getClass().getSimpleName(), "?" + "", this.slots.size());        this.ghost = tile;
        this.kind = com.mofengbaizhi.tinkersnewlife.content.item.PortableTableItem.Kind.MODIFIER_WORKTABLE;
    }

    public PortableModifierWorktableMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, (slimeknights.tconstruct.tables.block.entity.table.ModifierWorktableBlockEntity) com.mofengbaizhi.tinkersnewlife.content.item.PortableTableItem
                .makeGhost(com.mofengbaizhi.tinkersnewlife.content.item.PortableTableItem.Kind.MODIFIER_WORKTABLE));
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