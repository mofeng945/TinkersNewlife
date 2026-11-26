package com.mofengbaizhi.tinkersnewlife.content.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity;
import slimeknights.tconstruct.tables.menu.TinkerStationContainerMenu;

/**
 * §1332 便携工匠砧菜单。
 *
 * <p>★匠魂的"砧"就是 {@code TinkerStationBlock} 的子类 ✗ ⭐ 用的也是同一个
 * {@code TinkerStationContainerMenu} ✓ —— 界面布局与槽位数**都由方块决定**：
 * <pre>
 * setToolSelection(StationSlotLayoutLoader.get(tile.getBlockState().getBlock()));   // 布局看方块
 * new TinkerStationBlockEntity(pos, state, block.getSlotCount());                    // 槽数看方块
 * </pre>
 * 所以只要幽灵实体是用 **砧方块** 造出来的 ⇒ 打开的就是**砧的界面** ✓
 *
 * <p>存档键与工匠站**分开**（{@code tnl_portable_tinkers_anvil_inv} ✓）⇒ 两者内容互不干扰 ✓
 */
public class PortableAnvilMenu extends TinkerStationContainerMenu {

    private final TinkerStationBlockEntity ghost;

    public PortableAnvilMenu(int id, Inventory inv, TinkerStationBlockEntity tile) {
        super(id, inv, tile);
        this.ghost = tile;
        com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携设备] 槽数（菜单）{} slots={}",
                this.getClass().getSimpleName(), this.slots.size());
    }

    public PortableAnvilMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, (TinkerStationBlockEntity) com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport
                .makeGhost("slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity",
                        "tconstruct:tinkers_anvil"));
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
        if (ghost == null || player.level().isClientSide()) {
            return;
        }
        com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.storeToPlayer(player, ghost,
                com.mofengbaizhi.tinkersnewlife.content.item.PortableTableItem
                        .saveKey(com.mofengbaizhi.tinkersnewlife.content.item.PortableTableItem.Kind.TINKERS_ANVIL));
        for (int i = 0; i < ghost.getContainerSize(); i++) {
            ghost.setItem(i, ItemStack.EMPTY);
        }
    }
}