package com.mofengbaizhi.tinkersnewlife.content.storage;

import com.mofengbaizhi.tinkersnewlife.content.ModMenus;
import com.mofengbaizhi.tinkersnewlife.content.item.SilentGloveItem;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

import java.util.UUID;

public class SilentGloveContainer extends AbstractContainerMenu {

    private final IItemHandler inventory;
    private final UUID vaultUUID;
    /** 服务端持有手套物品栈（用于更新回收列表）；客户端为 null */
    private final ItemStack gloveStack;

    public SilentGloveContainer(int containerId, Inventory playerInventory, ItemStack gloveStack) {
        super(ModMenus.SILENT_GLOVE_CONTAINER.get(), containerId);
        this.vaultUUID = SilentGloveItem.getOrCreateVaultUUID(gloveStack);
        this.inventory = SilentGloveItem.getHandler(gloveStack);
        this.gloveStack = gloveStack;
        initSlots(playerInventory);
    }

    public SilentGloveContainer(int containerId, Inventory playerInventory, UUID vaultUUID, IItemHandler inventory) {
        super(ModMenus.SILENT_GLOVE_CONTAINER.get(), containerId);
        this.vaultUUID = vaultUUID;
        this.inventory = inventory;
        this.gloveStack = null;
        initSlots(playerInventory);
    }

    private void initSlots(Inventory playerInventory) {
        final IItemHandler inv = this.inventory;

        // 空间奇点库格子（12格，2行 × 6列）
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 6; col++) {
                int index = row * 6 + col;
                int x = 44 + col * 18;
                int y = 20 + row * 18;
                this.addSlot(new SlotItemHandler(inv, index, x, y) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return inv.isItemValid(index, stack);
                    }

                    @Override
                    public void set(ItemStack stack) {
                        ItemStack prev = getItem();
                        super.set(stack);
                        // 玩家手动放入新物品 → 加入回收列表（自动回收不经过 GUI，不会误加）
                        if (gloveStack != null && !stack.isEmpty() && !ItemStack.isSameItem(prev, stack)) {
                            SilentGloveItem.addToRecycleList(gloveStack, stack);
                        }
                    }

                    @Override
                    public void onTake(Player player, ItemStack stack) {
                        // 玩家手动取出物品 → 从回收列表移除
                        if (gloveStack != null) {
                            SilentGloveItem.removeFromRecycleList(gloveStack, stack);
                        }
                        super.onTake(player, stack);
                    }
                });
            }
        }

        // 玩家物品栏（27格）
        int playerInvY = 62;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = row * 9 + col + 9;
                int x = 8 + col * 18;
                int y = playerInvY + row * 18;
                this.addSlot(new Slot(playerInventory, index, x, y));
            }
        }

        // 玩家快捷栏（9格）
        for (int col = 0; col < 9; col++) {
            int x = 8 + col * 18;
            int y = playerInvY + 58;
            this.addSlot(new Slot(playerInventory, col, x, y));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        int vaultSlotCount = 12;

        if (slotIndex < vaultSlotCount) {
            // 从库移出（到玩家背包）→ 手动取出，移除回收列表
            boolean moved = this.moveItemStackTo(stack, vaultSlotCount, this.slots.size(), true);
            if (!moved) return ItemStack.EMPTY;
            if (gloveStack != null) {
                SilentGloveItem.removeFromRecycleList(gloveStack, copy);
            }
        } else {
            // 从玩家背包移入库 → 手动放入，加入回收列表
            boolean moved = this.moveItemStackTo(stack, 0, vaultSlotCount, false);
            if (!moved) return ItemStack.EMPTY;
            if (gloveStack != null) {
                SilentGloveItem.addToRecycleList(gloveStack, copy);
            }
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        // ⭐ 由 handler 自身标记脏，外部无需调用 save()
        this.broadcastChanges();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // ⭐ 由 handler 自身标记脏，外部无需调用 save()
        this.broadcastChanges();
    }

    public UUID getVaultUUID() {
        return vaultUUID;
    }

    public IItemHandler getInventory() {
        return inventory;
    }
}