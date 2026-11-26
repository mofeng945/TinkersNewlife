package com.mofengbaizhi.tinkersnewlife.network.curse;

import com.mofengbaizhi.tinkersnewlife.client.data.ClientForgeData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端→客户端：构筑术式拟造进度同步。
 * 携带剩余 tick（remaining）与总耗时 tick（total），以及目标物品注册名。
 * remaining<=0 且 total<=0 表示清除进度条。客户端以毫秒插值本地平滑推进，
 * 不依赖客户端 gameTime 与服务端的同步。
 */
public class PacketSyncForge {

    private final long remaining;
    private final long total;
    private final String itemId;

    public PacketSyncForge(long remaining, long total, String itemId) {
        this.remaining = remaining;
        this.total = total;
        this.itemId = itemId == null ? "" : itemId;
    }

    public PacketSyncForge(FriendlyByteBuf buf) {
        this.remaining = buf.readLong();
        this.total = buf.readLong();
        this.itemId = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeLong(remaining);
        buf.writeLong(total);
        buf.writeUtf(itemId);
    }

    public static void handle(PacketSyncForge packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientForgeData.update(packet.remaining, packet.total, packet.itemId));
        ctx.get().setPacketHandled(true);
    }
}
