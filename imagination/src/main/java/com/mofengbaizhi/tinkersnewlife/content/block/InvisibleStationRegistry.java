package com.mofengbaizhi.tinkersnewlife.content.block;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** §1280 透明工匠砧的方块与方块实体类型注册。 */
public final class InvisibleStationRegistry {

    public static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, TinkersNewlife.MOD_ID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, TinkersNewlife.MOD_ID);

    public static final RegistryObject<InvisibleStationBlock> INVISIBLE_STATION = BLOCKS.register(
            "invisible_tinker_station",
            () -> new InvisibleStationBlock(BlockBehaviour.Properties.copy(Blocks.CRAFTING_TABLE)
                    .noOcclusion().noCollission().strength(0.5F)));

    public static final RegistryObject<BlockEntityType<InvisibleStationBlockEntity>> INVISIBLE_STATION_BE =
            BLOCK_ENTITIES.register("invisible_tinker_station",
                    () -> BlockEntityType.Builder.of(InvisibleStationBlockEntity::new,
                            INVISIBLE_STATION.get()).build(null));

    private InvisibleStationRegistry() {
    }
}