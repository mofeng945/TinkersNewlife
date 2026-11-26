package com.mofengbaizhi.tinkersnewlife.worldgen;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayDeque;

/** Placement is queued from actual loaded starts, never recursively searches worldgen. */
// Legacy event placement disabled: native StructureStart generation owns new churches.
public final class SleepwakeChurchGeneration {
    private record Job(ServerLevel level, BlockPos origin) {}
    private static final ArrayDeque<Job> QUEUE = new ArrayDeque<>();
    private static final TagKey<Structure> VILLAGE = TagKey.create(Registries.STRUCTURE, new ResourceLocation("minecraft", "village"));
    @SubscribeEvent public static void onChunk(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !event.isNewChunk()) return;
        var chunk = event.getChunk();
        // Read chunk starts only; server.execute moves all ledger/placement work onto server thread.
        if (level.dimension() == Level.OVERWORLD) {
            for (var entry : chunk.getAllStarts().entrySet()) {
                var holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE).wrapAsHolder(entry.getKey());
                if (!holder.is(VILLAGE) || !entry.getValue().isValid()) continue;
                var box = entry.getValue().getBoundingBox();
                BlockPos site = new BlockPos(box.maxX() + 12, 0, box.minZ());
                long key = chunk.getPos().toLong();
                level.getServer().execute(() -> {
                    var ledger = SleepwakeChurchSites.get(level);
                    if (!ledger.evaluateVillageOnce(key)) return;
                    var random = net.minecraft.util.RandomSource.create(level.getSeed() ^ key ^ 0x534C454550L);
                    if (random.nextFloat() < 0.25F && ledger.canPlace(site, 50)) SleepwakeChurchSites.get(level).queue(site);
                });
            }
        } else if (level.dimension().location().equals(new ResourceLocation("tinkersnewlife", "great_white_space"))) {
            int x = chunk.getPos().x, z = chunk.getPos().z;
            if (Math.floorMod(x, 160) != 40 || Math.floorMod(z, 160) != 40) return;
            BlockPos site = new BlockPos(x * 16, 0, z * 16);
            level.getServer().execute(() -> { if (SleepwakeChurchSites.get(level).canPlace(site, 80)) SleepwakeChurchSites.get(level).queue(site); });
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 20 != 0) return;
        QUEUE.clear(); // Jobs are reconstructed from the durable ledger; never retain stale ServerLevel references.
        for (ServerLevel candidateLevel : event.getServer().getAllLevels())
            for (BlockPos site : SleepwakeChurchSites.get(candidateLevel).pending()) {
                boolean loaded = true;
                for (int x = site.getX() >> 4; x <= (site.getX()+50) >> 4; x++)
                    for (int z = site.getZ() >> 4; z <= (site.getZ()+72) >> 4; z++)
                        if (!candidateLevel.hasChunk(x,z)) loaded = false;
                if (loaded) { QUEUE.add(new Job(candidateLevel,site)); break; }
            }
        if (QUEUE.isEmpty()) return;
        Job job = QUEUE.removeFirst();
        ServerLevel level = job.level;
        if (level.getServer().getLevel(level.dimension()) != level) return;
        int separation = level.dimension() == Level.OVERWORLD ? 50 : 80;
        if (!SleepwakeChurchSites.get(level).canPlace(job.origin, separation)) { SleepwakeChurchSites.get(level).finish(job.origin); return; }
        // Defer until the full footprint is loaded, rather than forcing chunk generation.
        for (int x = job.origin.getX() >> 4; x <= (job.origin.getX()+50) >> 4; x++)
            for (int z = job.origin.getZ() >> 4; z <= (job.origin.getZ()+72) >> 4; z++)
                if (!level.hasChunk(x,z)) { QUEUE.addLast(job); return; }
        int highest = level.getMinBuildHeight();
        for (int x=0;x<51;x++) for(int z=0;z<73;z++) highest = Math.max(highest,
                level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,job.origin.getX()+x,job.origin.getZ()+z));
        if(highest+82>=level.getMaxBuildHeight()) { SleepwakeChurchSites.get(level).finish(job.origin); return; }
        BlockPos origin = job.origin.atY(highest);
        var template = level.getStructureManager().get(new ResourceLocation("tinkersnewlife","sleepwake_church"));
        if(template.isEmpty()) return;
        for(int x=0;x<51;x++) for(int z=0;z<73;z++) {
            int ground=level.getHeight(Heightmap.Types.OCEAN_FLOOR,origin.getX()+x,origin.getZ()+z);
            for(int y=ground;y<highest;y++) level.setBlock(new BlockPos(origin.getX()+x,y,origin.getZ()+z),Blocks.STONE_BRICKS.defaultBlockState(),2);
        }
        if(!template.get().placeInWorld(level,origin,origin,new StructurePlaceSettings().setIgnoreEntities(true),level.random,2)) return;
        SleepwakeChurchSites.get(level).accept(origin);
        SleepwakeChurchSites.get(level).finish(job.origin);
        var merchant=ModEntities.MOMO_MERCHANT.get().create(level);
        if(merchant!=null){merchant.moveTo(origin.getX()+25.5,origin.getY()+5,origin.getZ()+58.5,180,0);merchant.setChurchResident();level.addFreshEntity(merchant);}
        TinkersNewlife.LOGGER.info("Sleepwake church generated in {} at {}",level.dimension().location(),origin);
    }
}
