package com.mofengbaizhi.tinkersnewlife.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/** Reconstruct nearby village starts using seed/generator only: never loads chunks. */
final class SleepwakeVillageProbe {
    static boolean nearVillage(Structure.GenerationContext c, int x, int z) {
        var villages = TagKey.create(Registries.STRUCTURE, new ResourceLocation("minecraft", "village"));
        for (var set : c.registryAccess().registryOrThrow(Registries.STRUCTURE_SET)) {
            if (!(set.placement() instanceof RandomSpreadStructurePlacement placement)) continue;
            if (set.structures().stream().noneMatch(e -> e.structure().is(villages))) continue;
            for (int rx=-1;rx<=1;rx++) for(int rz=-1;rz<=1;rz++) {
                ChunkPos candidate=placement.getPotentialStructureChunk(c.seed(), c.chunkPos().x+rx*placement.spacing(),c.chunkPos().z+rz*placement.spacing());
                if (Math.abs(candidate.getMinBlockX()-x)>256 || Math.abs(candidate.getMinBlockZ()-z)>256) continue;
                for(var entry:set.structures()) {
                    if(!entry.structure().is(villages)) continue;
                    var village=entry.structure().value();
                    var start=village.generate(c.registryAccess(),c.chunkGenerator(),c.biomeSource(),c.randomState(),
                            c.structureTemplateManager(),c.seed(),candidate,0,c.heightAccessor(), village.biomes()::contains);
                    if(!start.isValid()) continue;
                    var box=start.getBoundingBox();
                    int dx=Math.max(Math.max(box.minX()- (x+50), x-box.maxX()),0);
                    int dz=Math.max(Math.max(box.minZ()- (z+72), z-box.maxZ()),0);
                    if(dx*dx+dz*dz<=2500 && (dx>0 || dz>0)) return true;
                }
            }
        }
        return false;
    }
    private SleepwakeVillageProbe() {}
}
