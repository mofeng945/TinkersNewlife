package com.mofengbaizhi.tinkersnewlife.network.curse;

import com.mofengbaizhi.tinkersnewlife.client.screen.CursedSpeechScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务端→客户端：打开咒言编辑界面。
 * 携带：玩家已学全部词条 id + 当前六段咒言（可为空串段表示未设置）。
 */
public class PacketOpenCursedSpeechScreen {

    private final List<String> learned;
    private final List<String> chant;

    public PacketOpenCursedSpeechScreen(List<String> learned, List<String> chant) {
        this.learned = learned == null ? new ArrayList<>() : learned;
        this.chant = chant == null ? new ArrayList<>() : chant;
    }

    public PacketOpenCursedSpeechScreen(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        this.learned = new ArrayList<>();
        for (int i = 0; i < n; i++) this.learned.add(buf.readUtf());
        int m = buf.readVarInt();
        this.chant = new ArrayList<>();
        for (int i = 0; i < m; i++) this.chant.add(buf.readUtf());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(learned.size());
        for (String s : learned) buf.writeUtf(s);
        buf.writeVarInt(chant.size());
        for (String s : chant) buf.writeUtf(s);
    }

    public static void handle(PacketOpenCursedSpeechScreen packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> Minecraft.getInstance().setScreen(
                        new CursedSpeechScreen(packet.learned, packet.chant))));
        ctx.get().setPacketHandled(true);
    }
}
