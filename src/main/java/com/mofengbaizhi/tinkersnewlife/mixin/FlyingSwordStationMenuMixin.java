package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.unlock.FlyingSwordResultSlot;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import slimeknights.tconstruct.tables.block.entity.inventory.LazyResultContainer;
import slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity;
import slimeknights.tconstruct.tables.menu.TinkerStationContainerMenu;
import slimeknights.tconstruct.tables.menu.slot.LazyResultSlot;

/** The station, anvil and both portable menus use this same constructor. */
@Mixin(value = TinkerStationContainerMenu.class, remap = false)
public abstract class FlyingSwordStationMenuMixin {
    @Redirect(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lslimeknights/tconstruct/tables/block/entity/table/TinkerStationBlockEntity;)V",
        at = @At(value = "NEW", target = "(Lslimeknights/tconstruct/tables/block/entity/inventory/LazyResultContainer;II)Lslimeknights/tconstruct/tables/menu/slot/LazyResultSlot;"),
        require = 1)
    private LazyResultSlot tinkersnewlife$gateResultSlot(LazyResultContainer result, int x, int y,
                                                        int containerId, Inventory inventory,
                                                        TinkerStationBlockEntity station) {
        return new FlyingSwordResultSlot(result, x, y, station, inventory.player);
    }
}
