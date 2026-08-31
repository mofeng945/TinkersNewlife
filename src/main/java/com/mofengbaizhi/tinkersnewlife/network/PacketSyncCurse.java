package com.mofengbaizhi.tinkersnewlife.network;

import com.mofengbaizhi.tinkersnewlife.client.ClientCurseData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端→客户端：咒力 HUD 数据同步
 * 携带：咒力/上限/领域状态/无限状态/当前选中的术式 id（空串 = 未佩戴或无数式）/已调伏式神位掩码/
 * 咒力亲和/咒力输出（召唤界面计算具体消耗用）。
 */
public class PacketSyncCurse {

    private final double curse;
    private final double max;
    private final boolean domainActive;
    private final boolean infinite;
    /** 当前选中的术式 id（如 tinkersnewlife:kai），无术式时为 "" */
    private final String techniqueId;
    /** 已调伏式神位掩码（位 = ShikigamiType.ordinal()） */
    private final int tamedMask;
    /** 咒力亲和（召唤消耗计算用） */
    private final int affinity;
    /** 咒力输出等级（召唤消耗计算用） */
    private final int output;

    public PacketSyncCurse(double curse, double max, boolean domainActive, boolean infinite,
                           String techniqueId, int tamedMask, int affinity, int output) {
        this.curse = curse;
        this.max = max;
        this.domainActive = domainActive;
        this.infinite = infinite;
        this.techniqueId = techniqueId == null ? "" : techniqueId;
        this.tamedMask = tamedMask;
        this.affinity = affinity;
        this.output = output;
    }

    public PacketSyncCurse(FriendlyByteBuf buf) {
        this.curse = buf.readDouble();
        this.max = buf.readDouble();
        this.domainActive = buf.readBoolean();
        this.infinite = buf.readBoolean();
        this.techniqueId = buf.readUtf();
        this.tamedMask = buf.readInt();
        this.affinity = buf.readInt();
        this.output = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeDouble(curse);
        buf.writeDouble(max);
        buf.writeBoolean(domainActive);
        buf.writeBoolean(infinite);
        buf.writeUtf(techniqueId);
        buf.writeInt(tamedMask);
        buf.writeInt(affinity);
        buf.writeInt(output);
    }

    public static void handle(PacketSyncCurse packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientCurseData.update(
                packet.curse, packet.max, packet.domainActive, packet.infinite, packet.techniqueId,
                packet.tamedMask, packet.affinity, packet.output));
        ctx.get().setPacketHandled(true);
    }
}
