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
        var sets = c.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        var placementState = net.minecraft.world.level.chunk.ChunkGeneratorStructureState.createForNormal(
                c.randomState(), c.seed(), c.biomeSource(), sets.asLookup());
        for (var set : sets) {
            if (!(set.placement() instanceof RandomSpreadStructurePlacement placement)) continue;
            if (set.structures().stream().noneMatch(e -> e.structure().is(villages))) continue;
            for (int rx=-1;rx<=1;rx++) for(int rz=-1;rz<=1;rz++) {
                ChunkPos candidate=placement.getPotentialStructureChunk(c.seed(), c.chunkPos().x+rx*placement.spacing(),c.chunkPos().z+rz*placement.spacing());
                if (!placement.isStructureChunk(placementState, candidate.x, candidate.z)) continue;
                if (Math.abs(candidate.getMinBlockX()-x)>256 || Math.abs(candidate.getMinBlockZ()-z)>256) continue;
                // Match vanilla weighted selection and retry order, not every possible entry.
                var remaining = new java.util.ArrayList<>(set.structures());
                var selection = new net.minecraft.world.level.levelgen.WorldgenRandom(
                        new net.minecraft.world.level.levelgen.LegacyRandomSource(0L));
                selection.setLargeFeatureSeed(c.seed(), candidate.x, candidate.z);
                while (!remaining.isEmpty()) {
                    int total = remaining.stream().mapToInt(e -> e.weight()).sum();
                    int roll = remaining.size() == 1 ? 0 : selection.nextInt(total);
                    int index = 0;
                    while (roll >= remaining.get(index).weight()) roll -= remaining.get(index++).weight();
                    var entry = remaining.remove(index);
                    var village=entry.structure().value();
                    // Data packs may accidentally tag this church as a village. Never recurse.
                    if (village instanceof SleepwakeChurchStructure) continue;
                    var start=village.generate(c.registryAccess(),c.chunkGenerator(),c.biomeSource(),c.randomState(),
                            c.structureTemplateManager(),c.seed(),candidate,0,c.heightAccessor(), village.biomes()::contains);
                    if(!start.isValid()) continue;
                    // A valid non-village wins this set; later village alternatives never spawn.
                    if (!entry.structure().is(villages)) break;
                    var box=start.getBoundingBox();
                    int dx=Math.max(Math.max(box.minX()- (x+50), x-box.maxX()),0);
                    int dz=Math.max(Math.max(box.minZ()- (z+72), z-box.maxZ()),0);
                    if(dx*dx+dz*dz<=2500 && (dx>0 || dz>0)) return true;
                    break; // Vanilla stops at the first valid structure in this set.
                }
            }
        }
        return false;
    }
    private SleepwakeVillageProbe() {}
}
