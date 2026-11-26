package com.mofengbaizhi.tinkersnewlife.content.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.ingredient.SizedIngredient;
import slimeknights.tconstruct.library.json.IntRange;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.recipe.modifiers.adding.ModifierRecipe;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationContainer;
import slimeknights.tconstruct.library.tools.SlotType.SlotCount;

import javax.annotation.Nullable;
import java.util.List;

/**
 * TNL1322 <b>带「当前等级区间」限制的匠魂改造配方</b>（用户反馈：「在工匠砧上按配方摆放对应物品时，
 * 优先生效的是一级配方，导致后续配方无法使用」）。
 *
 * <h2>为什么需要它</h2>
 * 匠魂的 {@link ModifierRecipe#matches} <b>只看材料种类、不看工具现有等级</b> ✓，
 * 而「矿工视野」有两张配方（1 级 20 个 / 升级 2~5 级 30 个，材料种类完全相同）。
 * 工匠砧挑配方时只取**第一个 matches 的**，升级时的等级校验在 {@code getValidatedResult} 里才失败 ✗
 * ⇒ 一级配方把升级配方挡死（而配方顺序还依赖 map 迭代顺序，本身也不稳定 ✗）。
 *
 * <h2>本类做了什么</h2>
 * 在 {@link ModifierRecipe} 的基础上加两个 JSON 字段：
 * <ul>
 *   <li>{@code min_current_level}（默认 0）—— 工具上该特性的**当前等级**至少要这么多；</li>
 *   <li>{@code max_current_level}（默认 999）—— 当前等级至多这么多。</li>
 * </ul>
 * {@code matches} 里除了原生材料判定，再卡一道当前等级 ⇒ 两张配方**互斥**，谁先被选中都不会抢 ✓
 * （水晶（modifier crystal）路径同样会走这个检查 ✓）。
 *
 * <p>⚠ 与匠魂 {@code multilevel_modifier} 的区别：那种是"每级槽位不同但材料相同"；
 * 我们是"每级材料数量不同"，所以需要这两张互斥配方 ✓。
 */
public class LeveledModifierRecipe extends ModifierRecipe {

    /** 当前等级下限（JSON: {@code min_current_level}） */
    private final int minCurrentLevel;
    /** 当前等级上限（JSON: {@code max_current_level}） */
    private final int maxCurrentLevel;

    public static final RecordLoadable<LeveledModifierRecipe> LOADER = RecordLoadable.create(
            ContextKey.ID.requiredField(),
            INPUTS_FIELD,
            TOOLS_FIELD,
            MAX_TOOL_SIZE_FIELD,
            RESULT_FIELD,
            LEVEL_FIELD,
            SLOTS_FIELD,
            ALLOW_CRYSTAL_FIELD,
            CHECK_TRAIT_LEVEL_FIELD,
            IntLoadable.FROM_ZERO.defaultField("min_current_level", 0, r -> r.minCurrentLevel),
            IntLoadable.FROM_ZERO.defaultField("max_current_level", 999, r -> r.maxCurrentLevel),
            LeveledModifierRecipe::new);

    public LeveledModifierRecipe(ResourceLocation id, List<SizedIngredient> inputs, Ingredient toolRequirement,
                                 int maxToolSize, ModifierId result, IntRange level, @Nullable SlotCount slots,
                                 boolean allowCrystal, boolean checkTraitLevel,
                                 int minCurrentLevel, int maxCurrentLevel) {
        super(id, inputs, toolRequirement, maxToolSize, result, level, slots, allowCrystal, checkTraitLevel);
        this.minCurrentLevel = minCurrentLevel;
        this.maxCurrentLevel = maxCurrentLevel;
    }

    @Override
    public boolean matches(ITinkerStationContainer inv, Level world) {
        if (!super.matches(inv, world)) {
            return false;
        }
        int current = inv.getTinkerable().getUpgrades().getLevel(this.result.getId());
        return current >= this.minCurrentLevel && current <= this.maxCurrentLevel;
    }
}
