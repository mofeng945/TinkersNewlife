package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 「呪蔵」方块里咒力的**权威存储**（每维度一份 {@link SavedData}）。
 *
 * <h2>为什么存在 world data 而不是方块实体里</h2>
 * <ul>
 *   <li><b>未加载区块也要能统计/灌注</b>：玩家咒力恢复是"先灌瓶 → 再灌呪蔵 → 最后进核心池"，
 *       若咒力存在方块实体上，区块没加载就没办法把咒力存进去，玩家会误以为自己没存量；</li>
 *   <li><b>统计要跨维度</b>：这里每维度一份数据，统计时遍历所有维度即可；</li>
 *   <li><b>避免两套账</b>：方块实体只负责流体能力（接咒力残秽），不存咒力，天生不会和这里打架。</li>
 * </ul>
 *
 * <p>条目：{@code 坐标 → (绑定使用者 UUID, 已存咒力)}。放置时写入，回收/被外力移除时删除。
 */
public class CurseVaultData extends SavedData {

    /** 单个呪蔵的容量（10 万咒力） */
    public static final double CAPACITY = 100000.0;
    /** 1 mb 咒力残秽换算的咒力（与封呪瓶一致：1mb = 10 咒力） */
    public static final int POWER_PER_MB = CurseBottleHelper.POWER_PER_MB;
    /** 单个呪蔵能装的咒力残秽（mb）= 10000 */
    public static final int CAPACITY_MB = (int) (CAPACITY / POWER_PER_MB);

    private static final String DATA_NAME = TinkersNewlife.MOD_ID + "_curse_vaults";

    /** 一个呪蔵条目 */
    public static final class Entry {
        public final UUID owner;
        public double power;

        Entry(UUID owner, double power) {
            this.owner = owner;
            this.power = power;
        }
    }

    private final Map<BlockPos, Entry> vaults = new HashMap<>();

    // ============================================================
    //  取用
    // ============================================================

    public static CurseVaultData get(Level level) {
        if (!(level instanceof ServerLevel server)) throw new IllegalStateException("呪蔵数据只能在服务端访问");
        return server.getDataStorage().computeIfAbsent(CurseVaultData::load, CurseVaultData::new, DATA_NAME);
    }

    @Nullable
    public static CurseVaultData getOrNull(@Nullable Level level) {
        return level instanceof ServerLevel server
                ? server.getDataStorage().computeIfAbsent(CurseVaultData::load, CurseVaultData::new, DATA_NAME)
                : null;
    }

    // ============================================================
    //  条目操作
    // ============================================================

    /** 放置时登记（已存在则保留原有咒力并改绑使用者；放置的物品自带咒力时覆盖） */
    public Entry register(BlockPos pos, UUID owner, double initialPower) {
        Entry entry = vaults.get(pos);
        if (entry == null) {
            entry = new Entry(owner, clamp(initialPower));
            vaults.put(pos.immutable(), entry);
        } else {
            // 同一坐标已有数据（例如回收失败/世界编辑）：保留较大者，避免凭空吞掉玩家存量
            entry.power = clamp(Math.max(entry.power, initialPower));
        }
        setDirty();
        return entry;
    }

    @Nullable
    public Entry get(BlockPos pos) {
        return vaults.get(pos);
    }

    /** 删除条目并返回它（回收方块时用；没有则返回 null） */
    @Nullable
    public Entry remove(BlockPos pos) {
        Entry removed = vaults.remove(pos);
        if (removed != null) setDirty();
        return removed;
    }

    /** 该坐标是否已登记（+ 是否属于某人） */
    public boolean isBoundTo(BlockPos pos, UUID owner) {
        Entry entry = vaults.get(pos);
        return entry != null && entry.owner.equals(owner);
    }

    /** 坐标是否是呪蔵（不论归属） */
    public boolean isVault(BlockPos pos) {
        return vaults.containsKey(pos);
    }

    public double getPower(BlockPos pos) {
        Entry entry = vaults.get(pos);
        return entry == null ? 0 : entry.power;
    }

