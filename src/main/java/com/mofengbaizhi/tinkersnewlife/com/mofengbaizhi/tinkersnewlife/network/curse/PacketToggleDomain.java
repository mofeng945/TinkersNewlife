package com.mofengbaizhi.tinkersnewlife.network.curse;

import com.mofengbaizhi.tinkersnewlife.content.curse.domain.DomainRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端→服务端：通用领域展开/关闭按键
 * （自动检测佩戴咒力核心上的领域特性并展开对应领域）
 */
public class PacketToggleDomain {

    public PacketToggleDomain() {}

    public PacketToggleDomain(FriendlyByteBuf buf) {}

    public void toBytes(FriendlyByteBuf buf) {}

    public static void handle(PacketToggleDomain packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            DomainRegistry.toggleDomain(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
