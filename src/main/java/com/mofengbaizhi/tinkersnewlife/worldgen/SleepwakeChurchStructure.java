package com.mofengbaizhi.tinkersnewlife.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import java.util.Optional;

/** Native registry-backed structure: starts/pieces are serialized and locatable. */
public final class SleepwakeChurchStructure extends Structure {
    public static final Codec<SleepwakeChurchStructure> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(instance ->
            instance.group(settingsCodec(instance), Codec.BOOL.optionalFieldOf("white_space", false)
                    .forGetter(s -> s.whiteSpace)).apply(instance, SleepwakeChurchStructure::new));
    private final boolean whiteSpace;
    public SleepwakeChurchStructure(StructureSettings settings, boolean whiteSpace) { super(settings); this.whiteSpace = whiteSpace; }
    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        // GenerationContext carries no dimension key. Match the actual server generator
        // by identity (read only); never load a chunk or query structure starts here.
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return Optional.empty();
        boolean allowed = false;
        for (var level : server.getAllLevels()) {
            if (level.getChunkSource().getGenerator() != context.chunkGenerator()) continue;
            var expected = whiteSpace ? new net.minecraft.resources.ResourceLocation("tinkersnewlife", "great_white_space")
                    : net.minecraft.world.level.Level.OVERWORLD.location();
            if (level.dimension().location().equals(expected)) allowed = true;
        }
        if (!allowed) return Optional.empty();
        int x = context.chunkPos().getMinBlockX();
        int z = context.chunkPos().getMinBlockZ();
        if (!whiteSpace && !SleepwakeVillageProbe.nearVillage(context, x, z)) return Optional.empty();
        return SleepwakeChurchTerrain.highestOrigin(context, x, z).map(origin ->
                new GenerationStub(origin, builder -> builder.addPiece(new SleepwakeChurchPiece(
                        SleepwakeChurchRegistry.PIECE.get(), context.structureTemplateManager(), origin))));
    }
    @Override public StructureType<?> type() { return SleepwakeChurchRegistry.TYPE.get(); }
}
