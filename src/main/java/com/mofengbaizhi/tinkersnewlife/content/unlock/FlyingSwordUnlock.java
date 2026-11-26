package com.mofengbaizhi.tinkersnewlife.content.unlock;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class FlyingSwordUnlock {
    public enum Manual {
        FLYING_SWORD("tinkersnewlife_flying_sword_unlocked", "flying_sword"),
        TANG_HENG_DAO("tinkersnewlife_tang_heng_dao_unlocked", "tang_heng_dao"),
        DRAGON_STAFF("tinkersnewlife_dragon_taming_unlocked", "dragon_staff");

        final String key;
        public final String messageKey;
        Manual(String key, String messageKey) { this.key = key; this.messageKey = messageKey; }
        private String rewardKey() { return key + "_chest_reward"; }
    }

    private FlyingSwordUnlock() {}

    public static boolean isUnlocked(Player player) { return has(player, Manual.FLYING_SWORD); }
    public static boolean canTameDragons(Player player) { return has(player, Manual.DRAGON_STAFF); }

    public static boolean canAssemble(Player player, Item item) {
        if (item == ModItems.FLYING_SWORD.get()) return has(player, Manual.FLYING_SWORD);
        if (item == ModItems.TANG_HENG_DAO.get()) return has(player, Manual.TANG_HENG_DAO);
        if (item == ModItems.DRAGON_STAFF.get()) return has(player, Manual.DRAGON_STAFF);
        return true;
    }

    public static boolean canShowLayout(Player player, ResourceLocation id) {
        if (!TinkersNewlife.MOD_ID.equals(id.getNamespace())) return true;
        return switch (id.getPath()) {
            case "flying_sword" -> has(player, Manual.FLYING_SWORD);
            case "tang_heng_dao" -> has(player, Manual.TANG_HENG_DAO);
            case "dragon_staff" -> has(player, Manual.DRAGON_STAFF);
            default -> true;
        };
    }

    private static boolean has(Player player, Manual manual) {
        return player != null && player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(manual.key);
    }

    public static boolean unlock(ServerPlayer player) { return unlock(player, Manual.FLYING_SWORD); }

    public static boolean unlock(ServerPlayer player, Manual manual) {
        if (has(player, manual)) return false;
        data(player).putBoolean(manual.key, true);
        sync(player);
        return true;
    }

    public static boolean claimChestPage(ServerPlayer player, Manual manual) {
        if (player instanceof FakePlayer || player.isSpectator() || manual == Manual.DRAGON_STAFF) return false;
        CompoundTag data = data(player);
        if (data.getBoolean(manual.rewardKey())) return false;
        data.putBoolean(manual.rewardKey(), true);
        return true;
    }

    private static CompoundTag data(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return data.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static void sync(ServerPlayer player) {
        int bits = 0;
        for (Manual manual : Manual.values()) if (has(player, manual)) bits |= 1 << manual.ordinal();
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Sync(bits));
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
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        CompoundTag original = event.getOriginal().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        for (Manual manual : Manual.values()) {
            if (original.getBoolean(manual.key)) data(event.getEntity()).putBoolean(manual.key, true);
            if (original.getBoolean(manual.rewardKey())) data(event.getEntity()).putBoolean(manual.rewardKey(), true);
        }
    }

    public record Sync(int bits) {
        public Sync(FriendlyByteBuf buf) { this(buf.readUnsignedByte()); }
        public void encode(FriendlyByteBuf buf) { buf.writeByte(bits); }
        public void handle(Supplier<NetworkEvent.Context> context) {
            var ctx = context.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Client.apply(bits)));
            ctx.setPacketHandled(true);
        }
    }

    private static final class Client {
        private static void apply(int bits) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player == null) return;
            for (Manual manual : Manual.values()) data(player).putBoolean(manual.key, (bits & (1 << manual.ordinal())) != 0);
        }
    }
}
