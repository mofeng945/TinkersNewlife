package com.mofengbaizhi.tinkersnewlife.network.stuck;

import com.mofengbaizhi.tinkersnewlife.content.entity.StuckArrowManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** TNL927 \u2605\u5ba2\u6237\u7aef \u2192 \u670d\u52a1\u7aef\uff1a\u2605\u957f\u6309 G \u6ee1 2 \u79d2 \u21d2 \u2605\u62d4\u7bad \u2713 */
public class PacketPullArrow {

    public PacketPullArrow() {
    }

    public PacketPullArrow(FriendlyByteBuf buf) {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && StuckArrowManager.has(player)) {
                StuckArrowManager.clear(player);          // \u2605\u62d4\u6389 \u2713
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
