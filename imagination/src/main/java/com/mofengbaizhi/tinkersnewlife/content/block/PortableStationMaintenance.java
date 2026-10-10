package com.mofengbaizhi.tinkersnewlife.content.block;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * §1289 maintenance: if a portable station was left behind (crash, logout, kill), the
 * borrowed block slot is restored when the owner logs back in. The info lives in the
 * item NBT (tnl_station_pos / tnl_station_state) so it survives a restart.
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PortableStationMaintenance {

    private PortableStationMaintenance() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer sp)) {
                return;
            }
            for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
                ItemStack s = sp.getInventory().getItem(i);
                if (!s.isEmpty() && s.getItem() instanceof com.mofengbaizhi.tinkersnewlife.content.item.PortableTinkerStationItem) {
                    com.mofengbaizhi.tinkersnewlife.content.item.PortableTinkerStationItem.restoreStale(sp, s);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}