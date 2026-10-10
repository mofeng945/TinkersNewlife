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
        // §1281 管理员诊断："客户端还没加载实体方块你就把 gui 加载了 ⇒ 然后 jei 就崩了"
        // 服务端 setBlockAndUpdate 后同 tick 就 openScreen ⇒ 客户端此刻还没有那个方块 ✗
        // ⇒ getBlockEntity(pos) ＝ null ⇒ 匠魂屏幕拿空 tile 算尺寸 ⇒ 负宽度 ⇒ JEI 崩 ✗
        // ⇒ 这里绝不给 null：拿不到真实体就自造一个"带客户端 level"的游离实体 ✓
        net.minecraft.world.level.Level level = null;
        net.minecraft.client.Minecraft mc = null;
        try {
            mc = net.minecraft.client.Minecraft.getInstance();
            level = mc.level;
        } catch (Throwable ignored) {
        }
        if (level != null) {
            try {
                if (level.getBlockEntity(pos) instanceof TinkerStationBlockEntity be) {
                    return be;
                }
                BlockPos fallback = mc.player != null ? mc.player.blockPosition() : pos;
                TinkerStationBlockEntity made = new TinkerStationBlockEntity(fallback,
                        com.mofengbaizhi.tinkersnewlife.content.block.InvisibleStationRegistry
                                .INVISIBLE_STATION.get().defaultBlockState(),
                        com.mofengbaizhi.tinkersnewlife.content.block.InvisibleStationBlockEntity.SLOTS);
                made.setLevel(level);
                return made;
            } catch (Throwable ignored) {
            }
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
