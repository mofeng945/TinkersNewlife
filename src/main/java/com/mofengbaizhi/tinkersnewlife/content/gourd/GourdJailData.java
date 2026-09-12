package com.mofengbaizhi.tinkersnewlife.content.gourd;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 狱门疆维度坐标管理（SavedData，随狱门疆维度存档持久化）：
 * <ul>
 *   <li>记录所有已占用的封印坐标（每个狱门疆一个，作为基岩球笼中心）</li>
 *   <li>分配新坐标时保证与已占用坐标至少相距 50 格，防止球笼重叠</li>
 *   <li>释放封印时解放坐标</li>
 *   <li>⭐ 记录「当前被封印的玩家 UUID → 球笼中心」与「已释放但当时不在线的玩家 → 返回点」，
 *       供登录/每 tick 兜底判定：球笼已拆却还留在狱门疆维度的人必须被送出去，绝不能坠入虚空</li>
 * </ul>
 */
public class GourdJailData extends SavedData {

    private static final String DATA_NAME = "tinkersnewlife_gourd_jails";
    private static final String KEY_CAGES = "cages";
    private static final String KEY_SEALED_PLAYERS = "sealedPlayers";
    private static final String KEY_PENDING_RETURNS = "pendingReturns";

    /** 已占用的球笼中心坐标 */
    private final List<BlockPos> occupied = new ArrayList<>();
    /** 当前被封印的玩家：UUID → 球笼中心 */
    private final Map<UUID, BlockPos> sealedPlayers = new HashMap<>();
    /** 已释放但当时不在线的玩家：UUID → 返回坐标（上线后立即送回，随后删除记录） */
    private final Map<UUID, PendingReturn> pendingReturns = new HashMap<>();

    /** 待送回坐标（维度 id + 坐标） */
    public record PendingReturn(String dimension, double x, double y, double z) {}

    public static GourdJailData get(ServerLevel gourdLevel) {
        return gourdLevel.getDataStorage().computeIfAbsent(GourdJailData::new, GourdJailData::new, DATA_NAME);
    }

    public GourdJailData() {}

    public GourdJailData(CompoundTag tag) {
        ListTag list = tag.getList(KEY_CAGES, Tag.TAG_LONG);
        for (int i = 0; i < list.size(); i++) {
            occupied.add(BlockPos.of(((net.minecraft.nbt.LongTag) list.get(i)).getAsLong()));
        }
        ListTag sealed = tag.getList(KEY_SEALED_PLAYERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < sealed.size(); i++) {
            CompoundTag e = sealed.getCompound(i);
            if (!e.hasUUID("id")) continue;
            sealedPlayers.put(e.getUUID("id"), BlockPos.of(e.getLong("cage")));
        }
        ListTag pending = tag.getList(KEY_PENDING_RETURNS, Tag.TAG_COMPOUND);
        for (int i = 0; i < pending.size(); i++) {
            CompoundTag e = pending.getCompound(i);
            if (!e.hasUUID("id")) continue;
            pendingReturns.put(e.getUUID("id"), new PendingReturn(
                    e.getString("dim"), e.getDouble("x"), e.getDouble("y"), e.getDouble("z")));
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (BlockPos pos : occupied) {
            list.add(net.minecraft.nbt.LongTag.valueOf(pos.asLong()));
        }
        tag.put(KEY_CAGES, list);
        ListTag sealed = new ListTag();
        for (Map.Entry<UUID, BlockPos> e : sealedPlayers.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", e.getKey());
            c.putLong("cage", e.getValue().asLong());
            sealed.add(c);
        }
        tag.put(KEY_SEALED_PLAYERS, sealed);
        ListTag pending = new ListTag();
        for (Map.Entry<UUID, PendingReturn> e : pendingReturns.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", e.getKey());
            c.putString("dim", e.getValue().dimension());
            c.putDouble("x", e.getValue().x());
            c.putDouble("y", e.getValue().y());
            c.putDouble("z", e.getValue().z());
            pending.add(c);
        }
        tag.put(KEY_PENDING_RETURNS, pending);
        return tag;
    }

    /** 分配一个封印坐标：与所有已占用坐标相距 ≥50 格（X 轴每 50 格递增，Y 固定在 0 附近） */
    public BlockPos assignCoordinate() {
        int index = occupied.size();
        BlockPos pos = new BlockPos(index * 50 + 25, 0, 25);
        occupied.add(pos);
        setDirty();
        return pos;
    }

    /** 释放坐标（清除球笼后调用） */
    public void releaseCoordinate(BlockPos pos) {
        occupied.remove(pos);
        setDirty();
    }

    public boolean isOccupied(BlockPos pos) {
        return occupied.contains(pos);
    }

    /** 已占用球笼中距离 {@code pos} 不超过 {@code maxDist} 格的那个（旧存档补登记用），无则 null */
    public BlockPos occupiedNear(BlockPos pos, double maxDist) {
        BlockPos best = null;
        double bestSq = maxDist * maxDist;
        for (BlockPos cage : occupied) {
            double d = cage.distSqr(pos);
            if (d <= bestSq) {
                bestSq = d;
                best = cage;
            }
        }
        return best;
    }

    // ============================================================
    //  封印名单 / 待送回名单
    // ============================================================

    /** 登记被封印玩家（封印成功时调用） */
    public void registerPrisoner(UUID id, BlockPos cage) {
        if (id == null) return;
        sealedPlayers.put(id, cage);
        pendingReturns.remove(id);
        setDirty();
    }

    /** 解除封印登记（释放时调用） */
    public void unregisterPrisoner(UUID id) {
        if (id == null) return;
        if (sealedPlayers.remove(id) != null) setDirty();
    }

    public boolean isSealed(UUID id) {
        return id != null && sealedPlayers.containsKey(id);
    }

    public BlockPos sealedCage(UUID id) {
        return id == null ? null : sealedPlayers.get(id);
    }

    /** 记下「该玩家出狱后应回到哪里」（囚徒不在线时用，上线即送回） */
    public void queuePendingReturn(UUID id, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim,
                                   net.minecraft.world.phys.Vec3 pos) {
        if (id == null || dim == null || pos == null) return;
        pendingReturns.put(id, new PendingReturn(dim.location().toString(), pos.x, pos.y, pos.z));
        setDirty();
    }

    /** 取出并清除待送回记录（无则 null） */
    public PendingReturn pollPendingReturn(UUID id) {
        if (id == null) return null;
        PendingReturn r = pendingReturns.remove(id);
        if (r != null) setDirty();
        return r;
    }
}
