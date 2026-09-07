package com.mofengbaizhi.tinkersnewlife.content.menu;

import com.mofengbaizhi.tinkersnewlife.content.ModMenus;
import com.mofengbaizhi.tinkersnewlife.content.block.MoltenForgeBlockEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;
import slimeknights.tconstruct.library.tools.item.IModifiable;

import javax.annotation.Nullable;

/**
 * 融锻炉容器（菜单）：工具熔炼槽 + 玩家背包；GUI 参照匠魂熔炉样式。
 */
public class MoltenForgeMenu extends AbstractContainerMenu {

    private final MoltenForgeBlockEntity te;

    public MoltenForgeMenu(int containerId, Inventory playerInventory,
                           @Nullable MoltenForgeBlockEntity te) {
        super(ModMenus.MOLTEN_FORGE_MENU.get(), containerId);
        this.te = te;
        if (te == null) return;
        // 工具熔炼槽（匠魂熔炉中部熔炼区位置）
        addSlot(new SlotItemHandler(te.getToolHandler(), 0, 80, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof IModifiable;
            }
        });
        // 玩家物品栏 3x9
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // 快捷栏
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    public MoltenForgeBlockEntity getTile() {
        return te;
    }

    /** 熔炼槽内当前物品 */
    public ItemStack getToolInSlot() {
        return te != null ? te.getToolHandler().getStackInSlot(0) : ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return te != null && te.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index == 0) {
                if (!this.moveItemStackTo(stack, 1, 37, true)) return ItemStack.EMPTY;
            } else {
                if (stack.getItem() instanceof IModifiable) {
                    if (!this.moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
                } else if (index < 28) {
                    if (!this.moveItemStackTo(stack, 28, 37, false)) return ItemStack.EMPTY;
                } else if (index < 37) {
                    if (!this.moveItemStackTo(stack, 1, 28, false)) return ItemStack.EMPTY;
                }
            }
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
            if (stack.getCount() == result.getCount()) return ItemStack.EMPTY;
            slot.onTake(player, stack);
        }
        return result;
    }
}
