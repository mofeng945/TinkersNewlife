package com.mofengbaizhi.tinkersnewlife.content.recipe;

import com.mofengbaizhi.tinkersnewlife.content.ModRecipeSerializers;
import com.mofengbaizhi.tinkersnewlife.content.curse.DomainRegistry;
import com.mofengbaizhi.tinkersnewlife.content.curse.TechniqueHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import slimeknights.mantle.data.loadable.common.IngredientLoadable;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.primitive.StringLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.ICustomOutputRecipe;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.recipe.TinkerRecipeTypes;
import slimeknights.tconstruct.library.recipe.modifiers.ModifierRecipeLookup;
import slimeknights.tconstruct.library.recipe.modifiers.ModifierSalvage;
import slimeknights.tconstruct.library.tools.SlotType;

/**
 * 修饰符槽位返还配方（术式/领域专用，按注册表一步到位）
 * <p>
 * 通过 {@code kind}（"technique" / "domain"）指定类别，加载时为 {@link TechniqueHandler} /
 * {@link DomainRegistry} 中所有该类别的修饰符自动注册标准 {@link ModifierSalvage}
 * （洗掉/剥离时返还对应槽位）。新增术式/领域只需注册进对应 handler，无需再写单个配方。
 */
public class TagModifierSalvage implements ICustomOutputRecipe<Container> {

    private static final LoadableField<Ingredient, TagModifierSalvage> TOOL_FIELD =
            IngredientLoadable.DISALLOW_EMPTY.requiredField("tools", r -> r.toolIngredient);
    private static final LoadableField<Integer, TagModifierSalvage> MAX_TOOL_SIZE_FIELD =
            IntLoadable.FROM_ONE.defaultField("max_tool_size", Integer.MAX_VALUE, r -> r.maxToolSize);
    private static final LoadableField<String, TagModifierSalvage> KIND_FIELD =
            StringLoadable.DEFAULT.requiredField("kind", r -> r.kind);
    private static final LoadableField<slimeknights.tconstruct.library.json.IntRange, TagModifierSalvage> LEVEL_FIELD =
            ModifierEntry.VALID_LEVEL.defaultField("level", r -> r.level);
    private static final LoadableField<SlotType.SlotCount, TagModifierSalvage> SLOTS_FIELD =
            SlotType.SlotCount.LOADABLE.nullableField("slots", r -> r.slots);

    public static final RecordLoadable<TagModifierSalvage> LOADER = RecordLoadable.create(
            ContextKey.ID.requiredField(),
            TOOL_FIELD,
            MAX_TOOL_SIZE_FIELD,
            KIND_FIELD,
            LEVEL_FIELD,
            SLOTS_FIELD,
            TagModifierSalvage::new
    );

    private final ResourceLocation id;
    private final Ingredient toolIngredient;
    private final int maxToolSize;
    private final String kind;
    private final slimeknights.tconstruct.library.json.IntRange level;
    private final SlotType.SlotCount slots;

    public TagModifierSalvage(ResourceLocation id, Ingredient toolIngredient, int maxToolSize, String kind,
                              slimeknights.tconstruct.library.json.IntRange level, SlotType.SlotCount slots) {
        this.id = id;
        this.toolIngredient = toolIngredient;
        this.maxToolSize = maxToolSize;
        this.kind = kind;
        this.level = level;
        this.slots = slots;
        // 加载时：为对应类别注册表中所有修饰符注册标准 salvage（返还槽位）
        java.util.Set<ModifierId> modifiers = "domain".equals(kind)
                ? DomainRegistry.getAllDomainIds()
                : TechniqueHandler.getAllTechniqueIds();
        for (ModifierId modifier : modifiers) {
            ModifierRecipeLookup.addSalvage(new ModifierSalvage(
                    new ResourceLocation(id.getNamespace(), id.getPath() + "/" + modifier.getPath()),
                    toolIngredient, maxToolSize, modifier, level, slots));
        }
    }

    @Override
    public boolean matches(Container container, Level level) {
        return false; // 纯注册用途，不参与容器匹配
    }

    @Override
    public net.minecraft.world.item.crafting.RecipeType<?> getType() {
        return TinkerRecipeTypes.DATA.get();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.TAG_MODIFIER_SALVAGE.get();
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }
}
