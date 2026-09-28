package com.mofengbaizhi.tinkersnewlife.content.rate;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * 「容器产率」的<b>每维度存档</b>（§735 建 · §737 起<b>物品 / 流体 / 能量三种都记</b> ✓）
 *
 * <h2>存什么（口径：只算<b>净</b>变化 ✓）</h2>
 * 每类物品/流体一条<b>定长环形缓冲</b>，能量一条整维度环形缓冲 ✓；
 * 每格 = <b>一个 10 分钟区间的"净增量"</b> ✓
 * （= 该区间内"两次采样都观测到的全部来源"的总量之差 ✓ ⇒ 维度内搬运自动抵消 ✓，
 * 消耗/玩家取走算负值 ✓ —— 这就是"净产率"的定义 ✓）。
 *
 * <ul>
 *   <li>{@code RING = 6} ⇒ 环满 = <b>最近 1 小时</b> ✓；速率 = `最近 n 格之和 ÷ (n × 10 分钟) × 60` ✓；</li>
 *   <li>单位：物品 <b>个/时</b> ✓、流体 <b>mB/时</b> ✓、能量 <b>FE/时</b> ✓；</li>
 *   <li>体积很小 ✓（每物品/流体 6 个 long ✓ 能量只有 6 个 long ✓）。</li>
 * </ul>
 *
 * <h2>⚠ 为什么不落盘"上一次的来源快照"</h2>
 * 那份快照是"每来源 × 每物品/流体"的计数 ✗（可能几万个数字 ✗）⇒ 不落盘 ✓
 * 代价：<b>重启后的第一个 10 分钟区间没有基线 ⇒ 该区间记 0</b> ✗（如实标注 ✓ 不谎报 ✓）。
 */
public class ContainerRateData extends SavedData {

    /** 环形缓冲长度：6 个区间 × 10 分钟 = 1 小时 ✓ */
    public static final int RING = 6;

    private static final String DATA_NAME = TinkersNewlife.MOD_ID + "_container_rate";

    /** 物品 → 环形缓冲 ✓ */
    private final Map<Item, long[]> itemRing = new HashMap<>();
    /** 流体 → 环形缓冲（mB ✓） */
    private final Map<Fluid, long[]> fluidRing = new HashMap<>();
    /** 能量：整维度一条（FE ✓） */
    private final long[] energyRing = new long[RING];

    /** 最后写入的槽位（-1 = 还没写过一个完整区间 ✓） */
    private int cursor = -1;
    /** 已写入的区间数（≤ RING ✓） */
    private int filled = 0;

    // ============================================================
    //  取用
    // ============================================================

