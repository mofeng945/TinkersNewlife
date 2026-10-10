package com.mofengbaizhi.tinkersnewlife.network.curse;

import com.mofengbaizhi.tinkersnewlife.client.screen.ConstructSelectScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端→客户端：打开构筑术式「拟造物品栏」。
 * 物品候选列表由客户端本地从配方管理器枚举（有合成配方的物品），
 * 服务端在收到 {@link PacketConstructSelect} 后权威校验与扣费。
 */
public class PacketOpenConstructScreen {

    public PacketOpenConstructScreen() {}

    public PacketOpenConstructScreen(FriendlyByteBuf buf) {}

    public void toBytes(FriendlyByteBuf buf) {}

    public static void handle(PacketOpenConstructScreen packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> Minecraft.getInstance().setScreen(new ConstructSelectScreen())));
        ctx.get().setPacketHandled(true);
    }
}
