package com.mofengbaizhi.tinkersnewlife.network.curse;

import com.mofengbaizhi.tinkersnewlife.client.screen.PlantSelectScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端→客户端：打开草木操术 顺转模式选择界面（树根 / 咒种），携带本次释放预估消耗用于展示。
 */
public class PacketOpenPlantScreen {

    private final int cost;

    public PacketOpenPlantScreen(int cost) {
        this.cost = cost;
    }

    public PacketOpenPlantScreen(FriendlyByteBuf buf) {
        this.cost = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(cost);
    }

    public static void handle(PacketOpenPlantScreen packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> Minecraft.getInstance().setScreen(new PlantSelectScreen(packet.cost))));
        ctx.get().setPacketHandled(true);
    }
}
