package com.mofengbaizhi.tinkersnewlife.network;

import com.mofengbaizhi.tinkersnewlife.content.curse.TechniqueHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端→服务端：释放术式按键（对看向的实体释放当前咒力核心术式槽上的术式）
 */
public class PacketUseTechnique {

    public PacketUseTechnique() {}

    public PacketUseTechnique(FriendlyByteBuf buf) {}

    public void toBytes(FriendlyByteBuf buf) {}

    public static void handle(PacketUseTechnique packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            TechniqueHandler.tryUse(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
