package com.mofengbaizhi.tinkersnewlife.network.stuck;

import com.mofengbaizhi.tinkersnewlife.content.entity.StuckArrowManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** TNL927 ★客户端 → 服务端：★长按 G 满 2 秒 ⇒ ★拔箭 ✓ */
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
                StuckArrowManager.clear(player);          // ★拔掉 ✓
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
