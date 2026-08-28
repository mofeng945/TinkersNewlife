package com.mofengbaizhi.tinkersnewlife.network;

import com.mofengbaizhi.tinkersnewlife.client.ClientEventHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端→客户端：告知客户端 curios 饰品栏槽位布局已变化，
 * 客户端延迟数 tick 后自动重开 curios 菜单（等服务端 curios 同步先落地），
 * 界面立即以新槽位布局刷新。
 */
public class PacketRefreshCuriosMenu {

    public PacketRefreshCuriosMenu() {}

    public PacketRefreshCuriosMenu(FriendlyByteBuf buf) {}

    public void toBytes(FriendlyByteBuf buf) {}

    public static void handle(PacketRefreshCuriosMenu packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(ClientEventHandler::requestCuriosReopen);
        ctx.get().setPacketHandled(true);
    }
}
