package com.mofengbaizhi.tinkersnewlife.content.recipe;

// 移植自 TiCEX (MIT): moffy.ticex.lib.recipe.EmbossmentModifierRecipe

import com.mofengbaizhi.tinkersnewlife.content.ModRecipeSerializers;
import com.mofengbaizhi.tinkersnewlife.integration.slashblade.hook.EmbossmentModifierHook.EmbossmentContext;
import com.mofengbaizhi.tinkersnewlife.integration.slashblade.hook.KatanaModifierHooks;
import com.mofengbaizhi.tinkersnewlife.util.EmbossmentHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.ingredient.SizedIngredient;
import slimeknights.tconstruct.library.json.IntRange;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.recipe.RecipeResult;
import slimeknights.tconstruct.library.recipe.modifiers.adding.AbstractModifierRecipe;
import slimeknights.tconstruct.library.recipe.modifiers.adding.ModifierRecipe;
import slimeknights.tconstruct.library.recipe.tinkerstation.IMutableTinkerStationContainer;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationContainer;
import slimeknights.tconstruct.library.tools.SlotType.SlotCount;
import slimeknights.tconstruct.library.tools.nbt.LazyToolStack;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * <b>"普通输入 ＋ 装裱输入"修饰符配方</b> —— 逐字移植 TiCEX {@code EmbossmentModifierRecipe}（MIT）。
 *
 * <p>比 {@link SingleEmbossmentModifierRecipe} 多一组"普通材料" ✓：
 * <ul>
 *   <li>{@code inputs} ✓：普通材料（照匠魂 {@code ModifierRecipe} 那样匹配 ✓，会被消耗 ✓）；</li>
 *   <li>{@code emboss_inputs} ✓：装裱输入（匹配到的槽会触发该修饰符的装裱钩子 ✓，同样被消耗 ✓）。</li>
 * </ul>
 *
 * <p>本仓的「魂魄（konpaku）」用的就是它 ✓（{@code inputs = konpaku_core} ＋
 * {@code emboss_inputs = 附魔书} ✓ 与 TiCEX 一致 ✓）。
 */
public class EmbossmentModifierRecipe extends AbstractModifierRecipe {

    public static final RecordLoadable<EmbossmentModifierRecipe> LOADER = RecordLoadable.create(
            ContextKey.ID.requiredField(),
            SizedIngredient.LOADABLE.list(1).requiredField("inputs", r -> r.inputs),
            SizedIngredient.LOADABLE.list(1).requiredField("emboss_inputs", r -> r.embossItem),
            TOOLS_FIELD,
            MAX_TOOL_SIZE_FIELD,
            RESULT_FIELD,
            LEVEL_FIELD,
            SLOTS_FIELD,
            EmbossmentModifierRecipe::new
    );

    private final List<SizedIngredient> embossItem;
    private final List<SizedIngredient> inputs;

    private List<SizedIngredient> ingredientsCache;

    public EmbossmentModifierRecipe(
            ResourceLocation id,
            List<SizedIngredient> inputs,
            List<SizedIngredient> embossItem,
            Ingredient toolRequirement,
            int maxToolSize,
            ModifierId result,
            IntRange level,
            @Nullable SlotCount slots
    ) {
        super(id, toolRequirement, maxToolSize, result, level, slots, false, false);
        this.inputs = inputs;
        this.embossItem = embossItem;
    }

    @Override
    public boolean matches(ITinkerStationContainer container, Level level) {
        if (!result.isBound() || !this.toolRequirement.test(container.getTinkerableStack())) {
            return false;
        }

        if (ingredientsCache == null) {
            ingredientsCache = new ArrayList<>();
        } else {
            ingredientsCache.clear();
        }

        ingredientsCache.addAll(inputs);
        ingredientsCache.addAll(embossItem);
        return ModifierRecipe.checkMatch(container, ingredientsCache);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.EMBOSSMENT_MODIFIER.get();
    }

    @Override
    public void updateInputs(LazyToolStack arg0, IMutableTinkerStationContainer arg1, boolean arg2) {
        ModifierRecipe.updateInputs(arg1, inputs);
        ModifierRecipe.updateInputs(arg1, embossItem);
    }

    @Override
    public List<ItemStack> getDisplayItems(int slot) {
        if (slot >= 0 && slot < inputs.size() + embossItem.size()) {
            if (slot < inputs.size()) {
                return inputs.get(slot).getMatchingStacks();
            } else {
                return embossItem.get(slot - inputs.size()).getMatchingStacks();
            }
        }
        return Collections.emptyList();
    }

    @Override
    public int getInputCount() {
        return inputs.size() + embossItem.size();
    }

    @Override
    public RecipeResult<LazyToolStack> getValidatedResult(ITinkerStationContainer inv, RegistryAccess access) {
        ToolStack tool = inv.getTinkerable();
        Component commonError = this.validatePrerequisites(tool);
        if (commonError != null) {
            return RecipeResult.failure(commonError);
        } else {
            ModifierId modifier = result.getId();

            tool = tool.copy();

            Component error = tool.tryValidate();
            if (error != null) {
                return RecipeResult.failure(error);
            }

            if (tool.getModifierLevel(modifier) == 0) {
                SlotCount slots = getSlots();
                if (slots != null) {
                    tool.getPersistentData().addSlots(slots.type(), -slots.count());
                }
            } else {
                tool.removeModifier(modifier, 1);
            }

            tool.addModifier(modifier, 1);
            boolean result = false;
            ItemStack resultStack = tool.createStack();

            EmbossmentContext context = new EmbossmentContext(resultStack, inv);

            boolean secondary = false;
            for (int i = 0; i < inv.getInputCount(); i++) {
                ItemStack input = inv.getInput(i);
                if (embossItem.get(0).test(input)) {
                    result = tool
                            .getModifier(modifier)
                            .getHook(KatanaModifierHooks.EMBOSSMENT)
                            .applyItem(context, i, secondary);
                }
                secondary = true;
            }

            if (result) {
                return LazyToolStack.success(
                        EmbossmentHelper.applyCatalystEmbossment(context.getToolStack(), inv, false));
            }
            return RecipeResult.failure(context.getErrorMsg());
        }
    }
}
