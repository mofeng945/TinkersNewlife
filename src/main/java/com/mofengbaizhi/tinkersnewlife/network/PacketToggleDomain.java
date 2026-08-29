package com.mofengbaizhi.tinkersnewlife.network;

import com.mofengbaizhi.tinkersnewlife.content.curse.DomainRegistry;
import com.mofengbaizhi.tinkersnewlife.content.curse.ZuoShaBoTuDomain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端→服务端：坐杀搏徒领域展开/关闭按键
 */
public class PacketToggleDomain {

    public PacketToggleDomain() {}

    public PacketToggleDomain(FriendlyByteBuf buf) {}

    public void toBytes(FriendlyByteBuf buf) {}

    public static void handle(PacketToggleDomain packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            // 通过通用领域注册表切换坐杀搏徒领域
            DomainRegistry.toggle(player, ZuoShaBoTuDomain::tryCreate);
        });
        ctx.get().setPacketHandled(true);
    }
}
