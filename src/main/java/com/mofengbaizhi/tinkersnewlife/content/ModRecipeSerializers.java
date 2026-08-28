package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.recipe.BottleReturnModifierRecipe;
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

    /** 咒力总量升级配方（返还空玻璃瓶） */
    public static final RegistryObject<RecipeSerializer<BottleReturnModifierRecipe>> CURSE_TOTAL_UPGRADE =
            RECIPE_SERIALIZERS.register("curse_total_upgrade",
                    () -> LoadableRecipeSerializer.of(BottleReturnModifierRecipe.LOADER));
}
