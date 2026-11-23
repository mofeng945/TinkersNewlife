package com.mofengbaizhi.tinkersnewlife.client;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT)
public final class MomoBossMusic {
    private static ResourceLocation activeDimension;
    private static Music music;

    private MomoBossMusic() {}

    public static void update(ResourceLocation dimension, boolean playing) {
        activeDimension = playing ? dimension : null;
        if (!playing && music != null) Minecraft.getInstance().getMusicManager().stopPlaying(music);
    }

    public static Music forCurrentFight(Minecraft client) {
        if (activeDimension == null || client.player == null || client.level == null
                || !client.player.isAlive() || !activeDimension.equals(client.level.dimension().location())) {
            if (music != null) client.getMusicManager().stopPlaying(music);
            return null;
        }
        if (music == null) music = new Music(Holder.direct(ModSounds.MOMO_BOSS_MUSIC.get()), 0, 0, true);
        return music;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        update(null, false);
    }
}
