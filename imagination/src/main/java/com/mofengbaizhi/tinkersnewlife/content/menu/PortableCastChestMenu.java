package com.mofengbaizhi.tinkersnewlife.content.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import slimeknights.tconstruct.tables.block.entity.chest.CastChestBlockEntity;
import slimeknights.tconstruct.tables.menu.TinkerChestContainerMenu;

/**
 * §1318 便携设备菜单（PortableCastChestMenu）——幽灵实体方案。
 *
 * <p>与便携工匠站完全同构：服务端造游离实体（带 level ＋ 远处坐标），
 * 内容存在**玩家持久数据**；客户端由混入按 buf 里的数据重建实体 ⇒ 永不 null。
 *
 * <p>{@code stillValid} 恒真：{@code ServerPlayer#doTick()} 每 tick 都会校验它，
 * 而幽灵在 1024 格外 ⇒ 否则界面开一瞬间就被服务端关掉。
 */
public class PortableCastChestMenu extends slimeknights.tconstruct.tables.menu.TinkerChestContainerMenu {

    private final slimeknights.tconstruct.tables.block.entity.chest.CastChestBlockEntity ghost;

    public PortableCastChestMenu(int id, Inventory inv, slimeknights.tconstruct.tables.block.entity.chest.CastChestBlockEntity tile) {
        super(id, inv, tile);
        this.ghost = tile;
    }

    public PortableCastChestMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, (slimeknights.tconstruct.tables.block.entity.chest.CastChestBlockEntity) com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceItem.buildGhost(
                slimeknights.tconstruct.tables.block.entity.chest.CastChestBlockEntity.class, buf, "tconstruct:cast_chest"));
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
        net.minecraft.world.Container c = (net.minecraft.world.Container) ghost;
        for (int i = 0; i < c.getContainerSize(); i++) {
            c.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
        }
    }
}