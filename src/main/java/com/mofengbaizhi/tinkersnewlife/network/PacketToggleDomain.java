package com.mofengbaizhi.tinkersnewlife.network;

import com.mofengbaizhi.tinkersnewlife.content.curse.DomainHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端→服务端：坐杀搏徒领域展开/关闭按键
 */
public class PacketToggleDomain {

    public PacketToggleDomain() {}

    public PacketToggleDomain(FriendlyByteBuf buf) {}

    public void toBytes(FriendlyByteBuf buf) {}

    public static void handle(PacketToggleDomain packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            DomainHandler.toggleDomain(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
