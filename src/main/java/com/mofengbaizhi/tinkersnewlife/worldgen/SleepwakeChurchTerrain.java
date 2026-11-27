package com.mofengbaizhi.tinkersnewlife.worldgen;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Select a dry, nearly level footprint without generating or loading chunks. */
public final class SleepwakeChurchTerrain {
    static final int MAX_FILL = 6;
    static final int STONE_COURSES = 3;
    private static final int[][] OFFSETS = {{0,0},{14,0},{0,14},{14,14},{7,7}};
    private SleepwakeChurchTerrain() { }

    private record Surface(int ground, boolean wet) {}
    private record Site(BlockPos origin, long cost) {}

    public static Optional<BlockPos> selectOrigin(Structure.GenerationContext context, int x, int z, boolean whiteSpace) {
        // Keep the adjusted origin within the structure-start chunk for locating and serialization.
        Map<Long, Surface> cache = new HashMap<>();
        Site best = null;
        for (int[] offset : OFFSETS) {
            int ox = x + offset[0], oz = z + offset[1];
            if (!whiteSpace && !SleepwakeVillageProbe.nearVillage(context, ox, oz)) continue;
            Site site = evaluate(context, cache, ox, oz);
            if (site != null && (best == null || site.cost < best.cost)) best = site;
        }
        return best == null ? Optional.empty() : Optional.of(best.origin);
    }

    private static Site evaluate(Structure.GenerationContext c, Map<Long, Surface> cache, int x, int z) {
        int[] heights = new int[SleepwakeChurchPiece.WIDTH * SleepwakeChurchPiece.DEPTH];
        int count = 0, min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int dx = 0; dx < SleepwakeChurchPiece.WIDTH; dx++) {
            for (int dz = 0; dz < SleepwakeChurchPiece.DEPTH; dz++) {
                Surface surface = surface(c, cache, x + dx, z + dz);
                if (surface.wet || surface.ground <= c.heightAccessor().getMinBuildHeight()) return null;
                heights[count++] = surface.ground;
                min = Math.min(min, surface.ground);
                max = Math.max(max, surface.ground);
                if (max - min > MAX_FILL + 2) return null;
            }
        }
        Arrays.sort(heights);
        int floor = heights[count * 9 / 10];
        if (floor - min > MAX_FILL || max - floor > 2 || floor + SleepwakeChurchPiece.HEIGHT > c.heightAccessor().getMaxBuildHeight()) return null;
        long cost = 0;
        int excavated = 0;
        for (int ground : heights) {
            if (ground > floor) excavated++;
            cost += Math.abs(ground - floor);
        }
        if (excavated > count / 10) return null;
        // The front approach is centred on the nave; reject water, cliffs and hanging entrances.
        for (int dx = 22; dx <= 28; dx++) for (int dz = -4; dz <= 0; dz++) {
            Surface approach = surface(c, cache, x + dx, z + dz);
            if (approach.wet || Math.abs(approach.ground - floor) > 1) return null;
        }
        return new Site(new BlockPos(x, floor, z), cost);
    }

    private static Surface surface(Structure.GenerationContext c, Map<Long, Surface> cache, int x, int z) {
        long key = net.minecraft.world.level.ChunkPos.asLong(x, z);
        return cache.computeIfAbsent(key, ignored -> {
            int ground = c.chunkGenerator().getFirstFreeHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, c.heightAccessor(), c.randomState());
            int top = c.chunkGenerator().getFirstFreeHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, c.heightAccessor(), c.randomState());
            return new Surface(ground, top > ground);
        });
    }
}
