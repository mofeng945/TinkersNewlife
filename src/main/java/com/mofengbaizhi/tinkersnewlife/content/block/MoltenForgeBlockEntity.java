package com.mofengbaizhi.tinkersnewlife.content.block;

import com.mofengbaizhi.tinkersnewlife.content.ModBlockEntities;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.server.ServerLifecycleHooks;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.recipe.TinkerRecipeTypes;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialFluidRecipe;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 融锻炉：独立熔炼方块，持有<b>多流体</b>容器 + 一个工具熔炼槽。
 * <p>工具放入熔炼槽（GUI 或右键），每 tick 读取其所有部件材料，有 {@code material_fluid}
 * 配方的材料按部件数注入对应流体到炉内多流体容器（每部件一单位）；无对应流体的材料直接化掉。
 * 产物可被桶/管道抽取（{@link IFluidHandler}，最多 {@link #TANKS} 种流体，每种 {@link #CAPACITY}mb）。
 */
public class MoltenForgeBlockEntity extends BlockEntity implements IFluidHandler {

    /** 容纳的最多流体种类 */
    public static final int TANKS = 6;
    /** 每种流体容量（mb） */
    public static final int CAPACITY = 8000;

    /** 工具熔炼槽 */
    private final ItemStackHandler toolHandler = new ItemStackHandler(1);
    /** 多流体容器 */
    private final List<FluidStack> fluids = new ArrayList<>(TANKS);
    /** 流体能力持有（供机械动力/匠魂等管道转运） */
    private final LazyOptional<IFluidHandler> fluidHandler = LazyOptional.of(() -> this);

    public MoltenForgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MOLTEN_FORGE.get(), pos, state);
    }

    // ==================== 工具槽 ====================

    public ItemStackHandler getToolHandler() {
        return toolHandler;
    }

    /** 每 tick（服务端）：熔炼工具槽内的工具 */
    public void serverTick() {
        if (level == null || level.isClientSide) return;
        ItemStack tool = toolHandler.getStackInSlot(0);
        if (tool.isEmpty()) return;
        if (molten(tool)) {
            toolHandler.setStackInSlot(0, ItemStack.EMPTY);
            setChanged();
        }
    }

    // ==================== 工具 → 多流体 ====================

    /** 融炼一个匠魂工具：读所有部件材料，有流体配方者注入（每部件一单位），无流体者消失；成功注入任意流体返回 true */
    public boolean molten(ItemStack tool) {
        if (tool == null || tool.isEmpty()) return false;
        ToolStack stack = ToolHelper.getToolStack(tool);
        if (stack == null) return false;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return false;
        Map<MaterialId, MaterialFluidRecipe> recipes = findFluidRecipes(server.getRecipeManager());
        boolean any = false;
        for (MaterialVariant variant : stack.getMaterials().getList()) {
            MaterialFluidRecipe mfr = recipes.get(variant.getId());
            if (mfr == null) continue;   // 无对应流体 → 直接消失
            List<FluidStack> list = mfr.getFluids();
            if (list.isEmpty()) continue;
            FluidStack fluid = list.get(0);
            int perUnit = mfr.getFluidAmount(fluid.getFluid());
            if (perUnit <= 0) perUnit = 144;
            int added = fill(new FluidStack(fluid.getFluid(), perUnit), IFluidHandler.FluidAction.EXECUTE);
            if (added > 0) any = true;
        }
        if (any) setChanged();
        return any;
    }

    private static Map<MaterialId, MaterialFluidRecipe> findFluidRecipes(RecipeManager manager) {
        Map<MaterialId, MaterialFluidRecipe> map = new HashMap<>();
        @SuppressWarnings({"rawtypes", "unchecked"})
        RecipeType rawType = TinkerRecipeTypes.DATA.get();
        for (Recipe<?> recipe : (Iterable<Recipe<?>>) (Iterable<?>) manager.getAllRecipesFor(rawType)) {
            if (recipe instanceof MaterialFluidRecipe mfr) {
                MaterialVariant output = mfr.getOutput();
                if (output != null && !output.isUnknown()) {
                    map.put(output.getId(), mfr);
                }
            }
        }
        return map;
    }

    // ==================== 多流体 IFluidHandler ====================

    /** 当前容纳的流体快照（供 GUI 渲染） */
    public List<FluidStack> getFluids() {
        return new ArrayList<>(fluids);
    }

    @Override
    public int getTanks() { return TANKS; }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return tank >= 0 && tank < fluids.size() ? fluids.get(tank) : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) { return CAPACITY; }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) { return !stack.isEmpty(); }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource == null || resource.isEmpty() || resource.getAmount() <= 0) return 0;
        Fluid fluid = resource.getFluid();
        long amount = resource.getAmount();
        for (FluidStack fs : fluids) {
            if (fs.getFluid() == fluid) {
                int space = CAPACITY - fs.getAmount();
                int add = (int) Math.min(space, amount);
                if (add > 0 && action.execute()) {
                    fs.setAmount(fs.getAmount() + add);
                    setChanged();
                }
                return add;
            }
        }
        if (fluids.size() >= TANKS) return 0;
        int add = (int) Math.min(CAPACITY, amount);
        if (add > 0 && action.execute()) {
            fluids.add(new FluidStack(fluid, add));
            setChanged();
        }
        return add;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource == null || resource.isEmpty()) return FluidStack.EMPTY;
        Fluid fluid = resource.getFluid();
        for (int i = 0; i < fluids.size(); i++) {
            FluidStack fs = fluids.get(i);
            if (fs.getFluid() == fluid) {
                int take = Math.min(fs.getAmount(), resource.getAmount());
                if (action.execute()) {
                    if (take >= fs.getAmount()) fluids.remove(i);
                    else fs.setAmount(fs.getAmount() - take);
                    setChanged();
                }
                return new FluidStack(fluid, take);
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (fluids.isEmpty()) return FluidStack.EMPTY;
        FluidStack fs = fluids.get(0);
        return drain(new FluidStack(fs.getFluid(), maxDrain), action);
    }

    /** 玩家仍可交互（方块存在且在范围内） */
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    // ==================== Capability（流体转运） ====================

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.FLUID_HANDLER) {
            return fluidHandler.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidHandler.invalidate();
    }

    // ==================== NBT（含客户端同步） ====================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("tool", toolHandler.serializeNBT());
        ListTag list = new ListTag();
        for (FluidStack fs : fluids) {
            CompoundTag f = new CompoundTag();
            if (!fs.isEmpty()) fs.writeToNBT(f);
            list.add(f);
        }
        tag.put("fluids", list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("tool")) toolHandler.deserializeNBT(tag.getCompound("tool"));
        fluids.clear();
        ListTag list = tag.getList("fluids", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            FluidStack read = FluidStack.loadFluidStackFromNBT(list.getCompound(i));
            if (!read.isEmpty()) fluids.add(read);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }
}
