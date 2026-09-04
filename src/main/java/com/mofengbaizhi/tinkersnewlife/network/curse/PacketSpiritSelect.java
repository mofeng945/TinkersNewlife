package com.mofengbaizhi.tinkersnewlife.network.curse;

import com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedSpiritTechnique;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端→服务端：咒灵操术 GUI 选定某行（mode 0=释放/收回，1=献祭蓄力；row = 列表下标）。
 */
public class PacketSpiritSelect {

    private final int mode;
    private final int row;

    public PacketSpiritSelect(int mode, int row) {
        this.mode = mode;
        this.row = row;
    }

    public PacketSpiritSelect(FriendlyByteBuf buf) {
        this.mode = buf.readVarInt();
        this.row = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(mode);
        buf.writeVarInt(row);
    }

    public static void handle(PacketSpiritSelect packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            CursedSpiritTechnique.selectRow(player, packet.mode, packet.row);
        });
        ctx.get().setPacketHandled(true);
    }
}
