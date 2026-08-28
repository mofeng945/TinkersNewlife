package com.mofengbaizhi.tinkersnewlife.network;

import com.mofengbaizhi.tinkersnewlife.client.ClientCurseData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端→客户端：咒力 HUD 数据同步
 */
public class PacketSyncCurse {

    private final double curse;
    private final double max;
    private final boolean domainActive;
    private final boolean infinite;

    public PacketSyncCurse(double curse, double max, boolean domainActive, boolean infinite) {
        this.curse = curse;
        this.max = max;
        this.domainActive = domainActive;
        this.infinite = infinite;
    }

    public PacketSyncCurse(FriendlyByteBuf buf) {
        this.curse = buf.readDouble();
        this.max = buf.readDouble();
        this.domainActive = buf.readBoolean();
        this.infinite = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeDouble(curse);
        buf.writeDouble(max);
        buf.writeBoolean(domainActive);
        buf.writeBoolean(infinite);
    }

    public static void handle(PacketSyncCurse packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientCurseData.update(packet.curse, packet.max, packet.domainActive, packet.infinite));
        ctx.get().setPacketHandled(true);
    }
}
