package com.mofengbaizhi.tinkersnewlife.content.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Per-dimension reservations. A chunk unload is not the death of its occupant. */
@Mod.EventBusSubscriber(modid = "tinkersnewlife")
public final class MomoBossPresence extends SavedData {
    private final Map<UUID, Vec3> positions = new HashMap<>();
    private static MomoBossPresence in(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(MomoBossPresence::load, MomoBossPresence::new, "tnl_momo_boss_presence");
    }
    private static MomoBossPresence load(CompoundTag tag) {
        var data = new MomoBossPresence();
        for (Tag entry : tag.getList("Bosses", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) entry;
            if (row.hasUUID("Id")) data.positions.put(row.getUUID("Id"), new Vec3(row.getDouble("X"), row.getDouble("Y"), row.getDouble("Z")));
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        var rows = new ListTag();
        positions.forEach((id,pos) -> { var row = new CompoundTag(); row.putUUID("Id", id); row.putDouble("X", pos.x); row.putDouble("Y", pos.y); row.putDouble("Z", pos.z); rows.add(row); });
        tag.put("Bosses", rows);
        return tag;
    }
    static void remember(ServerLevel level, KtuluMomo boss) {
        var data = in(level);
        if (!boss.position().equals(data.positions.put(boss.getUUID(), boss.position()))) data.setDirty();
    }
    @SubscribeEvent public static void joining(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof KtuluMomo boss) || !(event.getLevel() instanceof ServerLevel level)) return;
        boss.setPersistenceRequired();
        if (event.loadedFromDisk()) return;
        for (var entry : in(level).positions.entrySet()) {
            if (entry.getKey().equals(boss.getUUID())) continue;
            var live = level.getEntity(entry.getKey()); // UUID lookup only; does not load chunks.
            Vec3 other = live == null ? entry.getValue() : live.position();
            if (boss.position().distanceToSqr(other) <= 256.0 * 256.0) { event.setCanceled(true); return; }
        }
    }
    @SubscribeEvent public static void leaving(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof KtuluMomo boss) || !(event.getLevel() instanceof ServerLevel level)) return;
        var reason = boss.getRemovalReason();
        if (reason == null || reason.shouldSave()) remember(level, boss);
        else { var data = in(level); if (data.positions.remove(boss.getUUID()) != null) data.setDirty(); }
    }
}
