package com.mofengbaizhi.tinkersnewlife.client.handler;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import slimeknights.tconstruct.tables.client.inventory.TinkerStationScreen;
import slimeknights.tconstruct.tables.menu.TinkerStationContainerMenu;

/**
 * §1279 Portable station screen.
 *
 * <p>Root cause of the earlier crash (crash-2026-10-10_23.06.32): JEI's
 * GuiEventHandler#onDrawScreenPost builds an ImmutableRect2i from every GUI exclusion
 * area; TiC's TinkerStationScreen computes its width dynamically from the block entity
 * layout, and a DETACHED entity (portable station, no world position) can make it
 * negative -> "width must be >= 0" -> client crash while rendering.
 *
 * <p>No mixin: we simply subclass TiC's screen and clamp the reported size, so JEI never
 * sees a negative rectangle. TiC's own layout code still runs unchanged.
 */
public class PortableStationScreen extends TinkerStationScreen {

    public PortableStationScreen(TinkerStationContainerMenu container, Inventory playerInventory,
                                 Component title) {
        super(container, playerInventory, title);
    }

    @Override
    public int getXSize() {
        return Math.max(1, super.getXSize());
    }

    @Override
    public int getYSize() {
        return Math.max(1, super.getYSize());
    }
}