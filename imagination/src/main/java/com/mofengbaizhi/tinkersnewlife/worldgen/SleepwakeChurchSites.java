package com.mofengbaizhi.tinkersnewlife.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.server.level.ServerLevel;
import java.util.ArrayList;
import java.util.List;

/** Per-dimension persistent placement ledger: spacing is checked as distance, not grid spacing. */
public final class SleepwakeChurchSites extends SavedData {
    private final List<BlockPos> accepted = new ArrayList<>();
    private final java.util.Set<BlockPos> pending = new java.util.LinkedHashSet<>();
    public void queue(BlockPos pos) { if (pending.add(pos.immutable())) setDirty(); }
    public java.util.List<BlockPos> pending() { return java.util.List.copyOf(pending); }
    public void finish(BlockPos pos) { if (pending.remove(pos)) setDirty(); }
    private final java.util.Set<Long> evaluatedVillages = new java.util.HashSet<>();
    public static SleepwakeChurchSites get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(SleepwakeChurchSites::load,
                SleepwakeChurchSites::new, "tnl_sleepwake_church_sites");
    }
    public static SleepwakeChurchSites load(CompoundTag tag) {
        SleepwakeChurchSites data = new SleepwakeChurchSites();
        for (long pos : tag.getLongArray("Sites")) data.accepted.add(BlockPos.of(pos));
        for (long pos : tag.getLongArray("Pending")) data.pending.add(BlockPos.of(pos));
        for (long pos : tag.getLongArray("Villages")) data.evaluatedVillages.add(pos);
        return data;
    }
    public boolean evaluateVillageOnce(long chunk) {
        boolean added = evaluatedVillages.add(chunk);
        if (added) setDirty();
        return added;
    }
    public boolean canPlace(BlockPos origin, int minimumChunks) {
        long radius = minimumChunks * 16L;
        for (BlockPos other : accepted) {
            long dx = (long)origin.getX() - other.getX(), dz = (long)origin.getZ() - other.getZ();
            if (dx * dx + dz * dz < radius * radius) return false;
        }
        return true;
    }
    public void accept(BlockPos origin) { accepted.add(origin.immutable()); setDirty(); }
    @Override public CompoundTag save(CompoundTag tag) {
        tag.putLongArray("Pending", pending.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("Sites", accepted.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("Villages", evaluatedVillages.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }
}
