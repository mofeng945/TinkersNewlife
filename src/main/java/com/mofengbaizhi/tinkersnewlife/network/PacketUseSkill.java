package com.mofengbaizhi.tinkersnewlife.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketUseSkill {

    public PacketUseSkill() {}

    public PacketUseSkill(FriendlyByteBuf buf) {}

    public void toBytes(FriendlyByteBuf buf) {}

    public static void handle(PacketUseSkill packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                player.getPersistentData().putBoolean("dreadsteel_skill_request", true);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}