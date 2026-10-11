package com.mofengbaizhi.tinkersnewlife.content.menu;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity;
import slimeknights.tconstruct.tables.menu.TinkerStationContainerMenu;

/** §1310 便携工匠站菜单（幽灵方块版）。 */
public class PortableStationMenu extends TinkerStationContainerMenu {

    private final TinkerStationBlockEntity ghost;

    public PortableStationMenu(int id, Inventory inv, TinkerStationBlockEntity tile) {
        super(id, inv, tile);
        this.ghost = tile;
    }

    public PortableStationMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, rebuild(buf));
    }

    private static TinkerStationBlockEntity rebuild(FriendlyByteBuf buf) {
        com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携站·菜单] 客户端重建开始");
        CompoundTag nbt = null;
        try {
            nbt = buf.readNbt();
        } catch (Throwable ignored) {
        }
        Level level = null;
        try {
            level = net.minecraft.client.Minecraft.getInstance().level;
        } catch (Throwable ignored) {
        }
        TinkerStationBlockEntity be = com.mofengbaizhi.tinkersnewlife.content.item.PortableTinkerStationItem
                .createDetached(level);
        com.mofengbaizhi.tinkersnewlife.content.item.PortableTinkerStationItem.importInventory(be, nbt);   // §1311
        com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携站·菜单] 客户端重建完成 slots={}",
                be.getContainerSize());
        return be;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (ghost == null || player.level().isClientSide()) {
            return;
        }
        ItemStack holder = com.mofengbaizhi.tinkersnewlife.content.item.PortableTinkerStationItem.findIn(player);
        com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携站·关闭] 手里找到={} count={}",
                !holder.isEmpty(), holder.isEmpty() ? 0 : holder.getCount());
        if (!holder.isEmpty()) {
            com.mofengbaizhi.tinkersnewlife.content.item.PortableTinkerStationItem.saveInventory(holder, ghost);
            for (int i = 0; i < ghost.getContainerSize(); i++) {
                ghost.setItem(i, ItemStack.EMPTY);
            }
            return;
        }
        for (int i = 0; i < ghost.getContainerSize(); i++) {
            ItemStack s = ghost.getItem(i);
            if (!s.isEmpty()) {
                ghost.setItem(i, ItemStack.EMPTY);
                if (!player.getInventory().add(s)) {
                    player.drop(s, false);
                }
            }
        }
    }
}