package com.mofengbaizhi.tinkersnewlife.content.storage;

import com.mofengbaizhi.tinkersnewlife.content.ModMenus;
import com.mofengbaizhi.tinkersnewlife.content.modifier.QuantumBagModifier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public class BagContainer extends AbstractContainerMenu {

    private final IItemHandler bagInventory;
    private final UUID bagUUID;
    private final int bagLevel;
    private final int bagSlotCount;

    // ============================================================
    //  构造方法
    // ============================================================

    public BagContainer(int containerId, Inventory playerInventory, UUID uuid, int level) {
        super(ModMenus.BAG_CONTAINER.get(), containerId);
        this.bagUUID = uuid;
        this.bagLevel = level;
        this.bagSlotCount = StorageManager.getCapacityForLevel(level);
        this.bagInventory = StorageManager.getInstance().getOrCreate(uuid, level);
        initSlots(playerInventory);
    }

    public BagContainer(int containerId, Inventory playerInventory, UUID uuid, int level, CompoundTag data) {
        super(ModMenus.BAG_CONTAINER.get(), containerId);
        this.bagUUID = uuid;
        this.bagLevel = level;
        this.bagSlotCount = StorageManager.getCapacityForLevel(level);
        StorageManager.BigStackHandler handler = new StorageManager.BigStackHandler(bagSlotCount);
        handler.deserializeNBT(data);
        this.bagInventory = handler;
        initSlots(playerInventory);
    }

    private void initSlots(Inventory playerInventory) {
        int rows = bagSlotCount / 9;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                int index = row * 9 + col;
                if (index >= bagSlotCount) break;
                int x = 8 + col * 18;
                int y = 18 + row * 18;
                this.addSlot(new SlotItemHandler(bagInventory, index, x, y) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        if (QuantumBagModifier.getBagLevel(stack) > 0) return false;
                        return super.mayPlace(stack);
                    }

                    @Override
                    public int getMaxStackSize() {
                        return StorageManager.MAX_STACK_SIZE;
                    }

                    @Override
                    public int getMaxStackSize(ItemStack stack) {
                        return StorageManager.MAX_STACK_SIZE;
                    }
                });
            }
        }

        int playerInvY = 18 + rows * 18 + 14;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = row * 9 + col + 9;
                int x = 8 + col * 18;
                int y = playerInvY + row * 18;
                this.addSlot(new Slot(playerInventory, index, x, y));
            }
        }

        for (int col = 0; col < 9; col++) {
            int x = 8 + col * 18;
            int y = playerInvY + 58;
            this.addSlot(new Slot(playerInventory, col, x, y));
        }
    }

    // ============================================================
    //  🧹 整理功能
    // ============================================================

    public void sortInventory() {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < bagSlotCount; i++) {
            ItemStack stack = bagInventory.getStackInSlot(i);
            if (!stack.isEmpty()) items.add(stack.copy());
        }

        for (int i = 0; i < bagSlotCount; i++) {
            ((StorageManager.BigStackHandler) bagInventory).setStackInSlot(i, ItemStack.EMPTY);
        }

        Map<ItemKey, ItemStack> mergedMap = new LinkedHashMap<>();

        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;
            ItemKey key = new ItemKey(stack);
            ItemStack existing = mergedMap.get(key);
            if (existing == null) {
                ItemStack copy = stack.copy();
                if (copy.getCount() > StorageManager.MAX_STACK_SIZE) {
                    copy.setCount(StorageManager.MAX_STACK_SIZE);
                }
                mergedMap.put(key, copy);
            } else {
                int space = StorageManager.MAX_STACK_SIZE - existing.getCount();
                if (space > 0) {
                    int toAdd = Math.min(stack.getCount(), space);
                    existing.grow(toAdd);
                    stack.shrink(toAdd);
                }
                if (!stack.isEmpty()) {
                    mergedMap.put(new ItemKey(stack), stack.copy());
                }
            }
        }

        int slotIndex = 0;
        for (ItemStack stack : mergedMap.values()) {
            if (!stack.isEmpty() && slotIndex < bagSlotCount) {
                ((StorageManager.BigStackHandler) bagInventory).setStackInSlot(slotIndex, stack);
                slotIndex++;
            }
        }

        StorageManager.getInstance().markDirty(bagUUID);
        this.broadcastChanges();
    }

    private static class ItemKey {
        private final String id;
        private final String nbtString;

        public ItemKey(ItemStack stack) {
            this.id = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
            CompoundTag tag = stack.getTag();
            this.nbtString = tag != null ? tag.toString() : "";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ItemKey itemKey = (ItemKey) o;
            return Objects.equals(id, itemKey.id) && Objects.equals(nbtString, itemKey.nbtString);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, nbtString);
        }
    }

    // ============================================================
    //  ✅ 最稳定安全的 Shift 点击：只移动一组（原版最大堆叠）
    //  使用 moveItemStackTo 处理分散，但总数量限制为一组
    // ============================================================

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        int bagSlotCount = this.bagSlotCount;
        int groupSize = stack.getMaxStackSize(); // 物品原版最大堆叠（64/16等）
        int toMove = Math.min(stack.getCount(), groupSize);

        if (toMove <= 0) return ItemStack.EMPTY;

        // 从原槽位取出一组
        ItemStack extracted = slot.remove(toMove);
        if (extracted.isEmpty()) return ItemStack.EMPTY;

        // 确定目标区域
        int targetStart, targetEnd;
        boolean reverse;

        if (slotIndex < bagSlotCount) {
            // 从背包 → 玩家物品栏
            targetStart = bagSlotCount;
            targetEnd = this.slots.size();
            reverse = true;
        } else {
            // 从玩家物品栏 → 背包
            targetStart = 0;
            targetEnd = bagSlotCount;
            reverse = false;
        }

        // 尝试移动 extracted 到目标区域
        boolean moved = this.moveItemStackTo(extracted, targetStart, targetEnd, reverse);

        if (!moved) {
            // 完全无法移动，放回原槽位
            slot.set(extracted);
            return ItemStack.EMPTY;
        }

        // 如果还有剩余（部分放入），放回原槽位
        if (!extracted.isEmpty()) {
            ItemStack current = slot.getItem();
            if (current.isEmpty()) {
                slot.set(extracted);
            } else {
                // 合并回原槽位（同类物品）
                current.grow(extracted.getCount());
                slot.set(current);
            }
        }

        // 更新原槽位
        if (slot.getItem().isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        StorageManager.getInstance().markDirty(bagUUID);
        this.broadcastChanges();
        return copy;
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        StorageManager.getInstance().markDirty(bagUUID);
        this.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        StorageManager.getInstance().markDirty(bagUUID);
        StorageManager.getInstance().saveAllDirty();
    }

    public UUID getBagUUID() {
        return bagUUID;
    }

    public int getBagLevel() {
        return bagLevel;
    }

    public int getBagSlotCount() {
        return bagSlotCount;
    }

    public IItemHandler getBagInventory() {
        return bagInventory;
    }
}