package com.mofengbaizhi.tinkersnewlife.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

public final class SleepwakeChurchRegistry {
    public static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, "tinkersnewlife");
    public static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, "tinkersnewlife");
    public static final RegistryObject<StructureType<SleepwakeChurchStructure>> TYPE = TYPES.register("sleepwake_church", () -> () -> SleepwakeChurchStructure.CODEC);
    public static final RegistryObject<StructurePieceType> PIECE = PIECES.register("sleepwake_church", () ->
            (context, tag) -> new SleepwakeChurchPiece(SleepwakeChurchRegistry.PIECE.get(), context, tag));
    public static void register(IEventBus bus) { TYPES.register(bus); PIECES.register(bus); }
    private SleepwakeChurchRegistry() {}
}
