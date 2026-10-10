package com.mofengbaizhi.tinkersnewlife.network.curse;

import com.mofengbaizhi.tinkersnewlife.content.curse.technique.PuppetTechnique;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端→服务端：傀儡选择界面中选定傀儡类型 → 服务端召唤并转移视角。
 * kind: 0=铁傀儡，1=雪傀儡。
 */
public class PacketPuppetSelect {

    private final int kind;

    public PacketPuppetSelect(int kind) {
        this.kind = kind;
    }

    public PacketPuppetSelect(FriendlyByteBuf buf) {
        this.kind = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(kind);
    }

    public static void handle(PacketPuppetSelect packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (packet.kind != 0 && packet.kind != 1) return;
            PuppetTechnique.summon(player, packet.kind);
        });
        ctx.get().setPacketHandled(true);
    }
}
