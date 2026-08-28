package com.mofengbaizhi.tinkersnewlife.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import top.theillusivec4.curios.common.network.NetworkHandler;
import top.theillusivec4.curios.common.network.client.CPacketOpenCurios;

import java.util.function.Supplier;

/**
 * 服务端→客户端：告知客户端 curios 饰品栏槽位布局已变化，
 * 客户端若正在查看 curios 界面则自动重开菜单以刷新槽位布局。
 */
public class PacketRefreshCuriosMenu {

    public PacketRefreshCuriosMenu() {}

    public PacketRefreshCuriosMenu(FriendlyByteBuf buf) {}

    public void toBytes(FriendlyByteBuf buf) {}

    public static void handle(PacketRefreshCuriosMenu packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            // 服务端已关闭旧的 curios 菜单，这里发 curios 打开包让服务端重开，
            // 客户端界面随之用新槽位布局重建
            NetworkHandler.INSTANCE.sendToServer(new CPacketOpenCurios(ItemStack.EMPTY));
        });
        ctx.get().setPacketHandled(true);
    }
}
