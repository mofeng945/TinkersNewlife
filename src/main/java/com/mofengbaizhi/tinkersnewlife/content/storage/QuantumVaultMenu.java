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
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 152 + row * 18));   // 与界面 INV_TOP 一致 ✓
            }
        }
        // 快捷栏 9 格
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 210));                            // 与界面 HOTBAR_TOP 一致 ✓
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

        // ⚠ 客户端不做预测 ✗：单机里客户端与集成服务端**共用同一份静态 QuantumVaultManager** ✓，
        //   而原版 quickMoveStack 两端都会调用 → "客户端插一次 + 服务端插一次" = 凭空多一份 ✗✗
        //   （用户实测：Shift 存入会在存储里复制一份，且两份都能取出 ✓）。这里客户端直接返回，
        //   由服务端执行后用快照同步回去 ✓。
        if (player.level().isClientSide) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        // ⚠ 不允许把**量子背包本体**存进去 ✗（否则背包被吞、又取不出来 → 锁死 ✓）
        if (com.mofengbaizhi.tinkersnewlife.content.modifier.QuantumBagModifier.getBagLevel(stack) > 0) {
            return ItemStack.EMPTY;
        }

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
