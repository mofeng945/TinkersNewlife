package com.mofengbaizhi.tinkersnewlife.network.portal;

import com.mofengbaizhi.tinkersnewlife.client.screen.DimensionPassScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * <b>服务端 → 客户端</b>：打开「维度通行证」选择界面（§660）。
 *
 * <p>只在<b>伟大白色空间里</b>用通行证时发 —— 那里需要玩家自己选目标维度与坐标；
 * 在别处用通行证是直接开门的，不走这个包 ✓。
 *
 * <p>带两样东西：<b>可选维度列表</b>（服务端 {@code levelKeys()} 的字符串形式，排序过）与
 * <b>锚点</b>（玩家右键点到的那个方块）。锚点必须原样带回 {@link PacketCreatePortal}，
 * 服务端凭它决定门开在哪儿 ✓（客户端不能凭空指定门位 ⇒ 也就没法隔空开门）。
 */
public class PacketOpenDimensionPassScreen {

    private final List<String> dimensions;
    private final BlockPos anchor;

    public PacketOpenDimensionPassScreen(List<String> dimensions, BlockPos anchor) {
        this.dimensions = List.copyOf(dimensions);
        this.anchor = anchor.immutable();
    }

    public PacketOpenDimensionPassScreen(FriendlyByteBuf buf) {
        this.dimensions = buf.readList(FriendlyByteBuf::readUtf);
        this.anchor = buf.readBlockPos();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeCollection(dimensions, FriendlyByteBuf::writeUtf);
        buf.writeBlockPos(anchor);
    }

    public static void handle(PacketOpenDimensionPassScreen packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                Minecraft.getInstance().setScreen(new DimensionPassScreen(packet.dimensions, packet.anchor))));
        ctx.get().setPacketHandled(true);
    }
}
