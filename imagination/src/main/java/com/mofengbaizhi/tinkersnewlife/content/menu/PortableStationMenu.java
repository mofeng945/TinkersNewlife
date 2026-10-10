package com.mofengbaizhi.tinkersnewlife.content.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity;
import slimeknights.tconstruct.tables.menu.TinkerStationContainerMenu;

/** §1278 便携工匠站菜单：关闭时把里面的东西全还给玩家（用户口径）。 */
public class PortableStationMenu extends TinkerStationContainerMenu {

    private final TinkerStationBlockEntity detached;

    public PortableStationMenu(int id, Inventory inv, TinkerStationBlockEntity tile) {
        super(id, inv, tile);
        this.detached = tile;
    }

    /** 客户端构造：库存在 buf 里（当前为空 ⇒ 客户端用游离实例 ✓ 内容靠标准菜单同步 ✓） */
    public PortableStationMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, com.mofengbaizhi.tinkersnewlife.content.item.PortableTinkerStationItem
                .createDetached(inv.player == null ? null : inv.player.level()));
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (detached == null || player.level().isClientSide()) {
            return;
        }
        // ★ 关闭即清空：能塞进背包就塞 ✗ 塞不下掉在脚下 ✓
        for (int i = 0; i < detached.getContainerSize(); i++) {
            ItemStack s = detached.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            detached.setItem(i, ItemStack.EMPTY);
            if (!player.getInventory().add(s)) {
                player.drop(s, false);
            }
        }
    }
}