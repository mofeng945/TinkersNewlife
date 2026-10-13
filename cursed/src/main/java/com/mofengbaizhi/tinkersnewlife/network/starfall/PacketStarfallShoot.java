package com.mofengbaizhi.tinkersnewlife.network.starfall;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** TNL891 \u2605\u2605 \u84c4\u529b\u53d1\u5c04\u5305\uff08\u2605\u5ba2\u6237\u7aef\u677e\u624b \u21d2 \u670d\u52a1\u7aef\u53d1\u5c04 \u2713\uff09 */
public class PacketStarfallShoot {

    private final int charge;

    public PacketStarfallShoot(int charge) {
        this.charge = charge;
    }

    public PacketStarfallShoot(FriendlyByteBuf buf) {
        this.charge = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(charge);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp != null) {
                com.mofengbaizhi.tinkersnewlife.content.item.StarfallLamentItem.fireFromServer(sp, charge);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
