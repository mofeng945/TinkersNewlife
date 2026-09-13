package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModFluids;
import com.mofengbaizhi.tinkersnewlife.content.curse.CurseBottleHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * 封呪瓶：存储咒力的饰品容器。
 *
 * <ul>
 *   <li>佩戴于 curios 通用「饰品」槽（{@code curio}）；</li>
 *   <li>佩戴时**咒力优先进瓶**（回复/奖励/返还都先灌瓶，满了才进咒力核心池）；</li>
 *   <li>消耗顺序：咒力核心池 → 瓶 → 诡厄灵魂能量兜底（见 {@code CursePowerHelper}）；</li>
 *   <li>瓶内咒力写在物品 NBT，**死亡不丢**（玩家咒力池被清空也不影响瓶中储备）；</li>
 *   <li>最多 {@link CurseBottleHelper#CAPACITY} 点，以**耐久条**形式显示；</li>
 *   <li>可作为**流体容器**接取匠魂熔炉里的咒力残秽：1 mb = {@link CurseBottleHelper#POWER_PER_MB} 咒力，
 *       满瓶 {@link CurseBottleHelper#CAPACITY_MB} mb。古代咒术残卷熔炼即得 1 mb 残秽。</li>
 * </ul>
 */
public class CurseBottleItem extends Item implements ICurioItem {

    /** 耐久条颜色（咒力紫） */
    private static final int BAR_COLOR = 0xA855F7;

    public CurseBottleItem() {
        super(new Properties().stacksTo(1));
    }

    // ========== ICurioItem：通用「饰品」槽 ==========

    @Override
    public boolean canEquip(SlotContext context, ItemStack stack) {
        return "curio".equals(context.identifier());
    }

    @Override
    public boolean canEquipFromUse(SlotContext context, ItemStack stack) {
        return false;
    }

    // ========== 耐久条 = 瓶内咒力 ==========

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return CurseBottleHelper.getPower(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        double ratio = CurseBottleHelper.getPower(stack) / CurseBottleHelper.CAPACITY;
        return (int) Math.round(Math.max(0.0, Math.min(1.0, ratio)) * 13.0);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    // ========== 物品提示 ==========

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                               List<Component> tooltip, TooltipFlag flag) {
        double power = CurseBottleHelper.getPower(stack);
        tooltip.add(Component.translatable("item.tinkersnewlife.curse_bottle.power",
                        format(power), format(CurseBottleHelper.CAPACITY))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("item.tinkersnewlife.curse_bottle.fluid",
                        CurseBottleHelper.getFluidAmount(stack), CurseBottleHelper.CAPACITY_MB)
                .withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("item.tinkersnewlife.curse_bottle.hint")
                .withStyle(ChatFormatting.GRAY));
    }

    private static String format(double value) {
        if (value == Math.floor(value)) return String.valueOf((long) value);
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    // ========== 流体容器能力（接取熔炉中的咒力残秽） ==========

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new Caps(stack);
    }

    private static final class Caps implements ICapabilityProvider {
        private final LazyOptional<IFluidHandlerItem> holder;

        Caps(ItemStack stack) {
            this.holder = LazyOptional.of(() -> new BottleFluidHandler(stack));
        }

        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
            if (cap == ForgeCapabilities.FLUID_HANDLER_ITEM) return holder.cast();
            return LazyOptional.empty();
        }
    }

    /**
     * 封呪瓶的流体接口：只认本模组的「咒力残秽」，进多少 mb 就转成 10 倍咒力存进瓶里。
     * 有了它，玩家可以拿着瓶子在匠魂熔炉的**排液口**右键接取残秽（也能被其它模组的流体管道/储罐灌）。
     */
    private static final class BottleFluidHandler implements IFluidHandlerItem {
        private final ItemStack container;

        BottleFluidHandler(ItemStack container) {
            this.container = container;
        }

        @Override
        public ItemStack getContainer() {
            return container;
        }

        /** 本模组的咒力残秽（静止流体） */
        @Nullable
        private static net.minecraft.world.level.material.Fluid residue() {
            return ModFluids.CURSE_RESIDUE.still.get();
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Nonnull
        @Override
        public FluidStack getFluidInTank(int tank) {
            int mb = CurseBottleHelper.getFluidAmount(container);
            var fluid = residue();
            if (mb <= 0 || fluid == null) return FluidStack.EMPTY;
            return new FluidStack(fluid, mb);
        }

        @Override
        public int getTankCapacity(int tank) {
            return CurseBottleHelper.CAPACITY_MB;
        }

        @Override
        public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
            var fluid = residue();
            return fluid != null && !stack.isEmpty() && stack.getFluid() == fluid;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !isFluidValid(0, resource)) return 0;
            int space = CurseBottleHelper.CAPACITY_MB - CurseBottleHelper.getFluidAmount(container);
            // 先看咒力余量，避免"1mb 装不满但咒力已经到顶"时多算
            double spacePower = CurseBottleHelper.CAPACITY - CurseBottleHelper.getPower(container);
            int spaceMb = (int) Math.min(space, Math.floor(spacePower / CurseBottleHelper.POWER_PER_MB));
            int accepted = Math.min(resource.getAmount(), Math.max(0, spaceMb));
            if (accepted <= 0 || action.simulate()) return Math.max(0, accepted);
            CurseBottleHelper.addPower(container, (double) accepted * CurseBottleHelper.POWER_PER_MB);
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
            if (fluid == null) return FluidStack.EMPTY;
            int mb = Math.min(maxDrain, CurseBottleHelper.getFluidAmount(container));
            if (mb <= 0) return FluidStack.EMPTY;
            if (action.execute()) {
                CurseBottleHelper.consumePower(container, (double) mb * CurseBottleHelper.POWER_PER_MB);
            }
            return new FluidStack(fluid, mb);
        }
    }

    /** 语言键自检用（避免拼错键名时静默显示原文） */
    public static final String LANG_PREFIX = "item." + TinkersNewlife.MOD_ID + ".curse_bottle";
}