    /** 朝该呪蔵存入咒力，返回实际存进去的量（未登记/已满返回 0） */
    public double addPower(BlockPos pos, double amount) {
        Entry entry = vaults.get(pos);
        if (entry == null || amount <= 0) return 0;
        double added = Math.min(amount, CAPACITY - entry.power);
        if (added <= 0) return 0;
        entry.power += added;
        setDirty();
        return added;
    }

    /** 从该呪蔵扣咒力，返回实际扣掉的量 */
    public double consumePower(BlockPos pos, double amount) {
        Entry entry = vaults.get(pos);
        if (entry == null || amount <= 0) return 0;
        double used = Math.min(entry.power, amount);
        if (used <= 0) return 0;
        entry.power -= used;
        setDirty();
        return used;
    }

    /** 该呪蔵剩余可存容量 */
    public double getFreeSpace(BlockPos pos) {
        Entry entry = vaults.get(pos);
        return entry == null ? 0 : Math.max(0, CAPACITY - entry.power);
    }

    // ============================================================
    //  按使用者聚合（统计用；跨维度遍历所有 ServerLevel）
    // ============================================================

    /** 某个使用者绑定的所有呪蔵（跨维度）的咒力总和 */
    public static double sumPowerFor(MinecraftServer server, UUID owner) {
        double sum = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entry entry : get(level).vaults.values()) {
                if (entry.owner.equals(owner)) sum += entry.power;
            }
        }
        return sum;
    }

    /** 某个使用者绑定的呪蔵数量（跨维度） */
    public static int countFor(MinecraftServer server, UUID owner) {
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entry entry : get(level).vaults.values()) {
                if (entry.owner.equals(owner)) count++;
            }
        }
        return count;
    }

    /** 某个使用者绑定的呪蔵总容量（跨维度） */
    public static double capacityFor(MinecraftServer server, UUID owner) {
        return countFor(server, owner) * CAPACITY;
    }

    /**
     * 把咒力依次灌进该使用者的呪蔵，返回灌不下的余量。
     * <p>不要求区块加载（数据在 world data 里）。
     * <p>⭐ 咒力恢复是**每 tick** 调用的，所以这里直接遍历、不建临时 List（零分配）。
     */
    public static double storeForPlayer(MinecraftServer server, UUID owner, double amount) {
        double remaining = amount;
        for (ServerLevel level : server.getAllLevels()) {
            CurseVaultData data = get(level);
            if (data.vaults.isEmpty()) continue;
            for (Map.Entry<BlockPos, Entry> e : data.vaults.entrySet()) {
                if (remaining <= 0) return 0;
                if (!e.getValue().owner.equals(owner)) continue;
                remaining -= data.addPower(e.getKey(), remaining);
            }
        }
        return Math.max(0, remaining);
    }

    /** 从该使用者的呪蔵里扣咒力，返回实际扣掉的量（同样零分配） */
    public static double consumeForPlayer(MinecraftServer server, UUID owner, double amount) {
        double remaining = amount;
        double used = 0;
        for (ServerLevel level : server.getAllLevels()) {
            CurseVaultData data = get(level);
            if (data.vaults.isEmpty()) continue;
            for (Map.Entry<BlockPos, Entry> e : data.vaults.entrySet()) {
                if (remaining <= 0) break;
                if (!e.getValue().owner.equals(owner)) continue;
                double took = data.consumePower(e.getKey(), remaining);
                used += took;
                remaining -= took;
            }
        }
        return used;
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(value, CAPACITY));
    }

    // ============================================================
    //  存档
    // ============================================================

    public static CurseVaultData load(CompoundTag tag) {
        CurseVaultData data = new CurseVaultData();
        ListTag list = tag.getList("vaults", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            BlockPos pos = BlockPos.of(entryTag.getLong("pos"));
            UUID owner = entryTag.hasUUID("owner") ? entryTag.getUUID("owner") : null;
            if (owner == null) continue;
            data.vaults.put(pos, new Entry(owner, clamp(entryTag.getDouble("power"))));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, Entry> e : vaults.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putLong("pos", e.getKey().asLong());
            entryTag.putUUID("owner", e.getValue().owner);
            entryTag.putDouble("power", e.getValue().power);
            list.add(entryTag);
        }
        tag.put("vaults", list);
        return tag;
    }
}
