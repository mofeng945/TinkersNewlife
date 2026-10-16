package com.mofengbaizhi.tinkersnewlife.network.starfall;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** TNL907 ★免疫窗挡下 → 通知客户端闪金 ✓ */
public class PacketKtuluShieldFlash {

    private static long TNL$LOG = 0L;

    public PacketKtuluShieldFlash() {
    }

    public PacketKtuluShieldFlash(FriendlyByteBuf buf) {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            com.mofengbaizhi.tinkersnewlife.client.hud.KtuluBarFlash.trigger();
            if (System.currentTimeMillis() - TNL$LOG > 1500L) {      // TNL908 ★限流探针 ✓
                TNL$LOG = System.currentTimeMillis();
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                        "TNL908 client received shield flash, strength={}",
                        com.mofengbaizhi.tinkersnewlife.client.hud.KtuluBarFlash.strength());
            }
        }));
        ctx.get().setPacketHandled(true);
    }
}
