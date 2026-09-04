package com.mofengbaizhi.tinkersnewlife.network.curse;

import com.mofengbaizhi.tinkersnewlife.content.curse.technique.PlantManipulationTechnique;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端→服务端：草木操术 选择界面选定模式（0=树根 1=咒种）→ 服务端进入蓄力。
 */
public class PacketPlantSelect {

    private final int mode;

    public PacketPlantSelect(int mode) {
        this.mode = mode;
    }

    public PacketPlantSelect(FriendlyByteBuf buf) {
        this.mode = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(mode);
    }

    public static void handle(PacketPlantSelect packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            PlantManipulationTechnique.selectMode(player, packet.mode);
        });
        ctx.get().setPacketHandled(true);
    }
}
