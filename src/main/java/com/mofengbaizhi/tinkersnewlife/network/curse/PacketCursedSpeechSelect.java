package com.mofengbaizhi.tinkersnewlife.network.curse;

import com.mofengbaizhi.tinkersnewlife.content.cursespeech.CursedSpeechState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端→服务端：咒言编辑中把某段（0..5）设置为某词条 id。
 * 服务端校验：词条存在、类别与该段匹配、玩家已学会；通过后写入当前咒言。
 */
public class PacketCursedSpeechSelect {

    private final int index;
    private final String wordId;

    public PacketCursedSpeechSelect(int index, String wordId) {
        this.index = index;
        this.wordId = wordId;
    }

    public PacketCursedSpeechSelect(FriendlyByteBuf buf) {
        this.index = buf.readVarInt();
        this.wordId = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(index);
        buf.writeUtf(wordId == null ? "" : wordId);
    }

    public static void handle(PacketCursedSpeechSelect packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            CursedSpeechState.setChantPart(player, packet.index, packet.wordId);
        });
        ctx.get().setPacketHandled(true);
    }
}
