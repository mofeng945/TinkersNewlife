package com.mofengbaizhi.tinkersnewlife.content.rate;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * 「容器产率」的<b>每维度存档</b>（§735，{@link net.minecraft.world.level.saveddata.SavedData} ✓）
 *
 * <h2>存什么（口径：只算<b>净</b>增加 ✓）</h2>
 * 每个物品一条<b>定长环形缓冲</b>：<b>每个 10 分钟区间的"净增量"</b> ✓
 * （净增量 = 该区间内"两次采样都观测到的全部来源"的物品总数之差 ✓
 *  ⇒ 维度内搬运自动抵消 ✓ 消耗/玩家取走会算成负值 ✓ —— 这就是"净产率"的定义 ✓）。
 *
 * <ul>
 *   <li>{@code RING = 6} ⇒ 环满 = <b>最近 1 小时</b> ✓（用户要的"个/时"就是从这来的 ✓）；</li>
 *   <li>速率算法：{@code 最近 n 个区间之和 ÷ (n × 10 分钟) × 60} ✓ ⇒ <b>个/时</b> ✓；</li>
 *   <li>体积：每物品 6 个 long ✓ ⇒ 2000 物品也才 96 KB（NBT 压缩后更小 ✓）。</li>
 * </ul>
 *
 * <h2>⚠ 为什么不落盘"上一次的来源快照"</h2>
 * 那份快照是"每来源 × 每物品"的计数 ✗（可能几万个数字 ✗）⇒ 不落盘 ✓
 * 代价：<b>重启后的第一个 10 分钟区间没有基线 ⇒ 该区间记 0</b> ✗（如实标注 ✓ 不谎报 ✓）。
 */
public class ContainerRateData extends SavedData {

    /** 环形缓冲长度：6 个区间 × 10 分钟 = 1 小时 ✓ */
    public static final int RING = 6;

    private static final String DATA_NAME = TinkersNewlife.MOD_ID + "_container_rate";

    /** 物品 → 环形缓冲（下标 = 区间序号 mod RING ✓） */
    private final Map<Item, long[]> ring = new HashMap<>();
    /** 最后写入的槽位（-1 = 还没写过一个完整区间 ✓） */
    private int cursor = -1;
    /** 已写入的区间数（≤ RING ✓） */
    private int filled = 0;

    // ============================================================
    //  取用（每维度一份 ✓ 与 CurseVaultData 同一套写法 ✓）
    // ============================================================

    public static ContainerRateData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ContainerRateData::load, ContainerRateData::new, DATA_NAME);
    }

    // ============================================================
    //  写入：一个区间的净增量
    // ============================================================

    /**
     * 推入一个 10 分钟区间的<b>净增量表</b> ✓（正数 = 净增 ✓ 负数 = 净减 ✓）。
     * <p>⚠ 表里没有的物品这一格要<b>清零</b> ✗（否则会把 6 个区间前的旧值当成新值 ✓）。
     * <p>§736：形参改成 fastutil 的 {@code Object2LongMap} ✓（免装箱 ✓ 大基地下省一大截 GC ✓）。
     */
    public void pushInterval(it.unimi.dsi.fastutil.objects.Object2LongMap<Item> delta) {
        cursor = (cursor + 1) % RING;
        filled = Math.min(RING, filled + 1);
        for (long[] slot : ring.values()) {
            slot[cursor] = 0L;
        }
        for (it.unimi.dsi.fastutil.objects.Object2LongMap.Entry<Item> entry : delta.object2LongEntrySet()) {
            ring.computeIfAbsent(entry.getKey(), key -> new long[RING])[cursor] = entry.getLongValue();
        }
        setDirty();
    }

    /** 清空全部统计（对应"重开统计"✓ 本轮没有命令，留给以后的接口 ✓） */
    public void resetAll() {
        ring.clear();
        cursor = -1;
        filled = 0;
        setDirty();
    }

    // ============================================================
    //  读取：这就是本轮要交付的"方法接口" ✓
    // ============================================================

    /** 最近一个 10 分钟区间的净增量（个 ✓）；还没攒够一个区间 ⇒ 0 ✓ */
    public long lastIntervalNet(Item item) {
        long[] slot = ring.get(item);
        return (slot == null || filled <= 0 || cursor < 0) ? 0L : slot[cursor];
    }

    /** 已攒够的区间数（0 ~ {@link #RING} ✓）—— 调用方可据此判断"数据够不够" ✓ */
    public int intervals() {
        return filled;
    }

    /**
     * <b>净产率（个/时）</b> ✓ —— 取最近 {@code intervals} 个区间（≤ {@link #RING} ✓，
     * 不够就用现有的 ✓）求平均再折算到 1 小时 ✓。
     *
     * @param intervals 想要几个区间（1 个 = 只看最近 10 分钟 ✓；6 个 = 最近 1 小时 ✓）
     */
    public double netPerHour(Item item, int intervals) {
        long[] slot = ring.get(item);
        int n = Math.min(Math.max(0, intervals), filled);
        if (slot == null || n <= 0) return 0.0D;
        long sum = 0L;
        for (int i = 0; i < n; i++) {
            sum += slot[Math.floorMod(cursor - i, RING)];
        }
        double minutes = n * (ContainerRateManager.INTERVAL_TICKS / 1200.0D);   // 1200 tick = 1 分钟 ✓
        return sum / minutes * 60.0D;
    }

    /** 全部物品的净产率快照（个/时 ✓）—— 排序/筛选交给调用方 ✓ */
    public Map<Item, Double> netPerHourAll(int intervals) {
        Map<Item, Double> out = new HashMap<>();
        for (Item item : ring.keySet()) {
            double v = netPerHour(item, intervals);
            if (v != 0.0D) out.put(item, v);
        }
        return out;
    }

    // ============================================================
    //  存 / 读
    // ============================================================

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("cursor", cursor);
        tag.putInt("filled", filled);
        ListTag list = new ListTag();
        for (Map.Entry<Item, long[]> entry : ring.entrySet()) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(entry.getKey());
            if (id == null) continue;                      // 理论上不会 ✓ 防御性跳过 ✗
            CompoundTag one = new CompoundTag();
            one.putString("item", id.toString());
            one.putLongArray("ring", entry.getValue());
            list.add(one);
        }
        tag.put("items", list);
        return tag;
    }

    public static ContainerRateData load(CompoundTag tag) {
        ContainerRateData data = new ContainerRateData();
        data.cursor = tag.getInt("cursor");
        data.filled = tag.getInt("filled");
        ListTag list = tag.getList("items", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag one = list.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(one.getString("item"));
            if (id == null) continue;
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item == null) continue;                    // 模组被卸掉 ⇒ 悄悄丢 ✓
            long[] slot = one.getLongArray("ring");
            if (slot.length == RING) data.ring.put(item, slot);
        }
        return data;
    }
}
