package com.mofengbaizhi.tinkersnewlife.content.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import slimeknights.tconstruct.tables.block.entity.chest.AbstractChestBlockEntity;
import slimeknights.tconstruct.tables.menu.TinkerChestContainerMenu;

/** §1321 便携箱菜单（幽灵实体）：始终可用 ＋ 关闭时把内容写回**玩家存档**。 */
public class PortableChestMenu extends TinkerChestContainerMenu {

    private final AbstractChestBlockEntity ghost;
    private final com.mofengbaizhi.tinkersnewlife.content.item.PortableChestItem.Kind kind;

    public PortableChestMenu(int id, Inventory inv, AbstractChestBlockEntity tile,
                             com.mofengbaizhi.tinkersnewlife.content.item.PortableChestItem.Kind kind) {
        super(id, inv, tile);
        com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携设备] 槽数（菜单）{} side={} slots={}", this.getClass().getSimpleName(), "?" + "", this.slots.size());        this.ghost = tile;
        this.kind = kind;
    }

    public PortableChestMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, (slimeknights.tconstruct.tables.block.entity.chest.AbstractChestBlockEntity) com.mofengbaizhi.tinkersnewlife.content.item.PortableChestItem.makeGhost(
                com.mofengbaizhi.tinkersnewlife.content.item.PortableChestItem.Kind.CAST_CHEST),
                com.mofengbaizhi.tinkersnewlife.content.item.PortableChestItem.Kind.CAST_CHEST);
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
                com.mofengbaizhi.tinkersnewlife.content.item.PortableChestItem.storeToPlayer(player, ghost, kind);
            }
        } catch (Throwable ignored) {
        }
    }
}