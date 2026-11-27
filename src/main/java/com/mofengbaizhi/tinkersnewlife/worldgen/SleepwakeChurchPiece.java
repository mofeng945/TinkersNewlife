package com.mofengbaizhi.tinkersnewlife.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** Native chunk-clipped template piece. Placement policy is intentionally external. */
public final class SleepwakeChurchPiece extends TemplateStructurePiece {
    public static final ResourceLocation TEMPLATE = new ResourceLocation("tinkersnewlife", "sleepwake_church");
    public static final int WIDTH = 51, HEIGHT = 82, DEPTH = 73;
    private final boolean lowFoundation;
    private static StructurePlaceSettings settings() {
        return new StructurePlaceSettings().setIgnoreEntities(true).setKnownShape(false)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
    }
    public SleepwakeChurchPiece(StructurePieceType type, StructureTemplateManager manager, BlockPos origin) {
        super(type, 0, manager, TEMPLATE, TEMPLATE.toString(), settings(), origin);
        lowFoundation = true;
    }
    public SleepwakeChurchPiece(StructurePieceType type, StructurePieceSerializationContext context, CompoundTag tag) {
        super(type, tag, context.structureTemplateManager(), id -> settings());
        lowFoundation = tag.getBoolean("LowFoundation");
    }

    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putBoolean("LowFoundation", lowFoundation);
    }
    @Override protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level,
                                               RandomSource random, BoundingBox bounds) { }
    @Override public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                                      RandomSource random, BoundingBox chunkBounds, ChunkPos chunk, BlockPos pivot) {
        // Fill only the current chunk's footprint. Never query or write an adjacent chunk here.
        int minX = Math.max(templatePosition.getX(), chunkBounds.minX());
        int maxX = Math.min(templatePosition.getX() + WIDTH - 1, chunkBounds.maxX());
        int minZ = Math.max(templatePosition.getZ(), chunkBounds.minZ());
        int maxZ = Math.min(templatePosition.getZ() + DEPTH - 1, chunkBounds.maxZ());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
            if (lowFoundation) {
                int floor = templatePosition.getY();
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                boolean wet = !level.getFluidState(pos.set(x, ground, z)).isEmpty();
                for (int y = floor; y < Math.min(surface, floor + HEIGHT); y++) {
                    level.setBlock(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
                ground = Math.max(ground, floor - (wet ? SleepwakeChurchTerrain.MAX_WATER_FILL : SleepwakeChurchTerrain.MAX_FILL));
            }
            for (int y = ground; y < templatePosition.getY(); y++) {
                pos.set(x, y, z);
                var block = !lowFoundation || templatePosition.getY() - y <= SleepwakeChurchTerrain.STONE_COURSES
                        ? Blocks.STONE_BRICKS : level.getBlockState(new BlockPos(x, ground - 1, z)).is(Blocks.GRASS_BLOCK)
                        ? Blocks.DIRT : Blocks.STONE;
                level.setBlock(pos, block.defaultBlockState(), 2);
            }
        }
        super.postProcess(level, structures, generator, random, chunkBounds, chunk, pivot);
        BlockPos altar = templatePosition.offset(25, 5, 58);
        // Only the altar chunk creates the resident. The deterministic UUID also guards retries.
        if (chunkBounds.isInside(altar)) {
            java.util.UUID id = java.util.UUID.nameUUIDFromBytes(("sleepwake:" + templatePosition.asLong())
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (level.getLevel().getEntity(id) == null) {
                var merchant = com.mofengbaizhi.tinkersnewlife.content.ModEntities.MOMO_MERCHANT.get().create(level.getLevel());
                if (merchant != null) {
                    merchant.setUUID(id);
                    merchant.moveTo(altar.getX()+0.5, altar.getY(), altar.getZ()+0.5, 180, 0);
                    merchant.setChurchResident();
                    level.addFreshEntity(merchant);
                }
            }
        }
    }
}
