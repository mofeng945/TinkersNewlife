package com.mofengbaizhi.tinkersnewlife.content.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import slimeknights.tconstruct.tables.block.entity.table.PartBuilderBlockEntity;
import slimeknights.tconstruct.tables.menu.PartBuilderContainerMenu;

/**
 * §1318 便携设备菜单（PortablePartBuilderMenu）——幽灵实体方案。
 *
 * <p>与便携工匠站完全同构：服务端造游离实体（带 level ＋ 远处坐标），
 * 内容存在**玩家持久数据**；客户端由混入按 buf 里的数据重建实体 ⇒ 永不 null。
 *
 * <p>{@code stillValid} 恒真：{@code ServerPlayer#doTick()} 每 tick 都会校验它，
 * 而幽灵在 1024 格外 ⇒ 否则界面开一瞬间就被服务端关掉。
 */
public class PortablePartBuilderMenu extends slimeknights.tconstruct.tables.menu.PartBuilderContainerMenu {

    private final slimeknights.tconstruct.tables.block.entity.table.PartBuilderBlockEntity ghost;

    public PortablePartBuilderMenu(int id, Inventory inv, slimeknights.tconstruct.tables.block.entity.table.PartBuilderBlockEntity tile) {
        super(id, inv, tile);
        this.ghost = tile;
    }

    public PortablePartBuilderMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, (slimeknights.tconstruct.tables.block.entity.table.PartBuilderBlockEntity) com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceItem.buildGhost(
                slimeknights.tconstruct.tables.block.entity.table.PartBuilderBlockEntity.class, buf, "tconstruct:part_builder"));
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (ghost == null || player.level().isClientSide()) {
            return;
        }
        com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceItem.storeToPlayer(player, ghost);
        for (int i = 0; i < ghost.getContainerSize(); i++) {
            ghost.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
        }
    }
}