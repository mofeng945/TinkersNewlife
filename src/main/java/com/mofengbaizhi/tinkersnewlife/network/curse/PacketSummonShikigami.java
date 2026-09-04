package com.mofengbaizhi.tinkersnewlife.network.curse;

import com.mofengbaizhi.tinkersnewlife.content.curse.shikigami.ShikigamiHandler;
import com.mofengbaizhi.tinkersnewlife.content.curse.shikigami.ShikigamiType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端→服务端：选择界面中召唤某式神（携带类型序号）。
 * 服务端校验咒力并生成式神；未调伏式神进入敌意模式（调伏战）。
 */
public class PacketSummonShikigami {

    private final int typeIndex;

    public PacketSummonShikigami(int typeIndex) {
        this.typeIndex = typeIndex;
    }

    public PacketSummonShikigami(FriendlyByteBuf buf) {
        this.typeIndex = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(typeIndex);
    }

    public static void handle(PacketSummonShikigami packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            ShikigamiType[] types = ShikigamiType.values();
            if (packet.typeIndex >= 0 && packet.typeIndex < types.length) {
                ShikigamiHandler.summon(player, types[packet.typeIndex]);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
