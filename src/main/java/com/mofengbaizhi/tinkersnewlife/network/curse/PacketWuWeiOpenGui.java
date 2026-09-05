package com.mofengbaizhi.tinkersnewlife.network.curse;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenWuWeiScreen;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.curse.TechniqueHandler;
import com.mofengbaizhi.tinkersnewlife.content.curse.WuWeiHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * 客户端→服务端：P 键请求打开 无为转变 形态选择界面。
 * ⭐ 仅在当前选中的术式是「无为转变」时才下发击杀记录列表并打开 UI（S2C: PacketOpenWuWeiScreen）；
 * 否则静默返回（不提示任何消息）。
 */
public class PacketWuWeiOpenGui {

    public PacketWuWeiOpenGui() {}

    public PacketWuWeiOpenGui(FriendlyByteBuf buf) {}

    public void toBytes(FriendlyByteBuf buf) {}

    public static void handle(PacketWuWeiOpenGui packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            // 未切换到无为转变术式 → 静默返回
            if (!Modifiers.WU_WEI.getId().equals(TechniqueHandler.getSelectedTechniqueId(player))) {
                return;
            }
            TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new PacketOpenWuWeiScreen(WuWeiHandler.getRecordedForms(player), WuWeiHandler.getSelected(player)));
        });
        ctx.get().setPacketHandled(true);
    }
}
