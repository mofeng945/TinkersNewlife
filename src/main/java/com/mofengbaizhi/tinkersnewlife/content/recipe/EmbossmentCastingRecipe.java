package com.mofengbaizhi.tinkersnewlife.content.recipe;

// 移植自 TiCEX (MIT): moffy.ticex.lib.recipe.EmbossmentCastingRecipe

import com.mofengbaizhi.tinkersnewlife.content.ModRecipeSerializers;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.mantle.recipe.helper.LoadableRecipeSerializer;
import slimeknights.mantle.recipe.helper.TypeAwareRecipeSerializer;
import slimeknights.tconstruct.library.json.TinkerLoadables;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.recipe.casting.ICastingContainer;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialCastingRecipe;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;

/**
 * <b>"装裱浇铸"</b> —— 逐字移植 TiCEX {@code EmbossmentCastingRecipe}（MIT）。
 *
 * <p>它就是整个装裱体系的<b>源头</b> ✓：在浇铸台上把<b>一把真正的刀当铸模</b>用 ✓
 * （{@code cast} ＋ {@code cast_consumed: true} ✓ ⇒ 刀被消耗掉 ✓），
 * 倒进任意材料流体 → 铸出一个部件 ✓，并在 {@link #assemble} 里把**那把被牺牲的刀的完整存档**
 * 写进结果的 {@code embossed} 标签 ✓（{@code assembled.tag.embossed = cast.save()} ✓）。
 *
 * <p>⇒ 之后：
 * <ul>
 *   <li>{@code ModifierKoshirae}（拵）读 {@code input.embossed.tag.bladeState} ✓
 *       ⇒ 把这把刀的耀魂/击杀数/精炼<b>取较大值</b>并进自己的刀 ✓；</li>
 *   <li>{@code EmbossmentHelper.applyCatalystEmbossment} ✓ 把 {@code embossed.tag} 里结果上没有的 key 并进结果 ✓。</li>
 * </ul>
 *
 * <p>本仓的 {@code tinkersnewlife:catalyst_slashblade} 就由它产出 ✓
 * （见 {@code data/tinkersnewlife/recipes/catalyst_slashblade.json} ✓）。
 */
public class EmbossmentCastingRecipe extends MaterialCastingRecipe {

    protected static final LoadableField<IMaterialItem, EmbossmentCastingRecipe> RESULT_FIELD =
            TinkerLoadables.MATERIAL_ITEM.requiredField("result", r -> r.result);
    public static final RecordLoadable<EmbossmentCastingRecipe> LOADER = RecordLoadable.create(
            LoadableRecipeSerializer.TYPED_SERIALIZER.requiredField(),
            ContextKey.ID.requiredField(),
            LoadableRecipeSerializer.RECIPE_GROUP,
            CAST_FIELD,
            ITEM_COST_FIELD,
            RESULT_FIELD,
            MATERIALS_FIELD,
            CAST_CONSUMED_FIELD,
            SWITCH_SLOTS_FIELD,
            EmbossmentCastingRecipe::new
    );

    protected Ingredient castIngredient;

    public EmbossmentCastingRecipe(
            TypeAwareRecipeSerializer<?> serializer,
            ResourceLocation id,
            String group,
            Ingredient cast,
            int itemCost,
            IMaterialItem result,
            IJsonPredicate<MaterialVariantId> materials,
            boolean consumed,
            boolean switchSlots
    ) {
        super(serializer, id, group, cast, itemCost, result, materials, consumed, switchSlots);
        this.castIngredient = cast;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.CASTING_EMBOSSMENT.get();
    }

    @Override
    public ItemStack assemble(ICastingContainer inv, RegistryAccess access) {
        ItemStack assembled = super.assemble(inv, access);
        ItemStack cast = inv.getStack();
        assembled.getOrCreateTag().put("embossed", cast.save(new CompoundTag()));
        return assembled;
    }
}
