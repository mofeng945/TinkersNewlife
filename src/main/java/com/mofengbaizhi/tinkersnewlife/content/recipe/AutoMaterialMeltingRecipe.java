package com.mofengbaizhi.tinkersnewlife.content.recipe;

import com.mofengbaizhi.tinkersnewlife.content.ModItems;
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
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 万能材料熔化配方（一条配方自动解决所有流体材料）
 * <p>
 * 仅匹配$(bold)咒力核心$()（单部件单材料，熔化输出即该材料的流体，用量与浇铸消耗一致）。
 * 无需为每种材料手写 {@code material_melting}：本配方按整体物品匹配，
 * 自动查找咒力核心材料对应的 {@code tconstruct:material_fluid} 配方并输出其流体。
 * 以后新增流体材料只需写一个 material_fluid 配方，本配方自动生效。
 */
public class AutoMaterialMeltingRecipe implements IMeltingRecipe {

    public static final RecordLoadable<AutoMaterialMeltingRecipe> LOADER = RecordLoadable.create(
            ContextKey.ID.requiredField(),
            AutoMaterialMeltingRecipe::new
    );

    private final ResourceLocation id;

    public AutoMaterialMeltingRecipe(ResourceLocation id) {
        this.id = id;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public boolean matches(IMeltingContainer container, Level level) {
        ToolStack core = getCurseCore(container.getStack());
        if (core == null) return false;
        Map<MaterialId, MaterialFluidRecipe> recipes = findFluidRecipes(level.getRecipeManager());
        for (MaterialVariant material : core.getMaterials().getList()) {
            if (recipes.containsKey(material.getId())) return true;
        }
        return false;
    }

    @Override
    public FluidStack getOutput(IMeltingContainer container) {
        MaterialFluidRecipe mfr = findFluidRecipe(container);
        if (mfr == null) return FluidStack.EMPTY;
        List<FluidStack> fluids = mfr.getFluids();
        if (fluids.isEmpty()) return FluidStack.EMPTY;
        FluidStack fluid = fluids.get(0);
        int amount = mfr.getFluidAmount(fluid.getFluid());
        return new FluidStack(fluid.getFluid(), amount);
    }

    @Override
    public int getTemperature(IMeltingContainer container) {
        MaterialFluidRecipe mfr = findFluidRecipe(container);
        return mfr == null ? 300 : mfr.getTemperature();
    }

    @Override
    public int getTime(IMeltingContainer container) {
        return IMeltingRecipe.calcTimeForAmount(getTemperature(container), getOutput(container).getAmount());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.AUTO_MATERIAL_MELTING.get();
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    /** 咒力核心材料中第一个存在流体配方的材料所对应的配方 */
    private MaterialFluidRecipe findFluidRecipe(IMeltingContainer container) {
        ToolStack core = getCurseCore(container.getStack());
        if (core == null) return null;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        Map<MaterialId, MaterialFluidRecipe> recipes = findFluidRecipes(server.getRecipeManager());
        for (MaterialVariant material : core.getMaterials().getList()) {
            MaterialFluidRecipe mfr = recipes.get(material.getId());
            if (mfr != null) return mfr;
        }
        return null;
    }

    /** 仅接受咒力核心（单部件单材料，熔化输出即该材料流体） */
    private static ToolStack getCurseCore(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != ModItems.CURSE_CORE.get()) return null;
        return ToolStack.from(stack);
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
