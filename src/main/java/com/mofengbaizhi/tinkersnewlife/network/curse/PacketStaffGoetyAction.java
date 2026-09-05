package com.mofengbaizhi.tinkersnewlife.network.curse;

import com.mofengbaizhi.tinkersnewlife.content.goety.ModularStaffGoety;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端→服务端：模块化魔杖·巫法 操作。
 * action: 0=打开聚晶包 1=循环装备聚晶 2=放入聚晶(arg=背包聚晶序号) 3=取出聚晶(arg=包内槽位)
 */
public class PacketStaffGoetyAction {

    private final int action;
    private final int arg;

    public PacketStaffGoetyAction(int action, int arg) {
        this.action = action;
        this.arg = arg;
    }

    public PacketStaffGoetyAction(FriendlyByteBuf buf) {
        this.action = buf.readVarInt();
        this.arg = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeVarInt(arg);
    }

    public static void handle(PacketStaffGoetyAction packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            switch (packet.action) {
                case 0 -> ModularStaffGoety.openPouch(player);
                case 1 -> ModularStaffGoety.cycleFocus(player);
                case 2 -> ModularStaffGoety.putFocus(player, packet.arg);
                case 3 -> ModularStaffGoety.takeFocus(player, packet.arg);
                default -> {
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
