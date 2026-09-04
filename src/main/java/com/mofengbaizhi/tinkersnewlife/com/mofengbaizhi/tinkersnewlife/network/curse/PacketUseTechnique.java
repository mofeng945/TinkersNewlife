package com.mofengbaizhi.tinkersnewlife.network.curse;

import com.mofengbaizhi.tinkersnewlife.content.curse.TechniqueHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端→服务端：术式按键按下/松开。
 * 按下 = 即时术式直接释放 / 蓄力术式（灶·开）开始蓄力；
 * 松开 = 蓄力术式向当前朝向发射。
 */
public class PacketUseTechnique {

    private final boolean press;

    public PacketUseTechnique(boolean press) {
        this.press = press;
    }

    public PacketUseTechnique(FriendlyByteBuf buf) {
        this.press = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(press);
    }

    public static void handle(PacketUseTechnique packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (packet.press) {
                TechniqueHandler.onKeyPress(player);
            } else {
                TechniqueHandler.onKeyRelease(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
