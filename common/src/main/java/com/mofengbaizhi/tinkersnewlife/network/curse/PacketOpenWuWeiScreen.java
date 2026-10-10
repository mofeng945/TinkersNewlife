package com.mofengbaizhi.tinkersnewlife.network.curse;

import com.mofengbaizhi.tinkersnewlife.client.screen.WuWeiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务端→客户端：打开无为转变 形态选择界面。
 * 携带玩家已记录（击杀过）的生物 EntityType id 列表 + 当前已选中的形态 id（空 = 未选择）。
 */
public class PacketOpenWuWeiScreen {

    private final List<String> forms;
    private final String selected;

    public PacketOpenWuWeiScreen(List<String> forms, String selected) {
        this.forms = forms == null ? new ArrayList<>() : forms;
        this.selected = selected == null ? "" : selected;
    }

    public PacketOpenWuWeiScreen(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        this.forms = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            this.forms.add(buf.readUtf());
        }
        this.selected = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(forms.size());
        for (String s : forms) {
            buf.writeUtf(s);
        }
        buf.writeUtf(selected);
    }

    public static void handle(PacketOpenWuWeiScreen packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> Minecraft.getInstance().setScreen(new WuWeiScreen(packet.forms, packet.selected))));
        ctx.get().setPacketHandled(true);
    }
}
