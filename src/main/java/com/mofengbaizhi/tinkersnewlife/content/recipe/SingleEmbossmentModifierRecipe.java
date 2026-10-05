package com.mofengbaizhi.tinkersnewlife.content.recipe;

// 移植自 TiCEX (MIT): moffy.ticex.lib.recipe.SingleEmbossmentModifierRecipe

import com.google.common.collect.ImmutableList;
import com.mofengbaizhi.tinkersnewlife.content.ModRecipeSerializers;
import com.mofengbaizhi.tinkersnewlife.integration.slashblade.hook.EmbossmentModifierHook.EmbossmentContext;
import com.mofengbaizhi.tinkersnewlife.integration.slashblade.hook.KatanaModifierHooks;
import com.mofengbaizhi.tinkersnewlife.util.EmbossmentHelper;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import slimeknights.mantle.data.loadable.common.IngredientLoadable;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.tconstruct.library.json.IntRange;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.recipe.RecipeResult;
import slimeknights.tconstruct.library.recipe.modifiers.adding.AbstractModifierRecipe;
import slimeknights.tconstruct.library.recipe.modifiers.adding.IncrementalModifierRecipe;
import slimeknights.tconstruct.library.recipe.tinkerstation.IMutableTinkerStationContainer;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationContainer;
import slimeknights.tconstruct.library.tools.SlotType.SlotCount;
import slimeknights.tconstruct.library.tools.nbt.LazyToolStack;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * <b>"单输入装裱"修饰符配方</b> —— 逐字移植 TiCEX {@code SingleEmbossmentModifierRecipe}（MIT）。
 *
 * <p>与匠魂自带 {@code tconstruct:modifier} 配方的差别只有两点 ✓：
 * <ol>
 *   <li>配方字段是 {@code emboss_input}（单个 {@code Ingredient} ✓）而不是 {@code inputs} 列表 ✓；</li>
 *   <li>{@link #getValidatedResult} 里，把修饰符 {@code addModifier} 上去之后，
 *       <b>对每一个匹配到 {@code emboss_input} 的输入槽调用该修饰符的装裱钩子</b> ✓
 *       （{@code tool.getModifier(modifier).getHook(KatanaModifierHooks.EMBOSSMENT).applyItem(context, i, secondary)} ✓）
 *       ⇒ 这就是"装上特性时特性真的被执行"的那一步 ✓。</li>
 * </ol>
 *
 * <p>⚠ 它只能在<b>修补台/工匠砧</b>里工作 ✓（继承匠魂自己的 {@code AbstractModifierRecipe} ✓
 * ⇒ {@code getType()} 就是匠魂的 {@code tinker_station} ✓）。
 * <p>⚠ 它把输入槽<b>全部</b>按整组数量消耗 ✓（照 TiCEX 的 {@link #updateInputs} ✓）。
 */
public class SingleEmbossmentModifierRecipe extends AbstractModifierRecipe {

    public static final RecordLoadable<SingleEmbossmentModifierRecipe> LOADER = RecordLoadable.create(
            ContextKey.ID.requiredField(),
            IngredientLoadable.DISALLOW_EMPTY.requiredField("emboss_input", r -> r.input),
            TOOLS_FIELD,
            MAX_TOOL_SIZE_FIELD,
            RESULT_FIELD,
            LEVEL_FIELD,
            SLOTS_FIELD,
            SingleEmbossmentModifierRecipe::new
    );

    private final Ingredient input;

    private List<List<ItemStack>> slotCache;

    public SingleEmbossmentModifierRecipe(
            ResourceLocation id,
            Ingredient input,
            Ingredient toolRequirement,
            int maxToolSize,
            ModifierId result,
            IntRange level,
            SlotCount slots
    ) {
        super(id, toolRequirement, maxToolSize, result, level, slots, false, false);
        this.input = input;
    }

    @Override
    public boolean matches(ITinkerStationContainer inv, Level level) {
        if (!result.isBound() || !this.toolRequirement.test(inv.getTinkerableStack())) {
            return false;
        }
        return IncrementalModifierRecipe.containsOnlyIngredient(inv, input);
    }

    @Override
    public RecipeResult<LazyToolStack> getValidatedResult(ITinkerStationContainer inv, RegistryAccess access) {
        ToolStack tool = inv.getTinkerable().copy();

        ModifierId modifier = result.getId();

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
            ItemStack inputStack = inv.getInput(i);
            if (input.test(inputStack)) {
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

    @Override
    public void updateInputs(LazyToolStack result, IMutableTinkerStationContainer inv, boolean isServer) {
        for (int index = 0; index < inv.getInputCount(); ++index) {
            inv.shrinkInput(index, inv.getInput(index).getCount());
        }
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.SINGLE_MODIFIER_EMBOSSMENT.get();
    }

    @Override
    public List<ItemStack> getDisplayItems(int slot) {
        List<List<ItemStack>> inputs = getInputs();
        if (slot >= 0 && slot < inputs.size()) {
            return inputs.get(slot);
        }
        return Collections.emptyList();
    }

    @Override
    public int getInputCount() {
        return getInputs().size();
    }

    private List<List<ItemStack>> getInputs() {
        if (slotCache == null) {
            ImmutableList.Builder<List<ItemStack>> builder = ImmutableList.builder();

            // fill extra item slots
            List<ItemStack> items = Arrays.asList(input.getItems());

            builder.add(items);
            slotCache = builder.build();
        }
        return slotCache;
    }
}
