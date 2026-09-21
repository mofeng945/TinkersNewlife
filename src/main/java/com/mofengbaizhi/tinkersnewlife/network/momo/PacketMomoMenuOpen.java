package com.mofengbaizhi.tinkersnewlife.network.momo;

import com.mofengbaizhi.tinkersnewlife.client.screen.MomoMenuScreen;
import com.mofengbaizhi.tinkersnewlife.content.handler.MomoFavor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：打开墨默的**三选项菜单屏**（用户口径 §455 A ✓）。
 *
 * <p>⚠ 为什么不用"容器菜单 + MenuScreens"那套 ✗：实机里无槽位容器菜单的按钮**点不动**（用户实测 ✓ 连回退都点不动 ⇒
 * 点击根本没进 `mouseClicked` ✓）⇒ 改成**纯客户端 Screen** ✓ 只带 `momoId + favor` 两个数 ✓
 * 少掉 容器菜单 / MenuType / 容器按钮包 / 客户端菜单工厂 四层可疑环节 ✓。
 */
public class PacketMomoMenuOpen {

    private final int momoId;
    private final int favor;

    public PacketMomoMenuOpen(int momoId, int favor) {
        this.momoId = momoId;
        this.favor = favor;
    }

    public PacketMomoMenuOpen(FriendlyByteBuf buf) {
        this.momoId = buf.readInt();
        this.favor = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(momoId);
        buf.writeInt(favor);
    }

    public static void sendTo(ServerPlayer player, int momoId) {
        com.mofengbaizhi.tinkersnewlife.TinkersNewlife.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new PacketMomoMenuOpen(momoId, MomoFavor.get(player)));
    }

    public static void handle(PacketMomoMenuOpen packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> Minecraft.getInstance().setScreen(
                        new MomoMenuScreen(packet.momoId, packet.favor))));
        ctx.get().setPacketHandled(true);
    }
}
