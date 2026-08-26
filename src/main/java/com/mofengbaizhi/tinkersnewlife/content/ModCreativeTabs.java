package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.DragonStaffItem;
import com.mofengbaizhi.tinkersnewlife.content.item.FlyingSwordItem;
import com.mofengbaizhi.tinkersnewlife.content.item.ModularStaffItem;
import com.mofengbaizhi.tinkersnewlife.content.item.SilentGloveItem;
import com.mofengbaizhi.tinkersnewlife.content.item.WarScytheItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

import java.util.Collection;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TinkersNewlife.MOD_ID);

    private static final ToolDefinition DRAGON_STAFF_DEFINITION = DragonStaffItem.DRAGON_STAFF_DEFINITION;
    private static final ToolDefinition SILENT_GLOVE_DEFINITION = SilentGloveItem.SILENT_GLOVE_DEFINITION;

    public static final RegistryObject<CreativeModeTab> TINKERS_NEW_LIFE_TAB =
            CREATIVE_MODE_TABS.register("tinkersnewlife",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.tinkersnewlife"))
                            .icon(() -> new ItemStack(ModItems.GHELOTH_REMAINS.get()))
                            .displayItems((parameters, output) -> {
                                // ----- 基础材料 -----
                                output.accept(ModItems.GHELOTH_REMAINS.get());
                                output.accept(ModItems.NICHOLAS_BLESSING.get());
                                output.accept(ModItems.YELLOW_KING_REMNANT.get());
                                output.accept(ModItems.RLYEH_CALL.get());
                                output.accept(ModItems.ECHO_OF_THE_VOID.get());
                                output.accept(ModItems.ASTRAL_ANCHOR.get());
                                output.accept(ModItems.NEXUS_OF_SPACETIME.get());
                                output.accept(ModItems.YOG_SOTHOTH_GATE_KEY.get());
                                output.accept(ModItems.NYARLATHOTEP_DESIRE.get());
                                output.accept(ModItems.DURANDAL_SHARD.get());

                                // ----- 矿石 -----
                                output.accept(ModItems.GHELOTH_ORE.get());

                                // ----- 所有流体桶 -----
                                output.accept(ModFluids.GHELOTH_BLOOD.bucket.get());
                                output.accept(ModFluids.MOLTEN_NICHOLAS_BLESSING.bucket.get());
                                output.accept(ModFluids.HASTUR_MALICE.bucket.get());
                                output.accept(ModFluids.ASHEN_INK.bucket.get());
                                output.accept(ModFluids.MOLTEN_DRAGONSTEEL_FIRE.bucket.get());
                                output.accept(ModFluids.MOLTEN_DRAGONSTEEL_ICE.bucket.get());
                                output.accept(ModFluids.MOLTEN_DRAGONSTEEL_LIGHTNING.bucket.get());
                                output.accept(ModFluids.FIRE_BLOOD.bucket.get());
                                output.accept(ModFluids.ICE_BLOOD.bucket.get());
                                output.accept(ModFluids.LIGHTNING_BLOOD.bucket.get());
                                output.accept(ModFluids.MOLTEN_DREAD.bucket.get());
                                output.accept(ModFluids.MOLTEN_DREADSTEEL.bucket.get());
                                output.accept(ModFluids.MOLTEN_DURANDAL.bucket.get());

                                // ----- 铸模 -----
                                output.accept(ModItems.DRAGON_CORE_CAST.get());
                                output.accept(ModItems.DRAGON_CORE_SAND_CAST.get());
                                output.accept(ModItems.DRAGON_CORE_RED_SAND_CAST.get());
                                output.accept(ModItems.SPELL_CORE_CAST.get());
                                output.accept(ModItems.SPELL_CORE_SAND_CAST.get());
                                output.accept(ModItems.SPELL_CORE_RED_SAND_CAST.get());
                                addAllPartVariants(output, ModItems.DRAGON_CORE.get());
                                addAllPartVariants(output, ModItems.SPELL_CORE.get());

                                addAllToolVariants(output, DRAGON_STAFF_DEFINITION, ModItems.DRAGON_STAFF.get(), 3);

                                addAllToolVariants(output, SILENT_GLOVE_DEFINITION, ModItems.SILENT_GLOVE.get(), 2);

                                addAllToolVariants(output, WarScytheItem.WAR_SCYTHE_DEFINITION, ModItems.WAR_SCYTHE.get(), 5);

                                addAllToolVariants(output, ModularStaffItem.MODULAR_STAFF_DEFINITION, ModItems.MODULAR_STAFF.get(), 4);

                                addAllToolVariants(output, FlyingSwordItem.FLYING_SWORD_DEFINITION, ModItems.FLYING_SWORD.get(), 5);

                                output.accept(ModItems.DURANDAL_SWORD.get());
                            })
                            .build()
            );

    // ============================================================
    //  辅助方法：添加所有部件材质变体
    // ============================================================

    private static void addAllPartVariants(CreativeModeTab.Output output, ToolPartItem partItem) {
        Collection<IMaterial> materials = MaterialRegistry.getInstance().getAllMaterials();
        for (IMaterial material : materials) {
            MaterialId materialId = material.getIdentifier();
            if (!materialId.getNamespace().equals(TinkersNewlife.MOD_ID)) continue;

            ItemStack stack = new ItemStack(partItem);
            partItem.setMaterial(stack, materialId);

            if (!stack.isEmpty()) {
                output.accept(stack);
            }
        }
    }

    // ============================================================
    //  辅助方法：添加所有工具材质变体（手动传入部件数量）
    // ============================================================

    private static void addAllToolVariants(CreativeModeTab.Output output, ToolDefinition definition, Item toolItem, int partCount) {
        if (definition == null || toolItem == null) return;

        Collection<IMaterial> materials = MaterialRegistry.getInstance().getAllMaterials();

        for (IMaterial material : materials) {
            MaterialId materialId = material.getIdentifier();
            if (!materialId.getNamespace().equals(TinkersNewlife.MOD_ID)) continue;

            ItemStack stack = new ItemStack(toolItem);
            ToolStack tool = ToolStack.from(stack);
            if (tool == null) continue;

            // 根据传入的部件数量构建材质列表（⭐ 消除 2/3/4/5 魔法数字分支）
            MaterialVariant[] variants = new MaterialVariant[partCount];
            for (int i = 0; i < variants.length; i++) {
                variants[i] = MaterialVariant.of(material);
            }
            MaterialNBT materialNBT = MaterialNBT.of(variants);

            tool.setMaterials(materialNBT);
            tool.rebuildStats();

            ItemStack result = tool.createStack();
            if (!result.isEmpty()) {
                output.accept(result);
            }
        }
    }
}