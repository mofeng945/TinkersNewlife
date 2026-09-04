package com.mofengbaizhi.tinkersnewlife.network.curse;

import com.mofengbaizhi.tinkersnewlife.client.screen.PuppetSelectScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端→客户端：打开傀儡操术 选择界面（铁/雪傀儡），携带两种召唤消耗用于展示。
 */
public class PacketOpenPuppetScreen {

    private final int ironCost;
    private final int snowCost;

    public PacketOpenPuppetScreen(int ironCost, int snowCost) {
        this.ironCost = ironCost;
        this.snowCost = snowCost;
    }

    public PacketOpenPuppetScreen(FriendlyByteBuf buf) {
        this.ironCost = buf.readVarInt();
        this.snowCost = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(ironCost);
        buf.writeVarInt(snowCost);
    }

    public static void handle(PacketOpenPuppetScreen packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> Minecraft.getInstance().setScreen(
                        new PuppetSelectScreen(packet.ironCost, packet.snowCost))));
        ctx.get().setPacketHandled(true);
    }
}
