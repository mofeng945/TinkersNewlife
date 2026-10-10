package com.mofengbaizhi.tinkersnewlife.network.curse;

import com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedSpiritTechnique;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端→服务端：咒灵操术 GUI 选定某个个体（mode 0=释放/收回，1=献祭蓄力）。
 *
 * <p>⭐ 带 <b>uid</b>（个体记录自身的 UUID 文本）而不再只带下标：列表是"打开 GUI 那一刻"的快照，
 * 远程服务器上点一下要过一个来回，这期间某个体战死/被献祭都会让下标的含义变掉 ⇒
 * 只发下标会出现"点的是甲、服务端操作的是乙"✗。服务端优先按 uid 匹配，匹配不上才退回下标。
 */
public class PacketSpiritSelect {

    private final int mode;
    private final int row;
    private final String uid;

    public PacketSpiritSelect(int mode, int row, String uid) {
        this.mode = mode;
        this.row = row;
        this.uid = uid == null ? "" : uid;
    }

    public PacketSpiritSelect(FriendlyByteBuf buf) {
        this.mode = buf.readVarInt();
        this.row = buf.readVarInt();
        this.uid = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(mode);
        buf.writeVarInt(row);
        buf.writeUtf(uid == null ? "" : uid);
    }

    public static void handle(PacketSpiritSelect packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            CursedSpiritTechnique.selectRow(player, packet.mode, packet.row, packet.uid);
        });
        ctx.get().setPacketHandled(true);
    }
}
