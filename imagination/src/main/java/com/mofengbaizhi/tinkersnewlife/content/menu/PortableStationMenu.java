package com.mofengbaizhi.tinkersnewlife.content.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity;
import slimeknights.tconstruct.tables.menu.TinkerStationContainerMenu;

/** §1280 便携工匠站菜单：方块实体真在世界里；关闭时把内容写回物品 NBT 并拆掉那块透明砧。 */
public class PortableStationMenu extends TinkerStationContainerMenu {

    private final TinkerStationBlockEntity station;
    private final BlockPos stationPos;

    public PortableStationMenu(int id, Inventory inv, TinkerStationBlockEntity tile) {
        super(id, inv, tile);
        this.station = tile;
        this.stationPos = tile == null ? null : tile.getBlockPos();
    }

    /** 客户端：buf 里只有坐标（NetworkHooks 写的）⇒ 去客户端世界把那个方块实体找出来。 */
    public PortableStationMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, resolve(inv, buf.readBlockPos()));
    }

    private static TinkerStationBlockEntity resolve(Inventory inv, BlockPos pos) {
        try {
            net.minecraft.world.level.Level level = net.minecraft.client.Minecraft.getInstance().level;
            if (level != null && level.getBlockEntity(pos) instanceof TinkerStationBlockEntity be) {
                return be;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (station == null || player.level().isClientSide()) {
            return;
        }
        ItemStack holder = com.mofengbaizhi.tinkersnewlife.content.item.PortableTinkerStationItem.findIn(player);
        if (!holder.isEmpty()) {
            com.mofengbaizhi.tinkersnewlife.content.item.PortableTinkerStationItem.saveInventory(holder, station);
        } else {
            for (int i = 0; i < station.getContainerSize(); i++) {
                ItemStack s = station.getItem(i);
                if (!s.isEmpty()) {
                    station.setItem(i, ItemStack.EMPTY);
                    if (!player.getInventory().add(s)) {
                        player.drop(s, false);
                    }
                }
            }
        }
        // 拆掉那块透明砧（东西已经进了 NBT）
        if (stationPos != null && player.level() instanceof ServerLevel sl) {
            if (sl.getBlockState(stationPos).getBlock() == com.mofengbaizhi.tinkersnewlife.content.block.InvisibleStationRegistry.INVISIBLE_STATION.get()) {
                sl.setBlockAndUpdate(stationPos, Blocks.AIR.defaultBlockState());
            }
        }
    }
}