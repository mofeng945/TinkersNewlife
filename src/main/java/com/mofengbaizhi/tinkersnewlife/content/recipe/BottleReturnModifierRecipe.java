package com.mofengbaizhi.tinkersnewlife.content.recipe;

import com.mofengbaizhi.tinkersnewlife.content.ModRecipeSerializers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.ingredient.SizedIngredient;
import slimeknights.tconstruct.library.json.IntRange;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.recipe.modifiers.adding.ModifierRecipe;
import slimeknights.tconstruct.library.recipe.tinkerstation.IMutableTinkerStationContainer;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.library.tools.nbt.LazyToolStack;

import java.util.List;

/**
 * 返还空瓶的修改器升级配方
 * <p>
 * 与 {@code tconstruct:modifier} 配方 JSON 结构完全一致，仅在合成时额外向玩家返还
 * 指定数量的空玻璃瓶（用于"两瓶龙息（返还玻璃瓶）"这类消耗瓶装液体的配方）。
 */
public class BottleReturnModifierRecipe extends ModifierRecipe {

    public static final RecordLoadable<BottleReturnModifierRecipe> LOADER = RecordLoadable.create(
            ContextKey.ID.requiredField(),
            INPUTS_FIELD,
            TOOLS_FIELD,
            MAX_TOOL_SIZE_FIELD,
            RESULT_FIELD,
            LEVEL_FIELD,
            SLOTS_FIELD,
            ALLOW_CRYSTAL_FIELD,
            CHECK_TRAIT_LEVEL_FIELD,
            BottleReturnModifierRecipe::new
    );

    /** 返还的空玻璃瓶数量（龙息瓶消耗后返回空瓶） */
    private final int returnCount;

    public BottleReturnModifierRecipe(ResourceLocation id, List<SizedIngredient> inputs,
                                      Ingredient toolRequirement, int maxToolSize,
                                      ModifierId result, IntRange level, SlotType.SlotCount slots,
                                      boolean allowCrystal, boolean checkTraitLevel) {
        super(id, inputs, toolRequirement, maxToolSize, result, level, slots, allowCrystal, checkTraitLevel);
        this.returnCount = 2;
    }

    @Override
    public void updateInputs(LazyToolStack result, IMutableTinkerStationContainer inv, boolean isServer) {
        super.updateInputs(result, inv, isServer);
        // ⭐ 返还空玻璃瓶（两瓶龙息的空瓶）
        inv.giveItem(new ItemStack(Items.GLASS_BOTTLE, returnCount));
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.CURSE_TOTAL_UPGRADE.get();
    }
}
