package com.mofengbaizhi.tinkersnewlife.content.recipe;

import com.mofengbaizhi.tinkersnewlife.content.ModRecipeSerializers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.ingredient.SizedIngredient;
import slimeknights.tconstruct.library.json.IntRange;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.recipe.modifiers.adding.ModifierRecipe;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationContainer;
import slimeknights.tconstruct.library.tools.SlotType;

import java.util.List;

/**
 * 仅水晶添加的修饰符配方（术式/领域专用）
 * <p>
 * 术式与领域只能通过仪式 roll 核心获得，无法用普通材料在工具站添加；
 * 但允许用 TConstruct 修饰符水晶（{@code tconstruct:modifier_crystal}，
 * 携带对应修饰符 NBT）给核心添加/转移术式与领域。
 * <p>
 * 与普通 {@link ModifierRecipe} 的区别：
 * <ul>
 *   <li>{@code inputs} 在 JSON 中省略（默认空列表），普通材料路径永不匹配
 *       （{@code ModifierRecipe.checkMatch} 对空输入恒返回 false）</li>
 *   <li>{@link #matches} 显式只走水晶路径（{@code matchesCrystal}）</li>
 *   <li>槽位在构造时注册进 {@code ModifierRecipeLookup}，
 *       因此剥离/移除术式或领域时能正确返还术式槽/领域槽</li>
 * </ul>
 */
public class CrystalModifierRecipe extends ModifierRecipe {

    /** 输入字段：JSON 省略时默认空列表（普通材料路径永不可用） */
    private static final LoadableField<List<SizedIngredient>, CrystalModifierRecipe> INPUTS_FIELD_OPTIONAL =
            SizedIngredient.LOADABLE.list(1).defaultField("inputs", List.of(), r -> r.inputs);

    public static final RecordLoadable<CrystalModifierRecipe> LOADER = RecordLoadable.create(
            ContextKey.ID.requiredField(),
            INPUTS_FIELD_OPTIONAL,
            TOOLS_FIELD,
            MAX_TOOL_SIZE_FIELD,
            RESULT_FIELD,
            LEVEL_FIELD,
            SLOTS_FIELD,
            ALLOW_CRYSTAL_FIELD,
            CHECK_TRAIT_LEVEL_FIELD,
            CrystalModifierRecipe::new
    );

    public CrystalModifierRecipe(ResourceLocation id, List<SizedIngredient> inputs, Ingredient toolRequirement,
                                 int maxToolSize, ModifierId result, IntRange level, SlotType.SlotCount slots,
                                 boolean allowCrystal, boolean checkTraitLevel) {
        super(id, inputs, toolRequirement, maxToolSize, result, level, slots, allowCrystal, checkTraitLevel);
    }

    /** 仅水晶匹配：工具符合要求 + 输入槽内有携带本修饰符的水晶 */
    @Override
    public boolean matches(ITinkerStationContainer container, Level level) {
        return result.isBound()
                && toolRequirement.test(container.getTinkerableStack())
                && matchesCrystal(container);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.CRYSTAL_MODIFIER.get();
    }
}
