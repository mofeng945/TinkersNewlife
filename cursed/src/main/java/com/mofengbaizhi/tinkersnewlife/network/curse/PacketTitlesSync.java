package com.mofengbaizhi.tinkersnewlife.network.curse;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** §1305 服务端→客户端：同步"已持有 ＋ 已选中"的头衔。 */
public class PacketTitlesSync {

    public final List<String> held;
    public final List<String> selected;

    public PacketTitlesSync(List<String> held, List<String> selected) {
        this.held = new ArrayList<>(held);
        this.selected = new ArrayList<>(selected);
    }

    public PacketTitlesSync(FriendlyByteBuf buf) {
        this.held = new ArrayList<>();
        this.selected = new ArrayList<>();
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) {
            held.add(buf.readUtf());
        }
        int m = buf.readVarInt();
        for (int i = 0; i < m; i++) {
            selected.add(buf.readUtf());
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(held.size());
        for (String s : held) {
            buf.writeUtf(s);
        }
        buf.writeVarInt(selected.size());
        for (String s : selected) {
            buf.writeUtf(s);
        }
    }

    public static void handle(PacketTitlesSync packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> com.mofengbaizhi.tinkersnewlife.client.data.ClientTitles.set(packet.held, packet.selected));
        ctx.get().setPacketHandled(true);
    }
}