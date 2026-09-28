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
 * 服务端 → 客户端：把「工业开拓之证」绑定维度的<b>全部物品净产率</b>送过去并开屏（§742）✓
 *
 * <p>为什么不走 {@code Menu}：这份数据是"几千行只读列表" ✗，塞进容器菜单的槽位/数据槽根本放不下 ✗；
 * 而本模组本来就有一整套"服务端打包 ⇒ 客户端开屏"的现成口径 ✓
 * （见 {@code PacketOpenWuWeiScreen} 等 ✓）⇒ 照抄同一套 ✓。
 *
 * <p>⚠ 只传"物品 id + 个/时"✓：图标由客户端自己按注册名查（两端注册表一致 ✓），
 * 维度名传的是<b>语言键</b>（{@code dimension.<ns>.<path>} ✓）⇒ 客户端按自己的语言显示 ✓。
 * <p>⚠ 列表行数有上限（服务端截断 ✓ 见 {@code MAX_ROWS} ✓），避免大基地一次性发几十万行 ✗。
 */
public class PacketOpenPioneerRates {

    /** 单次最多送多少行 ✓（大基地几千种物品时只送前 N ✓ 屏幕底下会写明 ✓） */
    public static final int MAX_ROWS = 500;

    /**
     * 一行 = 物品注册名 ＋ <b>目前总量</b> ＋ <b>净产率（个/时）</b> ✓（§743 按用户口径加的总量列 ✓）
     * <p>⚠ 总量是"最近一次采样那一刻"的数 ✓（不是实时 ✗ 见 {@code ContainerRateManager#totalNow} ✓）
     */
    public record Row(String itemId, long total, double perHour) {
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
            list.add(new Row(buf.readUtf(), buf.readVarLong(), buf.readDouble()));
        }
        this.rows = list;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(dimensionKey);
        buf.writeVarInt(totalKinds);
        buf.writeVarInt(rows.size());
        for (Row row : rows) {
            buf.writeUtf(row.itemId());
            buf.writeVarLong(row.total());
            buf.writeDouble(row.perHour());
        }
    }

    public static void handle(PacketOpenPioneerRates packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            // §744：如果玩家**已经开着**同一维度的界面 ⇒ **原地更新** ✓
            //   （不能再 new 一个屏 ✗ —— 那会把滚动位置、鼠标焦点全闪掉 ✓ 而且看着像"闪屏"✗）
            if (IndustrialPioneerRatesScreen.applyUpdate(packet.dimensionKey, packet.totalKinds, packet.rows)) {
                return;
            }
            Minecraft.getInstance().setScreen(
                    new IndustrialPioneerRatesScreen(packet.dimensionKey, packet.totalKinds, packet.rows));
        }));
        ctx.get().setPacketHandled(true);
    }
}
