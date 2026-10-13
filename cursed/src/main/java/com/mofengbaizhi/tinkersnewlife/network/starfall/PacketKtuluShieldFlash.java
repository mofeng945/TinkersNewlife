package com.mofengbaizhi.tinkersnewlife.network.starfall;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** TNL907 \u2605\u514d\u75ab\u7a97\u6321\u4e0b \u2192 \u901a\u77e5\u5ba2\u6237\u7aef\u95ea\u91d1 \u2713 */
public class PacketKtuluShieldFlash {

    public PacketKtuluShieldFlash() {
    }

    public PacketKtuluShieldFlash(FriendlyByteBuf buf) {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.mofengbaizhi.tinkersnewlife.client.hud.KtuluBarFlash.trigger()));
        ctx.get().setPacketHandled(true);
    }
}
