package com.mofengbaizhi.tinkersnewlife.worldgen;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Select a footprint with bounded land fill and room to cut hills without generating or loading chunks. */
public final class SleepwakeChurchTerrain {
    static final int MAX_FILL = 3;
    static final int MAX_WATER_FILL = 16;
    private static final int MAX_CUT = 28;
    static final int STONE_COURSES = 3;
    private static final int[][] OFFSETS = {{0,0},{14,0},{0,14},{14,14},{7,7}};
    private SleepwakeChurchTerrain() { }

    private record Surface(int ground, int top) {}
    private record Site(BlockPos origin, long cost) {}

    public static Optional<BlockPos> selectOrigin(Structure.GenerationContext context, int x, int z, boolean whiteSpace) {
        // Keep the adjusted origin within the structure-start chunk for locating and serialization.
        Map<Long, Surface> cache = new HashMap<>();
        var villageBoxes = whiteSpace ? java.util.Collections.<net.minecraft.world.level.levelgen.structure.BoundingBox>emptyList()
                : SleepwakeVillageProbe.collect(context);
        Site best = null;
        Site fallback = null;
        boolean reserve = Math.floorMod(Math.floorDiv(context.chunkPos().x, 48), 2) == 0
                && Math.floorMod(Math.floorDiv(context.chunkPos().z, 48), 2) == 0;
        for (int[] offset : OFFSETS) {
            int ox = x + offset[0], oz = z + offset[1];
            int relation = whiteSpace ? 1 : SleepwakeVillageProbe.relation(villageBoxes, ox, oz);
            if (relation < 0 || (relation == 0 && !reserve)) continue;
            Site site = evaluate(context, cache, ox, oz);
            if (site == null) continue;
            if (relation == 1 && (best == null || site.cost < best.cost)) best = site;
            if (relation == 0 && (fallback == null || site.cost < fallback.cost)) fallback = site;
        }
        if (best == null) best = fallback;
        return best == null ? Optional.empty() : Optional.of(best.origin);
    }

    private static Site evaluate(Structure.GenerationContext c, Map<Long, Surface> cache, int x, int z) {
        int[] heights = new int[SleepwakeChurchPiece.WIDTH * SleepwakeChurchPiece.DEPTH];
        int count = 0;
        int highestWater = Integer.MIN_VALUE;
        int cap = Integer.MAX_VALUE;
        for (int dx = 0; dx < SleepwakeChurchPiece.WIDTH; dx++) {
            for (int dz = 0; dz < SleepwakeChurchPiece.DEPTH; dz++) {
                Surface surface = surface(c, cache, x + dx, z + dz);
                if (surface.ground <= c.heightAccessor().getMinBuildHeight()) return null;
                boolean wet = surface.top > surface.ground;
                if (wet) highestWater = Math.max(highestWater, surface.top);
                cap = Math.min(cap, surface.ground + (wet ? MAX_WATER_FILL : MAX_FILL));
                heights[count++] = surface.top;
            }
        }
        Arrays.sort(heights);
        int floor = Math.max(highestWater, Math.min(heights[count / 3], cap));
        if (floor > cap || heights[count - 1] - floor > MAX_CUT
                || floor + SleepwakeChurchPiece.HEIGHT > c.heightAccessor().getMaxBuildHeight()) return null;
        long cost = 0;
        for (int ground : heights) cost += Math.abs(ground - floor);
        // Entrance may climb a few natural steps, but never end in water or a cliff.
        for (int dx = 23; dx <= 27; dx++) for (int dz = -3; dz < 0; dz++) {
            Surface approach = surface(c, cache, x + dx, z + dz);
            if (approach.top > approach.ground || Math.abs(approach.ground - floor) > 3) return null;
            cost += 16L * Math.abs(approach.ground - floor);
        }
        return new Site(new BlockPos(x, floor, z), cost);
    }

    private static Surface surface(Structure.GenerationContext c, Map<Long, Surface> cache, int x, int z) {
        long key = net.minecraft.world.level.ChunkPos.asLong(x, z);
        return cache.computeIfAbsent(key, ignored -> {
            int ground = c.chunkGenerator().getFirstFreeHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, c.heightAccessor(), c.randomState());
            int top = c.chunkGenerator().getFirstFreeHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, c.heightAccessor(), c.randomState());
            return new Surface(ground, top);
        });
    }
}
