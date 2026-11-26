package com.mofengbaizhi.tinkersnewlife.network.curse;

import com.mofengbaizhi.tinkersnewlife.client.data.ClientWuWeiData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 服务端→客户端：某个<b>非玩家实体</b>被无为转变"原地换形态"（{@link com.mofengbaizhi.tinkersnewlife.content.curse.WuWeiHandler#transformControllableInPlace}）。
 * <p>
 * 为什么需要这个包：傀儡操术 / 黑鸟操术的操控链路是「相机绑实体 id + 每 tick 输入包」，
 * 所以被转变的傀儡/黑鸟<b>不能</b>删掉重建（那会直接失去全部操控）。做法是保留实体、
 * 把「形态」记在这条包里，客户端据此把该实体渲染成目标生物（同玩家伪装的代理渲染）。
 * <p>
 * 用实体 UUID（而非实体 id）作键：id 会被复用，UUID 不会。
 */
public class PacketMobDisguise {

    private final UUID entityId;
    private final String formId;

    public PacketMobDisguise(UUID entityId, String formId) {
        this.entityId = entityId;
        this.formId = formId == null ? "" : formId;
    }

    public PacketMobDisguise(FriendlyByteBuf buf) {
        this.entityId = buf.readUUID();
        this.formId = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(entityId);
        buf.writeUtf(formId);
    }

    public static void handle(PacketMobDisguise packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientWuWeiData.setMobDisguise(packet.entityId, packet.formId)));
        ctx.get().setPacketHandled(true);
    }
}
