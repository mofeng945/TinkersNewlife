package com.mofengbaizhi.tinkersnewlife.network.curse;

import com.mofengbaizhi.tinkersnewlife.content.curse.TechniqueHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端→服务端：切换当前术式按键。
 * 服务端把玩家选中的术式循环到核心上的下一个术式，并同步 HUD。
 */
public class PacketSwitchTechnique {

    public PacketSwitchTechnique() {}

    public PacketSwitchTechnique(FriendlyByteBuf buf) {}

    public void toBytes(FriendlyByteBuf buf) {}

    public static void handle(PacketSwitchTechnique packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            TechniqueHandler.onSwitch(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
