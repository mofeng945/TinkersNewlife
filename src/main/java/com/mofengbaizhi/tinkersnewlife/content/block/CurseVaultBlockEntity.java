package com.mofengbaizhi.tinkersnewlife.content.block;

import com.mofengbaizhi.tinkersnewlife.content.ModBlockEntities;
import com.mofengbaizhi.tinkersnewlife.content.ModFluids;
import com.mofengbaizhi.tinkersnewlife.content.curse.CurseVaultData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 呪蔵的方块实体：**只提供流体能力**（把匠魂熔炉/管道送来的「咒力残秽」按 1mb = 10 咒力转成咒力存进
 * {@link CurseVaultData}）。
 *
 * <p>咒力本身不存在这里（存在 world data 里，见 {@link CurseVaultData} 的说明），
 * 所以方块实体没有自己的存档数据，也就不会出现"方块实体与全局数据两套账"的问题。
 */
public class CurseVaultBlockEntity extends BlockEntity {

    private final LazyOptional<IFluidHandler> fluidHolder = LazyOptional.of(() -> new VaultFluidHandler(this));

    public CurseVaultBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CURSE_VAULT.get(), pos, state);
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) return fluidHolder.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidHolder.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        // 无自有状态（咒力在 world data）
        super.saveAdditional(tag);
    }

    /** 只认「咒力残秽」，进多少 mb 就转成 10 倍咒力存进该呪蔵 */
    private static final class VaultFluidHandler implements IFluidHandler {
        private final CurseVaultBlockEntity be;

        VaultFluidHandler(CurseVaultBlockEntity be) {
            this.be = be;
        }

        @Nullable
        private net.minecraft.world.level.material.Fluid residue() {
            return ModFluids.CURSE_RESIDUE.still.get();
        }

        private CurseVaultData data() {
            return be.getLevel() == null ? null : CurseVaultData.getOrNull(be.getLevel());
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Nonnull
        @Override
        public FluidStack getFluidInTank(int tank) {
            var fluid = residue();
            CurseVaultData data = data();
            if (fluid == null || data == null) return FluidStack.EMPTY;
            int mb = (int) Math.floor(data.getPower(be.getBlockPos()) / CurseVaultData.POWER_PER_MB);
            return mb <= 0 ? FluidStack.EMPTY : new FluidStack(fluid, mb);
        }

        @Override
        public int getTankCapacity(int tank) {
            return CurseVaultData.CAPACITY_MB;
        }

        @Override
        public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
            var fluid = residue();
            return fluid != null && !stack.isEmpty() && stack.getFluid() == fluid;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !isFluidValid(0, resource)) return 0;
            CurseVaultData data = data();
            if (data == null) return 0;
            double free = data.getFreeSpace(be.getBlockPos());
            int spaceMb = (int) Math.floor(free / CurseVaultData.POWER_PER_MB);
            int accepted = Math.min(resource.getAmount(), Math.max(0, spaceMb));
            if (accepted <= 0 || action.simulate()) return Math.max(0, accepted);
            data.addPower(be.getBlockPos(), (double) accepted * CurseVaultData.POWER_PER_MB);
            return accepted;
        }

        @Nonnull
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !isFluidValid(0, resource)) return FluidStack.EMPTY;
            return drain(resource.getAmount(), action);
        }

        @Nonnull
        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            var fluid = residue();
            CurseVaultData data = data();
            if (fluid == null || data == null) return FluidStack.EMPTY;
            int mb = Math.min(maxDrain,
                    (int) Math.floor(data.getPower(be.getBlockPos()) / CurseVaultData.POWER_PER_MB));
            if (mb <= 0) return FluidStack.EMPTY;
            if (action.execute()) {
                data.consumePower(be.getBlockPos(), (double) mb * CurseVaultData.POWER_PER_MB);
            }
            return new FluidStack(fluid, mb);
        }
    }
}
