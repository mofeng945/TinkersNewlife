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
        int x = context.chunkPos().getMinBlockX();
        int z = context.chunkPos().getMinBlockZ();
        if (!whiteSpace && !SleepwakeVillageProbe.nearVillage(context, x, z)) return Optional.empty();
        return SleepwakeChurchTerrain.highestOrigin(context, x, z).map(origin ->
                new GenerationStub(origin, builder -> builder.addPiece(new SleepwakeChurchPiece(
                        SleepwakeChurchRegistry.PIECE.get(), context.structureTemplateManager(), origin))));
    }
    @Override public StructureType<?> type() { return SleepwakeChurchRegistry.TYPE.get(); }
}
