package com.mofengbaizhi.tinkersnewlife.network.starfall;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** TNL891 ★★ 蓄力发射包（★客户端松手 ⇒ 服务端发射 ✓） */
public class PacketStarfallShoot {

    private final int charge;

    public PacketStarfallShoot(int charge) {
        this.charge = charge;
    }

    public PacketStarfallShoot(FriendlyByteBuf buf) {
        this.charge = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(charge);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp != null) {
                com.mofengbaizhi.tinkersnewlife.content.item.StarfallLamentItem.fireFromServer(sp, charge);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
