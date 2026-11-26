package com.mofengbaizhi.tinkersnewlife.content.unlock;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class FlyingSwordUnlock {
    private static final String KEY = "tinkersnewlife_flying_sword_unlocked";

    private FlyingSwordUnlock() {}

    public static boolean isUnlocked(Player player) {
        return player != null && player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(KEY);
    }

    public static boolean unlock(ServerPlayer player) {
        if (isUnlocked(player)) return false;
        set(player, true);
        sync(player);
        return true;
    }

    private static void set(Player player, boolean unlocked) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        data.getCompound(Player.PERSISTED_NBT_TAG).putBoolean(KEY, unlocked);
    }

    private static void sync(ServerPlayer player) {
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Sync(isUnlocked(player)));
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent public static void clonePlayer(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer && isUnlocked(event.getOriginal())) set(event.getEntity(), true);
    }

    public record Sync(boolean unlocked) {
        public Sync(FriendlyByteBuf buf) { this(buf.readBoolean()); }
        public void encode(FriendlyByteBuf buf) { buf.writeBoolean(unlocked); }
        public void handle(Supplier<NetworkEvent.Context> context) {
            var ctx = context.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Client.apply(unlocked)));
            ctx.setPacketHandled(true);
        }
    }

    private static final class Client {
        private static void apply(boolean unlocked) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null) set(player, unlocked);
        }
    }
}