    public static ContainerRateData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ContainerRateData::load, ContainerRateData::new, DATA_NAME);
    }

    // ============================================================
    //  写入：一个区间的净增量（三类一起 ✓）
    // ============================================================

    /**
     * 推入一个 10 分钟区间的净增量 ✓（正 = 净增 ✓ 负 = 净减 ✓）。
     * <p>⚠ 表里没有的物品/流体这一格要<b>清零</b> ✗（否则会把 6 个区间前的旧值当成新值 ✓）。
     */
    public void pushInterval(Object2LongMap<Item> items, Object2LongMap<Fluid> fluids, long energyNet) {
        cursor = (cursor + 1) % RING;
        filled = Math.min(RING, filled + 1);
        for (long[] slot : itemRing.values()) {
            slot[cursor] = 0L;
        }
        for (long[] slot : fluidRing.values()) {
            slot[cursor] = 0L;
        }
        energyRing[cursor] = energyNet;
        for (Object2LongMap.Entry<Item> entry : items.object2LongEntrySet()) {
            itemRing.computeIfAbsent(entry.getKey(), key -> new long[RING])[cursor] = entry.getLongValue();
        }
        for (Object2LongMap.Entry<Fluid> entry : fluids.object2LongEntrySet()) {
            fluidRing.computeIfAbsent(entry.getKey(), key -> new long[RING])[cursor] = entry.getLongValue();
        }
        setDirty();
    }

    /** 清空全部统计 ✓（对应"重开统计"✓ 本轮没有命令 ✓） */
    public void resetAll() {
        itemRing.clear();
        fluidRing.clear();
        java.util.Arrays.fill(energyRing, 0L);
        cursor = -1;
        filled = 0;
        setDirty();
    }

    /** 已攒够的区间数（0 ~ {@link #RING} ✓） */
    public int intervals() {
        return filled;
    }

    // ============================================================
    //  物品（个/时 ✓）
    // ============================================================

    public long lastIntervalNet(Item item) {
        return last(itemRing.get(item));
    }

    public double netPerHour(Item item, int intervals) {
        return rate(itemRing.get(item), intervals);
    }

    public Map<Item, Double> netPerHourAll(int intervals) {
        Map<Item, Double> out = new HashMap<>();
        for (Item item : itemRing.keySet()) {
            double v = netPerHour(item, intervals);
            if (v != 0.0D) out.put(item, v);
        }
        return out;
    }

    // ============================================================
    //  流体（mB/时 ✓）
    // ============================================================

    public long lastIntervalNet(Fluid fluid) {
        return last(fluidRing.get(fluid));
    }

    public double netPerHour(Fluid fluid, int intervals) {
        return rate(fluidRing.get(fluid), intervals);
    }

    /** 流体版的全量快照 ✓（名字带 fluid 前缀，避免与物品那个同签名 ✗） */
    public Map<Fluid, Double> fluidNetPerHourAll(int intervals) {
        Map<Fluid, Double> out = new HashMap<>();
        for (Fluid fluid : fluidRing.keySet()) {
            double v = netPerHour(fluid, intervals);
            if (v != 0.0D) out.put(fluid, v);
        }
        return out;
    }

    // ============================================================
    //  能量（FE/时 ✓）
    // ============================================================

    public long lastIntervalEnergyNet() {
        return (filled <= 0 || cursor < 0) ? 0L : energyRing[cursor];
    }

    public double energyNetPerHour(int intervals) {
        int n = Math.min(Math.max(0, intervals), filled);
        if (n <= 0) return 0.0D;
        long sum = 0L;
        for (int i = 0; i < n; i++) {
            sum += energyRing[Math.floorMod(cursor - i, RING)];
        }
        return sum / minutes(n) * 60.0D;
    }

    // ============================================================
    //  内部小工具
    // ============================================================

    private long last(long[] ring) {
        return (ring == null || filled <= 0 || cursor < 0) ? 0L : ring[cursor];
    }

    private double rate(long[] ring, int intervals) {
        int n = Math.min(Math.max(0, intervals), filled);
        if (ring == null || n <= 0) return 0.0D;
        long sum = 0L;
        for (int i = 0; i < n; i++) {
            sum += ring[Math.floorMod(cursor - i, RING)];
        }
        return sum / minutes(n) * 60.0D;
    }

    /** n 个区间 = 多少分钟 ✓（1200 tick = 1 分钟 ✓） */
    private static double minutes(int intervals) {
        return intervals * (ContainerRateManager.INTERVAL_TICKS / 1200.0D);
    }

    // ============================================================
    //  存 / 读
    // ============================================================

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("cursor", cursor);
        tag.putInt("filled", filled);
        tag.putLongArray("energy", energyRing);
        tag.put("items", saveMap(itemRing, ForgeRegistries.ITEMS::getKey));
        tag.put("fluids", saveMap(fluidRing, ForgeRegistries.FLUIDS::getKey));
        return tag;
    }

    private static <T> ListTag saveMap(Map<T, long[]> source, java.util.function.Function<T, ResourceLocation> idOf) {
        ListTag list = new ListTag();
        for (Map.Entry<T, long[]> entry : source.entrySet()) {
            ResourceLocation id = idOf.apply(entry.getKey());
            if (id == null) continue;                      // 理论上不会 ✓ 防御性跳过 ✗
            CompoundTag one = new CompoundTag();
            one.putString("id", id.toString());
            one.putLongArray("ring", entry.getValue());
            list.add(one);
        }
        return list;
    }

    public static ContainerRateData load(CompoundTag tag) {
        ContainerRateData data = new ContainerRateData();
        data.cursor = tag.getInt("cursor");
        data.filled = tag.getInt("filled");
        long[] energy = tag.getLongArray("energy");
        if (energy.length == RING) System.arraycopy(energy, 0, data.energyRing, 0, RING);
        loadMap(tag.getList("items", Tag.TAG_COMPOUND), data.itemRing, ForgeRegistries.ITEMS::getValue);
        loadMap(tag.getList("fluids", Tag.TAG_COMPOUND), data.fluidRing, ForgeRegistries.FLUIDS::getValue);
        return data;
    }

    private static <T> void loadMap(ListTag list, Map<T, long[]> target,
                                    java.util.function.Function<ResourceLocation, T> valueOf) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag one = list.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(one.getString("id"));
            if (id == null) continue;
            T value = valueOf.apply(id);
            if (value == null) continue;                   // 模组被卸掉 ⇒ 悄悄丢 ✓
            long[] ring = one.getLongArray("ring");
            if (ring.length == RING) target.put(value, ring);
        }
    }
}
