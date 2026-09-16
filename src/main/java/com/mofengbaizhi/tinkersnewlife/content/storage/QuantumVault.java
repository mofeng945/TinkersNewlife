package com.mofengbaizhi.tinkersnewlife.content.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 量子背包·<b>6 级</b>的存储：<b>按物品数量存放</b>（类似 AE / RS 的存储磁盘）✓
 *
 * <ul>
 *   <li><b>不限种类</b>：每种物品一条记录（同 ID 但 NBT 不同算不同种类 ✓）；</li>
 *   <li><b>总量封顶</b>：{@link #TOTAL_CAPACITY} = 64 × 2 × 81 × 64 = <b>663552</b> 个物品 ✓
 *       （正好等于匠魂容量模型里"6 级 = 27×6 = 162 格 × 每格 4096" ✓，与 1~5 级一脉相承 ✓）；</li>
 *   <li>单种物品没有单独上限（总量封顶即上限 ✓），所以"一格装 8192 个"这种限制不存在 ✓。</li>
 * </ul>
 *
 * <p>与 1~5 级的关系：升级到 6 级时会把旧的格子内容<b>整体并入</b>这里（见
 * {@link QuantumVaultManager#migrateFrom}）✓ —— 容量只增不减，不会丢东西 ✓。
 */
public class QuantumVault {

    /** 诊断开关（排查界面点击用；正常可置 false ✓） */
    public static final boolean DEBUG = true;

    /** 总容量：64 × 2 × 81 × 64 = 663552 个物品 ✓ */
    public static final long TOTAL_CAPACITY = 64L * 2L * 81L * 64L;

    /** 物品种类键：物品 + 它的 NBT（无 NBT 为 null ✓） */
    public record Key(Item item, @Nullable CompoundTag tag) {
    }

    private final Map<Key, Long> amounts = new LinkedHashMap<>();
    private long total = 0L;

    public static Key keyOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return new Key(stack.getItem(), tag == null ? null : tag.copy());
    }

    /** 当前已存总数 */
    public long total() {
        return total;
    }

    /** 还剩多少空间 */
    public long freeSpace() {
        return Math.max(0L, TOTAL_CAPACITY - total);
    }

    /** 某种物品存了多少 */
    public long amountOf(ItemStack stack) {
        return amounts.getOrDefault(keyOf(stack), 0L);
    }

    public boolean isEmpty() {
        return amounts.isEmpty();
    }

    /**
     * 存入（只吃下放得下的部分 ✓）。
     *
     * @return 实际存入的数量
     */
    public int insert(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        long space = freeSpace();
        if (space <= 0L) return 0;
        int want = (int) Math.min(stack.getCount(), space);
        if (want <= 0) return 0;
        amounts.merge(keyOf(stack), (long) want, Long::sum);
        total += want;
        return want;
    }

    /**
     * 取出指定数量（不足则全取 ✓）。
     *
     * @return 取出的物品（空栈表示没有这种物品）
     */
    public ItemStack extract(ItemStack template, int want) {
        if (template == null || template.isEmpty() || want <= 0) return ItemStack.EMPTY;
        Key key = keyOf(template);
        Long have = amounts.get(key);
        if (have == null || have <= 0L) return ItemStack.EMPTY;
        int take = (int) Math.min(want, Math.min(have, Integer.MAX_VALUE));
        long left = have - take;
        if (left <= 0L) {
            amounts.remove(key);
        } else {
            amounts.put(key, left);
        }
        total -= take;
        ItemStack out = template.copy();
        out.setCount(take);
        return out;
    }

    /** 取出一种物品的全部 */
    public ItemStack extractAll(ItemStack template) {
        long have = amountOf(template);
        if (have <= 0L) return ItemStack.EMPTY;
        int take = (int) Math.min(have, Integer.MAX_VALUE);
        return extract(template, take);
    }

    /** 当前所有种类（按插入顺序；界面自己排序 ✓） */
    public List<Map.Entry<Key, Long>> entries() {
        return new ArrayList<>(amounts.entrySet());
    }

    public ItemStack stackOf(Key key, int count) {
        ItemStack stack = new ItemStack(key.item());
        if (key.tag() != null) stack.setTag(key.tag().copy());
        stack.setCount(count);
        return stack;
    }

    // ============================================================
    //  NBT
    // ============================================================

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (Map.Entry<Key, Long> e : amounts.entrySet()) {
            CompoundTag entry = new CompoundTag();
            ItemStack template = stackOf(e.getKey(), 1);
            template.save(entry);                       // 存成"数量 1 的模板 + 单独的数量" ✓
            entry.putLong("VaultCount", e.getValue());
            list.add(entry);
        }
        root.put("Items", list);
        root.putLong("Total", total);
        return root;
    }

    public void load(CompoundTag root) {
        amounts.clear();
        total = 0L;
        ListTag list = root.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ItemStack template = ItemStack.of(entry);
            if (template.isEmpty()) continue;
            long count = entry.getLong("VaultCount");
            if (count <= 0L) continue;
            amounts.merge(keyOf(template), count, Long::sum);
            total += count;
        }
        if (total > TOTAL_CAPACITY) total = TOTAL_CAPACITY;      // 防手改存档越界 ✓
    }
}
