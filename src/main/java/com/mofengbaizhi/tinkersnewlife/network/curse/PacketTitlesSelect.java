package com.mofengbaizhi.tinkersnewlife.network.curse;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** §1305 客户端→服务端：提交"要展示的头衔"（★最多 3 枚 ✓）。 */
public class PacketTitlesSelect {

    private final List<String> ids = new ArrayList<>();

    public PacketTitlesSelect(List<String> ids) {
        this.ids.addAll(ids);
    }

    public PacketTitlesSelect(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) {
            ids.add(buf.readUtf());
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(ids.size());
        for (String s : ids) {
            buf.writeUtf(s);
        }
    }

    public static void handle(PacketTitlesSelect packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                com.mofengbaizhi.tinkersnewlife.content.curse.CurseTitles.setSelection(player, packet.ids);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}