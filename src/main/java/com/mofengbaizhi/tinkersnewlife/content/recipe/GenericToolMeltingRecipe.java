package com.mofengbaizhi.tinkersnewlife.content.recipe;

import com.mofengbaizhi.tinkersnewlife.content.ModRecipeSerializers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.server.ServerLifecycleHooks;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.recipe.TinkerRecipeTypes;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialFluidRecipe;
import slimeknights.tconstruct.library.recipe.melting.IMeltingContainer;
import slimeknights.tconstruct.library.recipe.melting.IMeltingRecipe;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用匠魂工具熔化配方（一条配方自动覆盖所有工具，含咒力核心）。
 * <p>
 * 匹配任意匠魂工具（{@link IModifiable}），融炼前读取工具<b>所有部件材料</b>：
 * <ul>
 *   <li>熔炼温度 = 有对应熔融流体的部件材料中，<b>融化温度最高</b>者（无流体材料的"熔化"只消失、不贡献温度）</li>
 *   <li>输出 = 该最高温且含流体的材料流体，用量 = 该材料的部件数 × 每部件一单位
 *       （{@code MaterialFluidRecipe#getFluidAmount}，即"融化为一单位材料流体"）</li>
 *   <li>没有对应熔融流体的部件材料 → 融化消失（不产出流体）</li>
 *   <li>TConstruct 熔炼为单流体输出，故多材料工具只产出温度最高者；其余有流体材料不产出（可拆件分熔）</li>
 * </ul>
 * 用于替代原来仅咒力核心专用的 {@link AutoMaterialMeltingRecipe}（通用化）。
 */
public class GenericToolMeltingRecipe implements IMeltingRecipe {

    public static final RecordLoadable<GenericToolMeltingRecipe> LOADER = RecordLoadable.create(
            ContextKey.ID.requiredField(),
            GenericToolMeltingRecipe::new
    );

    private final ResourceLocation id;

    public GenericToolMeltingRecipe(ResourceLocation id) {
        this.id = id;
    }

    private record Selected(MaterialId material, MaterialFluidRecipe recipe, int partCount) {}

    @Override
    @SuppressWarnings("rawtypes")
    public boolean matches(IMeltingContainer container, Level level) {
        Selected sel = resolve(container, level.getRecipeManager());
        return sel != null;
    }

    @Override
    public FluidStack getOutput(IMeltingContainer container) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return FluidStack.EMPTY;
        Selected sel = resolve(container, server.getRecipeManager());
        if (sel == null) return FluidStack.EMPTY;
        List<FluidStack> fluids = sel.recipe().getFluids();
        if (fluids.isEmpty()) return FluidStack.EMPTY;
        FluidStack fluid = fluids.get(0);
        int perUnit = sel.recipe().getFluidAmount(fluid.getFluid());
        return new FluidStack(fluid.getFluid(), perUnit * sel.partCount());
    }

    @Override
    public int getTemperature(IMeltingContainer container) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return 300;
        Selected sel = resolve(container, server.getRecipeManager());
        return sel == null ? 300 : sel.recipe().getTemperature();
    }

    @Override
    public int getTime(IMeltingContainer container) {
        return IMeltingRecipe.calcTimeForAmount(getTemperature(container),
                getOutput(container).getAmount());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.TOOL_MELTING.get();
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    /** 解析：温度最高的有流体材料及其部件数；无则 null */
    private static Selected resolve(IMeltingContainer container, RecipeManager manager) {
        ToolStack tool = getTool(container.getStack());
        if (tool == null) return null;
        Map<MaterialId, MaterialFluidRecipe> recipes = findFluidRecipes(manager);
        if (recipes.isEmpty()) return null;
        Map<MaterialId, Integer> counts = new HashMap<>();
        for (MaterialVariant variant : tool.getMaterials().getList()) {
            if (recipes.containsKey(variant.getId())) {
                counts.merge(variant.getId(), 1, Integer::sum);
            }
        }
        if (counts.isEmpty()) return null;
        MaterialId best = null;
        MaterialFluidRecipe bestRecipe = null;
        int bestTemp = -1;
        for (Map.Entry<MaterialId, Integer> e : counts.entrySet()) {
            MaterialFluidRecipe mfr = recipes.get(e.getKey());
            int temp = mfr.getTemperature();
            if (temp > bestTemp) {
                bestTemp = temp;
                best = e.getKey();
                bestRecipe = mfr;
            }
        }
        return best == null ? null : new Selected(best, bestRecipe, counts.get(best));
    }

    /** 仅接受匠魂工具（IModifiable，含咒力核心） */
    private static ToolStack getTool(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof IModifiable)) return null;
        ToolStack tool = ToolStack.from(stack);
        return tool == null ? null : tool;
    }

    /** 收集所有 material_fluid 配方：材料 → 配方 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Map<MaterialId, MaterialFluidRecipe> findFluidRecipes(RecipeManager manager) {
        Map<MaterialId, MaterialFluidRecipe> map = new HashMap<>();
        RecipeType rawType = TinkerRecipeTypes.DATA.get();
        for (Recipe<?> recipe : (java.util.Collection<Recipe<?>>) (java.util.Collection<?>) manager.getAllRecipesFor(rawType)) {
            if (recipe instanceof MaterialFluidRecipe mfr) {
                MaterialVariant output = mfr.getOutput();
                if (output != null && !output.isUnknown()) {
                    map.put(output.getId(), mfr);
                }
            }
        }
        return map;
    }
}
