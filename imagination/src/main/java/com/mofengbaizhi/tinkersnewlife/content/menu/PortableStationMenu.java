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

    /**
     * §1316 ★★ 关键修复 ★★
     *
     * <p>{@code ServerPlayer#doTick()} 每 tick 都会执行
     * {@code if (!containerMenu.stillValid(this)) closeContainer();}
     * 而匠魂/Mantle 的实现在算"玩家到方块实体的距离" ⇒ 幽灵在 (0,-1024,0)
     * ⇒ 距离 1024 格 ⇒ 永远 false ⇒ **服务端把刚开的界面关掉**（用户看到的"只开一瞬间"）。
     *
     * <p>这里的服务端菜单就是本类，所以直接恒真即可 ✓（★客户端不跑这个校验 ✓）
     */
    @Override
    public boolean stillValid(Player player) {
        return true;
    }
    @Override
    public void removed(Player player) {
        // §1315 抓"谁关的界面"：把调用栈打出来（★不再靠猜 ✓）
        try {
            StringBuilder sb = new StringBuilder();
            StackTraceElement[] st = new Throwable().getStackTrace();
            for (int i = 0; i < Math.min(9, st.length); i++) {
                sb.append("\n    at ").append(st[i]);
            }
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携站·关闭] 调用栈:{}{}",
                    player.level().isClientSide() ? "(客户端)" : "(服务端)", sb);
        } catch (Throwable ignored) {
        }
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