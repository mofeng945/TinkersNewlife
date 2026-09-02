package com.mofengbaizhi.tinkersnewlife.network;

import com.mofengbaizhi.tinkersnewlife.content.curse.WuWeiHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端→服务端：在无为转变 UI 中选择了某个形态（EntityType 注册名）。
 * 空字符串 = 清除选择。
 */
public class PacketWuWeiSelect {

    private final String formId;

    public PacketWuWeiSelect(String formId) {
        this.formId = formId;
    }

    public PacketWuWeiSelect(FriendlyByteBuf buf) {
        this.formId = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(formId == null ? "" : formId);
    }

    public static void handle(PacketWuWeiSelect packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!WuWeiHandler.hasTechnique(player)) return;
            String formId = packet.formId;
            // 服务端校验：只能选择已记录（击杀过）的形态
            if (!formId.isEmpty() && !WuWeiHandler.getRecordedForms(player).contains(formId)) {
                return;
            }
            WuWeiHandler.setSelected(player, formId);
            if (!formId.isEmpty()) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.tinkersnewlife.wu_wei.selected"), true);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
