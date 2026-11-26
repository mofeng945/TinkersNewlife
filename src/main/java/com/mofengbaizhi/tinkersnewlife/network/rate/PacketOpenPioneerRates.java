package com.mofengbaizhi.tinkersnewlife.network.rate;

import com.mofengbaizhi.tinkersnewlife.client.screen.IndustrialPioneerRatesScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务端 → 客户端：把「工业开拓之证」绑定维度的三类产率送过去并开屏（§742 建 · §746 扩成三类 ✓）
 *
 * <h2>载荷（§746 起）</h2>
 * 一个<b>通用行</b>列表 ✓，每行自带"是哪一类" ✓ —— 客户端据此分页签显示 ✓：
 * <pre>
 *   kind = 0 物品 ：id = 物品注册名 ；total = 个  ；perHour = 个/时
 *   kind = 1 流体 ：id = 流体注册名 ；total = mB  ；perHour = mB/时
 *   kind = 2 能量 ：id = ""         ；total = FE  ；perHour = FE/时（整维度只有一行 ✓）
 * </pre>
 *
 * <p>⚠ 只传 id ＋ 两个数 ✓：图标由客户端按注册名自己查（两端注册表一致 ✓）；
 * 维度名传的是<b>语言键</b>（{@code dimension.<ns>.<path>} ✓）⇒ 客户端按自己的语言显示 ✓。
 * <p>⚠ 行数有上限（服务端截断 ✓ 见 {@link #MAX_ROWS} ✓），且**截断了界面会写明** ✓ 不悄悄丢 ✗。
 */
public class PacketOpenPioneerRates {

    /** 行的类别 ✓ */
    public static final int KIND_ITEM = 0;
    public static final int KIND_FLUID = 1;
    public static final int KIND_ENERGY = 2;

    /** 单次最多送多少行（三类合计 ✓ §746：物品 400 ＋ 流体 100 ＋ 能量 1 ✓） */
    public static final int MAX_ROWS = 501;

    /**
     * 一行 = 类别 ＋ 注册名 ＋ <b>目前总量</b> ＋ <b>净产率</b> ✓
     * <p>⚠ 两个数都是"最近一次采样那一刻"的口径 ✓（见 {@code ContainerRateManager} ✓）
     */
    public record Row(int kind, String id, long total, double perHour) {
    }

    private final String dimensionKey;
    private final int totalKinds;
    private final List<Row> rows;

    public PacketOpenPioneerRates(String dimensionKey, int totalKinds, List<Row> rows) {
        this.dimensionKey = dimensionKey == null ? "" : dimensionKey;
        this.totalKinds = Math.max(0, totalKinds);
        this.rows = rows == null ? new ArrayList<>() : rows;
    }

    public PacketOpenPioneerRates(FriendlyByteBuf buf) {
        this.dimensionKey = buf.readUtf();
        this.totalKinds = buf.readVarInt();
        int n = buf.readVarInt();
        List<Row> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Row(buf.readVarInt(), buf.readUtf(), buf.readVarLong(), buf.readDouble()));
        }
        this.rows = list;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(dimensionKey);
        buf.writeVarInt(totalKinds);
        buf.writeVarInt(rows.size());
        for (Row row : rows) {
            buf.writeVarInt(row.kind());
            buf.writeUtf(row.id());
            buf.writeVarLong(row.total());
            buf.writeDouble(row.perHour());
        }
    }

    public static void handle(PacketOpenPioneerRates packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            // §744：如果玩家**已经开着**同一维度的界面 ⇒ **原地更新** ✓
            //   （不能再 new 一个屏 ✗ —— 那会把滚动位置、搜索框内容、鼠标焦点全闪掉 ✗）
            if (IndustrialPioneerRatesScreen.applyUpdate(packet.dimensionKey, packet.totalKinds, packet.rows)) {
                return;
            }
            Minecraft.getInstance().setScreen(
                    new IndustrialPioneerRatesScreen(packet.dimensionKey, packet.totalKinds, packet.rows));
        }));
        ctx.get().setPacketHandled(true);
    }
}
