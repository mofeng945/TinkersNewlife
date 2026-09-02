package com.mofengbaizhi.tinkersnewlife.content.gourd;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * 狱门疆维度坐标管理（SavedData，随狱门疆维度存档持久化）：
 * <ul>
 *   <li>记录所有已占用的封印坐标（每个狱门疆一个，作为基岩球笼中心）</li>
 *   <li>分配新坐标时保证与已占用坐标至少相距 50 格，防止球笼重叠</li>
 *   <li>释放封印时解放坐标</li>
 * </ul>
 */
public class GourdJailData extends SavedData {

    private static final String DATA_NAME = "tinkersnewlife_gourd_jails";
    private static final String KEY_CAGES = "cages";

    /** 已占用的球笼中心坐标 */
    private final List<BlockPos> occupied = new ArrayList<>();

    public static GourdJailData get(ServerLevel gourdLevel) {
        return gourdLevel.getDataStorage().computeIfAbsent(GourdJailData::new, GourdJailData::new, DATA_NAME);
    }

    public GourdJailData() {}

    public GourdJailData(CompoundTag tag) {
        ListTag list = tag.getList(KEY_CAGES, Tag.TAG_LONG);
        for (int i = 0; i < list.size(); i++) {
            occupied.add(BlockPos.of(((net.minecraft.nbt.LongTag) list.get(i)).getAsLong()));
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (BlockPos pos : occupied) {
            list.add(net.minecraft.nbt.LongTag.valueOf(pos.asLong()));
        }
        tag.put(KEY_CAGES, list);
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
}
