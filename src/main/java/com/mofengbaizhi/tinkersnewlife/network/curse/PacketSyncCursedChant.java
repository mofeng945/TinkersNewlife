package com.mofengbaizhi.tinkersnewlife.network.curse;

import com.mofengbaizhi.tinkersnewlife.client.data.ClientCursedChant;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端→客户端：咒言咏唱读条进度同步。
 * 携带剩余 tick + 总 tick + 标题（拼好的咒言文案）；remaining<=0 且 total<=0 表示清除。
 */
public class PacketSyncCursedChant {

    private final long remaining;
    private final long total;
    private final String label;

    public PacketSyncCursedChant(long remaining, long total, String label) {
        this.remaining = remaining;
        this.total = total;
        this.label = label == null ? "" : label;
    }

    public PacketSyncCursedChant(FriendlyByteBuf buf) {
        this.remaining = buf.readLong();
        this.total = buf.readLong();
        this.label = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeLong(remaining);
        buf.writeLong(total);
        buf.writeUtf(label);
    }

    public static void handle(PacketSyncCursedChant packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientCursedChant.update(packet.remaining, packet.total, packet.label));
        ctx.get().setPacketHandled(true);
    }
}
