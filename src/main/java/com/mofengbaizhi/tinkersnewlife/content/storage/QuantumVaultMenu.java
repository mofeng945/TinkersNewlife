package com.mofengbaizhi.tinkersnewlife.content.storage;

import com.mofengbaizhi.tinkersnewlife.content.ModMenus;
import com.mofengbaizhi.tinkersnewlife.network.VaultNetwork;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * 量子背包 6 级界面（按数量存放 ✓）。
 *
 * <p>本容器**没有自己的槽位** ✗ —— 存储是"物品类型 + 数量"的列表 ✓，
 * 由 {@code QuantumVaultScreen} 自绘、客户端操作经 {@code PacketVaultAction} 回服务端执行 ✓。
 * 这里只放玩家背包的 36 个槽位，并把"Shift 点击背包物品"接成<b>存入 6 级存储</b> ✓。
 */
public class QuantumVaultMenu extends AbstractContainerMenu {

    private final UUID uuid;

    public QuantumVaultMenu(int containerId, Inventory playerInventory, UUID uuid) {
        super(ModMenus.QUANTUM_VAULT.get(), containerId);
        this.uuid = uuid;

        // 玩家背包 27 格（3×9）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        // 快捷栏 9 格
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 198));
        }
    }

    public UUID getBagUUID() {
        return uuid;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /** Shift 点击玩家背包物品 → 存进 6 级存储 ✓（存完回传快照刷新界面 ✓） */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        QuantumVault vault = QuantumVaultManager.getInstance().getOrCreate(uuid);
        int inserted = vault.insert(stack);
        if (inserted <= 0) return ItemStack.EMPTY;

        stack.shrink(inserted);
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        QuantumVaultManager.getInstance().markDirty(uuid);

        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            VaultNetwork.sync(serverPlayer, uuid, this.containerId);
        }
        return ItemStack.EMPTY;
    }
}
