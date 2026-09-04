package com.mofengbaizhi.tinkersnewlife.content.recipe;

import com.mofengbaizhi.tinkersnewlife.content.ModRecipeSerializers;
import com.mofengbaizhi.tinkersnewlife.content.curse.domain.DomainRegistry;
import com.mofengbaizhi.tinkersnewlife.content.curse.TechniqueHandler;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.ingredient.SizedIngredient;
import slimeknights.tconstruct.library.json.IntRange;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.ModifierManager;
import slimeknights.tconstruct.library.recipe.RecipeResult;
import slimeknights.tconstruct.library.recipe.modifiers.ModifierRecipeLookup;
import slimeknights.tconstruct.library.recipe.modifiers.adding.ModifierRecipe;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationContainer;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.library.tools.nbt.LazyToolStack;
import slimeknights.tconstruct.library.tools.nbt.ToolDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.tools.TinkerModifiers;
import slimeknights.tconstruct.tools.item.ModifierCrystalItem;

import java.util.List;

/**
 * 仅水晶添加的修饰符配方（术式/领域专用，按注册表一步到位）
 * <p>
 * 通过 {@code kind}（"technique" / "domain"）指定类别，匹配携带该类别的任意修饰符水晶
 * 给咒力核心添加——新增术式/领域只需注册进 {@link TechniqueHandler} / {@link DomainRegistry}，
 * 无需再写单个配方。
 * <p>
 * 与普通 {@link ModifierRecipe} 的区别：
 * <ul>
 *   <li>{@code inputs} 在 JSON 中省略（默认空列表），普通材料路径永不匹配</li>
 *   <li>{@link #matches} 显式只走水晶路径（{@link #matchesCrystal}），且水晶修饰符需在对应类别注册表内</li>
 *   <li>{@link #getValidatedResult} 使用水晶携带的具体修饰符添加（而非固定 result）</li>
 * </ul>
 */
public class CrystalModifierRecipe extends ModifierRecipe {

    /** 输入字段：JSON 省略时默认空列表（普通材料路径永不可用） */
    private static final LoadableField<List<SizedIngredient>, CrystalModifierRecipe> INPUTS_FIELD_OPTIONAL =
            SizedIngredient.LOADABLE.list(1).defaultField("inputs", List.of(), r -> r.inputs);

    /** result 占位字段（JSON 省略，始终用占位 EMPTY；实际修饰符由水晶 NBT 决定） */
    private static final LoadableField<ModifierId, CrystalModifierRecipe> RESULT_FIELD_OPTIONAL =
            ModifierId.PARSER.defaultField("result", ModifierManager.EMPTY, r -> r.result.getId());

    /** 类别字段："technique" 或 "domain" */
    private static final LoadableField<String, CrystalModifierRecipe> KIND_FIELD =
            slimeknights.mantle.data.loadable.primitive.StringLoadable.DEFAULT.requiredField("kind", r -> r.kind);

    public static final RecordLoadable<CrystalModifierRecipe> LOADER = RecordLoadable.create(
            ContextKey.ID.requiredField(),
            INPUTS_FIELD_OPTIONAL,
            TOOLS_FIELD,
            MAX_TOOL_SIZE_FIELD,
            RESULT_FIELD_OPTIONAL,
            LEVEL_FIELD,
            SLOTS_FIELD,
            ALLOW_CRYSTAL_FIELD,
            CHECK_TRAIT_LEVEL_FIELD,
            KIND_FIELD,
            CrystalModifierRecipe::new
    );

    /** 类别："technique" / "domain" */
    private final String kind;
    /** 本配方槽位（AbstractModifierRecipe.slots 为 private，自行保存一份） */
    private final SlotType.SlotCount recipeSlots;

