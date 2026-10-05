package com.mofengbaizhi.tinkersnewlife.content.recipe;

// 移植自 TiCEX (MIT): moffy.ticex.lib.recipe.EmbossmentBuildingRecipe

import com.mofengbaizhi.tinkersnewlife.content.ModRecipeSerializers;
import com.mofengbaizhi.tinkersnewlife.util.EmbossmentHelper;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.jetbrains.annotations.NotNull;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.common.IngredientLoadable;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.helper.LoadableRecipeSerializer;
import slimeknights.tconstruct.library.json.TinkerLoadables;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.recipe.RecipeResult;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationContainer;
import slimeknights.tconstruct.library.recipe.tinkerstation.building.ToolBuildingRecipe;
import slimeknights.tconstruct.library.tools.definition.module.material.ToolPartsHook;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.LazyToolStack;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;

/**
 * <b>"装裱建刀"</b> —— 逐字移植 TiCEX {@code EmbossmentBuildingRecipe}（MIT）。
 *
 * <p>= 匠魂自带 {@code tconstruct:tool_building} 配方 ✓，只是把 {@link #getValidatedResult} 换成
 * "建出工具后再走一遍 {@link EmbossmentHelper#applyCatalystEmbossment}（{@code copyAttribute = true} ✓）" ✓ ——
 * 也就是：如果建刀时输入槽里塞了带 {@code embossed} 标签的东西 ✓，那把"被装裱的刀"的
 * **装备属性 ＋ 存档 NBT** 会一并并进新刀 ✓（TiCEX 用它给重锋拔刀剑建刀 ✓）。
 *
 * <p>⚠ <b>本仓现状</b>：序列化器已注册 ✓，但**没有数据文件在用它** ✗ —— 本仓的
 * {@code data/tinkersnewlife/recipes/tools/building/katana.json} 仍然是原版
 * {@code tconstruct:tool_building} ✓（拔刀剑的三个部件槽不可能放得进"被装裱的刀" x
 * ⇒ 换成它<b>没有任何可观察差别</b> ✗，却要冒改坏建刀流程的风险 ✗ ⇒ 本轮不动 ✓）。
 * 需要时把那条 JSON 的 {@code type} 改成 {@code tinkersnewlife:embossment_building} 即可 ✓。
 */
public class EmbossmentBuildingRecipe extends ToolBuildingRecipe {

    public static final RecordLoadable<EmbossmentBuildingRecipe> LOADER = RecordLoadable.create(
            ContextKey.ID.requiredField(),
            LoadableRecipeSerializer.RECIPE_GROUP,
            TinkerLoadables.MODIFIABLE_ITEM.requiredField("result", r -> r.output),
            IntLoadable.FROM_ONE.defaultField("result_count", 1, true, r -> r.outputCount),
            Loadables.RESOURCE_LOCATION.nullableField("slot_layout", r -> r.layoutSlot),
            IngredientLoadable.DISALLOW_EMPTY.list(0).defaultField("extra_requirements", List.of(), r -> r.ingredients),
            EmbossmentBuildingRecipe::new
    );

    public EmbossmentBuildingRecipe(
            ResourceLocation id,
            String group,
            IModifiable output,
            int outputCount,
            ResourceLocation layoutSlot,
            List<Ingredient> ingredients
    ) {
        super(id, group, output, outputCount, layoutSlot, ingredients, null, List.of());
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.BUILDING_EMBOSSMENT.get();
    }

    @Override
    public @NotNull RecipeResult<LazyToolStack> getValidatedResult(@NotNull ITinkerStationContainer inv,
                                                                  @NotNull RegistryAccess access) {
        List<MaterialVariant> materials = IntStream.range(0, ToolPartsHook.parts(output.getToolDefinition()).size())
                .mapToObj(i -> MaterialVariant.of(IMaterialItem.getMaterialFromStack(inv.getInput(i))))
                .toList();
        ItemStack resultStack = ToolStack.createTool(
                output.asItem(),
                output.getToolDefinition(),
                new MaterialNBT(materials)
        ).createStack(outputCount);
        return LazyToolStack.success(EmbossmentHelper.applyCatalystEmbossment(resultStack, inv, true));
    }
}
