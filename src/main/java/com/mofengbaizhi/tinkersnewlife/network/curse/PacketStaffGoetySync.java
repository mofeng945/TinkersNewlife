package com.mofengbaizhi.tinkersnewlife.network.curse;

import com.mofengbaizhi.tinkersnewlife.client.screen.StaffGoetyScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务端→客户端：魔杖·巫法 状态同步；open=true 时客户端打开聚晶包界面。
 */
public class PacketStaffGoetySync {

    private final boolean open;
    private final int mode;
    private final int idx;
    private final List<ItemStack> foci;
    private final List<ItemStack> invFoci;

    public PacketStaffGoetySync(boolean open, int mode, int idx,
                                List<ItemStack> foci, List<ItemStack> invFoci) {
        this.open = open;
        this.mode = mode;
        this.idx = idx;
        this.foci = new ArrayList<>(foci);
        this.invFoci = new ArrayList<>(invFoci);
    }

    public PacketStaffGoetySync(FriendlyByteBuf buf) {
        this.open = buf.readBoolean();
        this.mode = buf.readVarInt();
        this.idx = buf.readVarInt();
        int n = buf.readVarInt();
        this.foci = new ArrayList<>();
        for (int i = 0; i < n; i++) this.foci.add(buf.readItem());
        int m = buf.readVarInt();
        this.invFoci = new ArrayList<>();
        for (int i = 0; i < m; i++) this.invFoci.add(buf.readItem());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(open);
        buf.writeVarInt(mode);
        buf.writeVarInt(idx);
        buf.writeVarInt(foci.size());
        for (ItemStack s : foci) buf.writeItem(s);
        buf.writeVarInt(invFoci.size());
        for (ItemStack s : invFoci) buf.writeItem(s);
    }

    public static void handle(PacketStaffGoetySync packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> {
                    net.minecraft.client.gui.screens.Screen current = Minecraft.getInstance().screen;
                    // open=true（按 J）→ 打开；否则若聚晶包界面正开着 → 用新数据原地刷新（保持常驻）
                    if (packet.open || current instanceof StaffGoetyScreen) {
                        Minecraft.getInstance().setScreen(new StaffGoetyScreen(packet.mode, packet.idx,
                                packet.foci, packet.invFoci));
                    }
                }));
        ctx.get().setPacketHandled(true);
    }
}