    public CrystalModifierRecipe(ResourceLocation id, List<SizedIngredient> inputs, Ingredient toolRequirement,
                                 int maxToolSize, ModifierId result, IntRange level, SlotType.SlotCount slots,
                                 boolean allowCrystal, boolean checkTraitLevel, String kind) {
        super(id, inputs, toolRequirement, maxToolSize, result, level, slots, allowCrystal, checkTraitLevel);
        this.kind = kind;
        this.recipeSlots = slots;
        // ⭐ 为类别内所有术式/领域修饰符注册（剥离 UI 的可剥离列表来自 ModifierRecipeLookup 的
        // 添加配方 result 注册；占位 result 无效，必须逐个注册真实 modifier）
        java.util.Set<ModifierId> ids = "domain".equals(kind)
                ? DomainRegistry.getAllDomainIds()
                : TechniqueHandler.getAllTechniqueIds();
        for (ModifierId modifier : ids) {
            ModifierRecipeLookup.addRecipeModifier(slots != null ? slots.type() : null,
                    new slimeknights.tconstruct.library.modifiers.util.LazyModifier(modifier));
        }
    }

    /** 水晶修饰符是否属于本配方类别（查注册表） */
    private boolean isKind(ModifierId modifier) {
        return "domain".equals(kind) ? DomainRegistry.isDomain(modifier) : TechniqueHandler.isTechnique(modifier);
    }

    /** 仅水晶匹配：工具符合要求 + 输入槽内有携带本类别修饰符的水晶 */
    @Override
    public boolean matches(ITinkerStationContainer container, Level level) {
        boolean m = toolRequirement.test(container.getTinkerableStack())
                && matchesCrystal(container);
        if (m) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[CrystalRecipe] {} 匹配成功 (kind={})", getId(), kind);
        }
        return m;
    }

    @Override
    protected boolean matchesCrystal(ITinkerStationContainer container) {
        if (!allowCrystal) return false;
        boolean found = false;
        for (int i = 0; i < container.getInputCount(); i++) {
            ItemStack stack = container.getInput(i);
            if (stack.isEmpty()) continue;
            // 只能有一组水晶
            if (found || !stack.is(TinkerModifiers.modifierCrystal.asItem())) {
                return false;
            }
            ModifierId modifier = ModifierCrystalItem.getModifier(stack);
            if (!isKind(modifier)) {
                return false;
            }
            found = true;
        }
        return found;
    }

    /** 使用水晶携带的具体修饰符添加（水晶 NBT 里的 modifier 已在 matches 中校验属于本类别） */
    @Override
    public RecipeResult<LazyToolStack> getValidatedResult(ITinkerStationContainer inv, RegistryAccess access) {
        // 从水晶读取具体修饰符
        ModifierId crystalMod = null;
        for (int i = 0; i < inv.getInputCount(); i++) {
            ItemStack stack = inv.getInput(i);
            if (!stack.isEmpty() && stack.is(TinkerModifiers.modifierCrystal.asItem())) {
                crystalMod = ModifierCrystalItem.getModifier(stack);
                break;
            }
        }
        if (crystalMod == null) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[CrystalRecipe] {} 输出空：未找到水晶输入", getId());
            return RecipeResult.failure(Component.translatable("tinkersnewlife.recipe.crystal_missing"));
        }
        ToolStack tool = inv.getTinkerable();
        Component commonError = validatePrerequisites(tool);
        if (commonError != null) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[CrystalRecipe] {} 输出空：前置校验失败 {}", getId(), commonError.getString());
            return RecipeResult.failure(commonError);
        }
        // 消耗槽位
        tool = tool.copy();
        ToolDataNBT persistentData = tool.getPersistentData();
        if (recipeSlots != null) {
            persistentData.addSlots(recipeSlots.type(), -recipeSlots.count());
        }
        // 添加水晶携带的修饰符
        tool.addModifier(crystalMod, 1);
        Component toolValidation = tool.tryValidate();
        if (toolValidation != null) {
            return RecipeResult.failure(toolValidation);
        }
        return success(tool, inv);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.CRYSTAL_MODIFIER.get();
    }
}
