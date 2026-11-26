package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoBossMusic;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class MomoBossMusicHandler {
    private static final Map<ServerPlayer, ResourceLocation> PLAYING = new HashMap<>();

    private MomoBossMusicHandler() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (PLAYING.isEmpty() && KtuluMomo.TNL_ACTIVE.isEmpty()) return;

        Map<ServerPlayer, ResourceLocation> audience = new HashMap<>();
        for (KtuluMomo boss : KtuluMomo.TNL_ACTIVE.values()) {
            if (boss.isRemoved() || boss.tnl$isDead() || !boss.tnl$isBossForm()) continue;
            for (ServerPlayer player : boss.tnl$bossAudience()) {
                if (player.level() == boss.level() && player.isAlive() && player.distanceTo(boss) <= 64.0F) {
                    audience.put(player, player.level().dimension().location());
                }
            }
        }
        // Read final membership after the boss's periodic remove/add refresh.
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            ResourceLocation dimension = audience.get(player);
            if (!java.util.Objects.equals(dimension, PLAYING.get(player))) {
                TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new PacketMomoBossMusic(player.level().dimension().location(), dimension != null));
            }
        }
        PLAYING.clear();
        PLAYING.putAll(audience);
        PLAYING.keySet().retainAll(event.getServer().getPlayerList().getPlayers());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PLAYING.clear();
    }
}
