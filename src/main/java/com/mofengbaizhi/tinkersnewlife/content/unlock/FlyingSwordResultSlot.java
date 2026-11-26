package com.mofengbaizhi.tinkersnewlife.content.unlock;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.recipe.tinkerstation.building.ToolBuildingRecipe;
import slimeknights.tconstruct.tables.block.entity.inventory.LazyResultContainer;
import slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity;
import slimeknights.tconstruct.tables.menu.slot.LazyResultSlot;

/** Gates the result per menu viewer without modifying the shared block entity's recipe cache. */
public final class FlyingSwordResultSlot extends LazyResultSlot {
    private final TinkerStationBlockEntity station;
    private final Player viewer;

    public FlyingSwordResultSlot(LazyResultContainer inventory, int x, int y,
                                 TinkerStationBlockEntity station, Player viewer) {
        super(inventory, x, y);
        this.station = station;
        this.viewer = viewer;
    }

    public static boolean isLockedAssembly(TinkerStationBlockEntity station, Player player) {
        return station.getLastRecipe() instanceof ToolBuildingRecipe building
            && !FlyingSwordUnlock.canAssemble(player, building.getOutput().asItem());
    }

    @Override
    public ItemStack getItem() {
        // The lazy lookup must run first: it updates lastRecipe after inputs change.
        ItemStack result = super.getItem();
        return !result.isEmpty() && isLockedAssembly(station, viewer)
            ? ItemStack.EMPTY : result;
    }

    @Override
    public boolean mayPickup(Player player) {
        ItemStack result = super.getItem();
        return !(!result.isEmpty() && isLockedAssembly(station, player))
            && super.mayPickup(player);
    }

    @Override
    public ItemStack remove(int amount) {
        // LazyResultSlot.remove delegates to the raw shared container even for an empty-looking slot.
        return getItem().isEmpty() ? ItemStack.EMPTY : super.remove(amount);
    }
}
