package com.mofengbaizhi.tinkersnewlife.network.curse;

import com.mofengbaizhi.tinkersnewlife.client.screen.CursedSpiritScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务端→客户端 <b>回执</b>：咒灵操术"每个个体是否在场上"的权威状态（按个体 uid，不带 NBT）。
 *
 * <p>为什么要它：客户端那份个体列表只是"打开 GUI 那一刻"的快照 —— 释放/收回/幽灵清理/
 * 释放体战死都发生在<b>服务端</b>，玩家在远程服务器上还隔着一段延迟。没有回执时，
 * 客户端只能一直显示旧状态（典型："UI 说在场上、其实早就没了"✗）。
 * 现在服务端每做一次决定就回执一次，客户端<b>只按回执改</b>：
 * <ul>
 *   <li>uid 在回执里 → 用回执的 released 覆盖本地那一行；</li>
 *   <li>本地有、回执里没有（记录已被删，例如战死）→ 把那一行删掉；</li>
 *   <li>回执里有、本地没有（列表新增）→ <b>不动</b>：没有 NBT 就画不出 3D 展示，
 *       等玩家重开 GUI 时服务端会发完整列表 ✓。</li>
 * </ul>
 * 只在"当前正开着咒灵操术列表"时才有可见效果；没开界面时静默忽略（结果另有聊天提示 ✓）。
 */
public class PacketSpiritState {

    private final List<String> uids;
    private final List<Boolean> released;

    public PacketSpiritState(List<String> uids, List<Boolean> released) {
        this.uids = new ArrayList<>(uids);
        this.released = new ArrayList<>(released);
    }

    public PacketSpiritState(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        this.uids = new ArrayList<>(n);
        this.released = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            this.uids.add(buf.readUtf());
            this.released.add(buf.readBoolean());
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(uids.size());
        for (int i = 0; i < uids.size(); i++) {
            buf.writeUtf(uids.get(i) == null ? "" : uids.get(i));
            buf.writeBoolean(i < released.size() && released.get(i));
        }
    }

    public static void handle(PacketSpiritState packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> {
                    // 只在"正开着咒灵操术列表"时应用；其它界面/没开界面一律忽略
                    if (Minecraft.getInstance().screen instanceof CursedSpiritScreen screen) {
                        screen.applyState(packet.uids, packet.released);
                    }
                }));
        ctx.get().setPacketHandled(true);
    }
}
