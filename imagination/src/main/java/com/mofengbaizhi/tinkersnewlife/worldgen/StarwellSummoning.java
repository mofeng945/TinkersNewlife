package com.mofengbaizhi.tinkersnewlife.worldgen;

import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Called only after a player finishes eating the Call. No per-tick world or inventory scan. */
public final class StarwellSummoning {
    private static final ResourceKey<Structure> ARENA = ResourceKey.create(Registries.STRUCTURE,
            new ResourceLocation("tinkersnewlife", "starwell_arena"));
    private StarwellSummoning() {}

    public static void afterEating(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.END || level.getDragonFight() == null
                || !level.getDragonFight().hasPreviouslyKilledDragon()) return;

        var start = level.structureManager().getStructureWithPieceAt(player.blockPosition(), ARENA);
        if (!start.isValid()) return;
        for (var piece : start.getPieces()) {
            if (!(piece instanceof StarwellArenaPiece arena)) continue;
            BlockPos floor = arena.altarSurface();
            Vec3 center = Vec3.atBottomCenterOf(floor);
            if (player.position().distanceToSqr(center) > 20 * 20) continue;
            var bounds = AABB.of(arena.getBoundingBox());
            if (!level.getEntitiesOfClass(KtuluMomo.class, bounds, boss -> !boss.isRemoved()).isEmpty()) return;
            KtuluMomo boss = ModEntities.KTULU_MOMO.get().create(level);
            if (boss == null) return;
            // Try the center first, then nearby points on the same flat altar if it is obstructed.
            for (int[] d : new int[][]{{0,0},{3,0},{-3,0},{0,3},{0,-3}}) {
                BlockPos pos = floor.offset(d[0], 0, d[1]);
                boss.moveTo(pos.getX()+0.5, pos.getY(), pos.getZ()+0.5, player.getYRot()+180, 0);
                if (!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),net.minecraft.core.Direction.UP)
                        || !level.noCollision(boss,boss.getBoundingBox()) || player.getBoundingBox().intersects(boss.getBoundingBox())) continue;
                if (level.addFreshEntity(boss) && !player.isCreative() && !player.isSpectator()) boss.setTarget(player);
                return;
            }
        }
    }
}
