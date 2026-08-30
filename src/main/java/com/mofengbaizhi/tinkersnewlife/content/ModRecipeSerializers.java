package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.recipe.AutoMaterialMeltingRecipe;
import com.mofengbaizhi.tinkersnewlife.content.recipe.CrystalModifierRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import slimeknights.mantle.recipe.helper.LoadableRecipeSerializer;

/**
 * 自定义配方序列化器注册
 */
public class ModRecipeSerializers {

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, TinkersNewlife.MOD_ID);

    /** 仅水晶添加的修饰符配方（术式/领域专用，见 CrystalModifierRecipe） */
    public static final RegistryObject<RecipeSerializer<CrystalModifierRecipe>> CRYSTAL_MODIFIER =
            RECIPE_SERIALIZERS.register("crystal_modifier",
                    () -> LoadableRecipeSerializer.of(CrystalModifierRecipe.LOADER));

    /** 万能材料熔化配方（一条配方自动覆盖所有流体材料，见 AutoMaterialMeltingRecipe） */
    public static final RegistryObject<RecipeSerializer<AutoMaterialMeltingRecipe>> AUTO_MATERIAL_MELTING =
            RECIPE_SERIALIZERS.register("auto_material_melting",
                    () -> LoadableRecipeSerializer.of(AutoMaterialMeltingRecipe.LOADER));
}
