package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.block.MoltenForgeBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 方块实体注册表 */
public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, TinkersNewlife.MOD_ID);

    /** 融锻炉：读取工具所有部件材料，多流体产物直接注入其内部容器 */
    public static final RegistryObject<BlockEntityType<MoltenForgeBlockEntity>> MOLTEN_FORGE =
            BLOCK_ENTITIES.register("melt_forge",
                    () -> BlockEntityType.Builder.of(
                            MoltenForgeBlockEntity::new,
                            ModBlocks.MOLTEN_FORGE.get()).build(null));
}
