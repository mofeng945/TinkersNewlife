package com.mofengbaizhi.tinkersnewlife.network.portal;

import com.mofengbaizhi.tinkersnewlife.client.screen.DimensionPassScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

/**
 * <b>服务端 → 客户端</b>：打开「维度通行证」选择界面（§660／§661）。
 *
 * <p>§661 起<b>两种用法都走这个包</b>，靠 {@code lockedDimensionId} 区分模式：
 * <ul>
 *   <li>{@code null} ⇒ <b>自由模式</b>（在伟大白色空间里用）：下拉选维度 ＋ 填 x、y、z。
 *       {@code dimensions} 是服务端算好的<b>已解锁</b>名单（去过 ＋ 不在黑名单）✓；</li>
 *   <li>非 null ⇒ <b>锁定模式</b>（在其它维度用）：维度锁死为这个 id、<b>不能填 y</b>
 *       （{@code lockedY} 就是那个固定值 ＝ 白色空间地面表层），只有 x／z 可改。
 *       此时 {@code dimensions} 是空列表。</li>
 * </ul>
 *
 * <p>带的三样东西：可选维度列表、<b>锚点</b>（玩家右键点到的那个方块，确认时原样带回，
 * 客户端不能凭空指定门位 ⇒ 也就没法隔空开门）、以及锁定模式的维度 id 与 y。
 */
public class PacketOpenDimensionPassScreen {

    private final List<String> dimensions;
    private final BlockPos anchor;
    /** 锁定模式的维度 id；自由模式为 null */
    @Nullable
    private final String lockedDimensionId;
    /** 锁定模式的固定 y（自由模式下无意义） */
    private final int lockedY;

    public PacketOpenDimensionPassScreen(List<String> dimensions, BlockPos anchor,
                                        @Nullable String lockedDimensionId, int lockedY) {
        this.dimensions = List.copyOf(dimensions);
        this.anchor = anchor.immutable();
        this.lockedDimensionId = lockedDimensionId;
        this.lockedY = lockedY;
    }

    public PacketOpenDimensionPassScreen(FriendlyByteBuf buf) {
        this.dimensions = buf.readList(FriendlyByteBuf::readUtf);
        this.anchor = buf.readBlockPos();
        this.lockedDimensionId = buf.readBoolean() ? buf.readUtf() : null;
        this.lockedY = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeCollection(dimensions, FriendlyByteBuf::writeUtf);
        buf.writeBlockPos(anchor);
        buf.writeBoolean(lockedDimensionId != null);
        if (lockedDimensionId != null) buf.writeUtf(lockedDimensionId);
        buf.writeVarInt(lockedY);
    }

    public static void handle(PacketOpenDimensionPassScreen packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                Minecraft.getInstance().setScreen(new DimensionPassScreen(
                        packet.dimensions, packet.anchor, packet.lockedDimensionId, packet.lockedY))));
        ctx.get().setPacketHandled(true);
    }
}
