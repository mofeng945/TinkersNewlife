package com.mofengbaizhi.tinkersnewlife.network.momo;

import com.mofengbaizhi.tinkersnewlife.client.screen.MomoHireScreen;
import com.mofengbaizhi.tinkersnewlife.client.screen.MomoMenuScreen;
import com.mofengbaizhi.tinkersnewlife.content.handler.MomoFavor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：打开墨默的界面（`kind` 区分 ✓ 用户口径：**菜单与雇佣界面是两块** ✓）。
 *
 * <ul>
 *   <li>{@code kind = 0} ⇒ 三选项菜单（对话 / 交易 / 雇佣 + 回退）；</li>
 *   <li>{@code kind = 1} ⇒ **独立的雇佣界面**（天数选择 + 五种等价物的应付量）。</li>
 * </ul>
 *
 * <p>⚠ 整套不用"容器菜单 + MenuScreens"：无槽位容器菜单的按钮在实机里点不动（连回退都点不动，用户实测）
 * ⇒ 现在全是**纯客户端 Screen**，只靠这一个包把屏打开。
 */
public class PacketMomoMenuOpen {

    public static final int KIND_MENU = 0;
    public static final int KIND_HIRE = 1;

    private final int momoId;
    private final int favor;
    private final int kind;

    public PacketMomoMenuOpen(int momoId, int favor, int kind) {
        this.momoId = momoId;
        this.favor = favor;
        this.kind = kind;
    }

    public PacketMomoMenuOpen(FriendlyByteBuf buf) {
        this.momoId = buf.readInt();
        this.favor = buf.readInt();
        this.kind = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(momoId);
        buf.writeInt(favor);
        buf.writeInt(kind);
    }

    /** 打开三选项菜单 */
    public static void sendMenu(ServerPlayer player, int momoId) {
        send(player, momoId, KIND_MENU);
    }

    /** 打开独立雇佣界面 */
    public static void sendHire(ServerPlayer player, int momoId) {
        send(player, momoId, KIND_HIRE);
    }

    private static void send(ServerPlayer player, int momoId, int kind) {
        com.mofengbaizhi.tinkersnewlife.TinkersNewlife.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new PacketMomoMenuOpen(momoId, MomoFavor.favorAsSeenByMomo(player), kind));
    }

    public static void handle(PacketMomoMenuOpen packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(packet.kind == KIND_HIRE
                    ? new MomoHireScreen(packet.momoId, packet.favor)
                    : new MomoMenuScreen(packet.momoId, packet.favor));
        }));
        ctx.get().setPacketHandled(true);
    }
}
