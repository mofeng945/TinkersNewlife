package com.mofengbaizhi.tinkersnewlife.network;

import com.mofengbaizhi.tinkersnewlife.client.ClientWuWeiData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 服务端→客户端（广播）：某玩家进入/解除 无为转变 伪装。
 * 携带被伪装玩家 UUID 与形态 EntityType 注册名（空 = 解除伪装）。
 * 客户端据此把该玩家渲染成目标生物外观（无替身实体）。
 */
public class PacketWuWeiDisguise {

    private final UUID playerId;
    private final String formId;

    public PacketWuWeiDisguise(UUID playerId, String formId) {
        this.playerId = playerId;
        this.formId = formId == null ? "" : formId;
    }

    public PacketWuWeiDisguise(FriendlyByteBuf buf) {
        this.playerId = buf.readUUID();
        this.formId = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(playerId);
        buf.writeUtf(formId);
    }

    public static void handle(PacketWuWeiDisguise packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientWuWeiData.setDisguise(packet.playerId, packet.formId)));
        ctx.get().setPacketHandled(true);
    }
}
