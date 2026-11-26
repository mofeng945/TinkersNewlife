package com.mofengbaizhi.tinkersnewlife.worldgen;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Deterministic, non-chunk-loading terrain query for a native Structure generation stub. */
public final class SleepwakeChurchTerrain {
    private SleepwakeChurchTerrain() { }
    public static Optional<BlockPos> highestOrigin(Structure.GenerationContext context, int originX, int originZ) {
        int highest = context.heightAccessor().getMinBuildHeight();
        // All footprint columns, not just corners: a hill in the centre must not cut into the nave.
        for (int x = originX; x < originX + SleepwakeChurchPiece.WIDTH; x++) {
            for (int z = originZ; z < originZ + SleepwakeChurchPiece.DEPTH; z++) {
                highest = Math.max(highest, context.chunkGenerator().getFirstFreeHeight(x, z,
                        Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState()));
            }
        }
        if (highest + SleepwakeChurchPiece.HEIGHT > context.heightAccessor().getMaxBuildHeight()) return Optional.empty();
        return Optional.of(new BlockPos(originX, highest, originZ));
    }
}
