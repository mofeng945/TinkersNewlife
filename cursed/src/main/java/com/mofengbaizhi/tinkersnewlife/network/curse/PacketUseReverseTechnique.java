package com.mofengbaizhi.tinkersnewlife.network.curse;

import com.mofengbaizhi.tinkersnewlife.content.curse.TechniqueHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端→服务端：术式反转按键按下/松开（F 键）。
 * 按下 = 反转术式开始蓄力（如苍→赫蓄力）；
 * 松开 = 蓄力型反转术式发射。
 */
public class PacketUseReverseTechnique {

    private final boolean press;

    public PacketUseReverseTechnique(boolean press) {
        this.press = press;
    }

    public PacketUseReverseTechnique(FriendlyByteBuf buf) {
        this.press = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(press);
    }

    public static void handle(PacketUseReverseTechnique packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (packet.press) {
                TechniqueHandler.onReverseKeyPress(player);
            } else {
                TechniqueHandler.onReverseKeyRelease(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
