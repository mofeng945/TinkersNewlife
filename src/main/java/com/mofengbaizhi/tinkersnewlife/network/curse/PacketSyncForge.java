package com.mofengbaizhi.tinkersnewlife.network.curse;

import com.mofengbaizhi.tinkersnewlife.client.data.ClientForgeData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端→客户端：构筑术式拟造进度同步。
 * 携带拟造起始/结束 gameTime 与目标物品注册名（空 = 未在拟造）。
 * 客户端以本地 gameTime 计算剩余进度并渲染 HUD 进度条。
 */
public class PacketSyncForge {

    private final long start;
    private final long end;
    private final String itemId;

    public PacketSyncForge(long start, long end, String itemId) {
        this.start = start;
        this.end = end;
        this.itemId = itemId == null ? "" : itemId;
    }

    public PacketSyncForge(FriendlyByteBuf buf) {
        this.start = buf.readLong();
        this.end = buf.readLong();
        this.itemId = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeLong(start);
        buf.writeLong(end);
        buf.writeUtf(itemId);
    }

    public static void handle(PacketSyncForge packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientForgeData.update(packet.start, packet.end, packet.itemId));
        ctx.get().setPacketHandled(true);
    }
}
