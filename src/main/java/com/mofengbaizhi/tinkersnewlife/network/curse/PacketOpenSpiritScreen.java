package com.mofengbaizhi.tinkersnewlife.network.curse;

import com.mofengbaizhi.tinkersnewlife.client.screen.CursedSpiritScreen;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedSpiritTechnique.SpiritEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务端→客户端：打开咒灵操术 个体列表 GUI（mode 0=释放/收回，1=献祭蓄力）。
 * 每个条目携带完整 NBT 快照（用于客户端 3D 展示实体）。
 */
public class PacketOpenSpiritScreen {

    public static final class RowData {
        /** 个体记录自身的 UUID 文本：客户端点击时回传它（服务端按 uid 选行，不再依赖下标） */
        public final String uid;
        public final String name;
        public final String type;
        public final CompoundTag nbt;
        /** ⭐ 唯一的"在场上"依据 = 服务端记录里的 releasedId ≥ 0；客户端从不自行推断/先行置位 */
        public boolean released;

        RowData(SpiritEntry e) {
            this.uid = e.uid == null ? "" : e.uid.toString();
            this.name = e.name;
            this.type = e.type;
            this.nbt = e.nbt;
            this.released = e.releasedId >= 0;
        }

        RowData(FriendlyByteBuf buf) {
            this.uid = buf.readUtf();
            this.name = buf.readUtf();
            this.type = buf.readUtf();
            this.nbt = buf.readNbt();
            this.released = buf.readBoolean();
        }

        void write(FriendlyByteBuf buf) {
            buf.writeUtf(uid == null ? "" : uid);
            buf.writeUtf(name == null ? "" : name);
            buf.writeUtf(type);
            buf.writeNbt(nbt);
            buf.writeBoolean(released);
        }
    }

    private final int mode;
    private final List<RowData> rows;

    public PacketOpenSpiritScreen(int mode, List<SpiritEntry> entries) {
        this.mode = mode;
        this.rows = new ArrayList<>();
        for (SpiritEntry e : entries) {
            rows.add(new RowData(e));
        }
    }

    public PacketOpenSpiritScreen(FriendlyByteBuf buf) {
        this.mode = buf.readVarInt();
        int n = buf.readVarInt();
        this.rows = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            rows.add(new RowData(buf));
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(mode);
        buf.writeVarInt(rows.size());
        for (RowData r : rows) {
            r.write(buf);
        }
    }

    public static void handle(PacketOpenSpiritScreen packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> Minecraft.getInstance().setScreen(new CursedSpiritScreen(packet.mode, packet.rows))));
        ctx.get().setPacketHandled(true);
    }
}
