package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.display.TooltipModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import javax.annotation.Nullable;
import java.util.List;

public class DragonBloodTankTrait extends Modifier implements TooltipModifierHook {

    public static final ModifierId MODIFIER_ID = new ModifierId(TinkersNewlife.MOD_ID, "dragon_blood_tank");
    public static final int CAPACITY_PER_LEVEL = 5000;

    private static final ResourceLocation KEY_TANK = new ResourceLocation(TinkersNewlife.MOD_ID, "dragon_blood_tank");

    private static final ResourceLocation FIRE_BLOOD_ID =
            new ResourceLocation(TinkersNewlife.MOD_ID, "fire_blood_still");
    private static final ResourceLocation ICE_BLOOD_ID =
            new ResourceLocation(TinkersNewlife.MOD_ID, "ice_blood_still");
    private static final ResourceLocation LIGHTNING_BLOOD_ID =
            new ResourceLocation(TinkersNewlife.MOD_ID, "lightning_blood_still");

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        hookBuilder.addHook(this, ModifierHooks.TOOLTIP);
    }

    @Override
    public void addTooltip(IToolStackView tool, ModifierEntry modifier,
                           @Nullable Player player, List<Component> tooltip,
                           TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
        int level = modifier.getLevel();
        int capacity = level * CAPACITY_PER_LEVEL;

        DragonBloodTankData data = getTankData(tool, capacity);
        if (data == null) {
            data = new DragonBloodTankData(capacity);
        }

        tooltip.add(Component.translatable("modifier.tinkersnewlife.dragon_blood_tank.capacity",
                data.getTotalFilled(), capacity));

        if (tooltipKey == TooltipKey.SHIFT) {
            if (data.getFireAmount() > 0) {
                tooltip.add(Component.translatable("modifier.tinkersnewlife.dragon_blood_tank.fire",
                        data.getFireAmount()));
            }
            if (data.getIceAmount() > 0) {
                tooltip.add(Component.translatable("modifier.tinkersnewlife.dragon_blood_tank.ice",
                        data.getIceAmount()));
            }
            if (data.getLightningAmount() > 0) {
                tooltip.add(Component.translatable("modifier.tinkersnewlife.dragon_blood_tank.lightning",
                        data.getLightningAmount()));
            }
            int remaining = capacity - data.getTotalFilled();
            if (remaining > 0) {
                tooltip.add(Component.translatable("modifier.tinkersnewlife.dragon_blood_tank.remaining",
                        remaining));
            }
        } else if (data.getTotalFilled() > 0) {
            tooltip.add(Component.translatable("modifier.tinkersnewlife.dragon_blood_tank.shift_hint"));
        }
    }

    @Nullable
    public static DragonBloodTankData getTankData(IToolStackView tool, int capacity) {
        ModDataNBT persistentData = tool.getPersistentData();
        if (!persistentData.contains(KEY_TANK)) {
            return null;
        }
        CompoundTag tankTag = persistentData.getCompound(KEY_TANK);
        return new DragonBloodTankData(tankTag, capacity);
    }

    @Nullable
    public static DragonBloodTankData getTankData(ItemStack stack, int capacity) {
        // ✅ 使用 ToolHelper 安全获取
        ToolStack tool = ToolHelper.getToolStack(stack);
        if (tool == null) return null;
        return getTankData(tool, capacity);
    }

    public static void setTankData(IToolStackView tool, DragonBloodTankData data) {
        ModDataNBT persistentData = tool.getPersistentData();
        persistentData.put(KEY_TANK, data.serializeNBT());
    }

    public static void setTankData(ItemStack stack, DragonBloodTankData data) {
        // ✅ 使用 ToolHelper 安全获取
        ToolStack tool = ToolHelper.getToolStack(stack);
        if (tool == null) return;
        setTankData(tool, data);
    }

    public static int getCapacity(ItemStack stack) {
        // ✅ 使用 ToolHelper 安全获取
        ToolStack tool = ToolHelper.getToolStack(stack);
        if (tool == null) return 0;
        int level = tool.getModifierLevel(MODIFIER_ID);
        return level * CAPACITY_PER_LEVEL;
    }

    public static boolean isDragonBlood(FluidStack fluid) {
        if (fluid.isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fluid.getFluid());
        if (id == null) return false;
        return FIRE_BLOOD_ID.equals(id) || ICE_BLOOD_ID.equals(id) || LIGHTNING_BLOOD_ID.equals(id);
    }

    @Nullable
    public static DragonBloodType getDragonBloodType(FluidStack fluid) {
        if (fluid.isEmpty()) return null;
        ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fluid.getFluid());
        if (id == null) return null;
        if (FIRE_BLOOD_ID.equals(id)) return DragonBloodType.FIRE;
        if (ICE_BLOOD_ID.equals(id)) return DragonBloodType.ICE;
        if (LIGHTNING_BLOOD_ID.equals(id)) return DragonBloodType.LIGHTNING;
        return null;
    }

    @Nullable
    public static ResourceLocation getFluidId(DragonBloodType type) {
        return switch (type) {
            case FIRE -> FIRE_BLOOD_ID;
            case ICE -> ICE_BLOOD_ID;
            case LIGHTNING -> LIGHTNING_BLOOD_ID;
        };
    }

    public static class DragonBloodTankData {
        private int fireAmount;
        private int iceAmount;
        private int lightningAmount;
        private final int capacity;

        public DragonBloodTankData(int capacity) {
            this.capacity = capacity;
            this.fireAmount = 0;
            this.iceAmount = 0;
            this.lightningAmount = 0;
        }

        public DragonBloodTankData(CompoundTag tag, int capacity) {
            this.capacity = capacity;
            this.fireAmount = tag.getInt("fire");
            this.iceAmount = tag.getInt("ice");
            this.lightningAmount = tag.getInt("lightning");
        }

        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("fire", fireAmount);
            tag.putInt("ice", iceAmount);
            tag.putInt("lightning", lightningAmount);
            return tag;
        }

        public int getFireAmount() { return fireAmount; }
        public int getIceAmount() { return iceAmount; }
        public int getLightningAmount() { return lightningAmount; }
        public int getTotalFilled() { return fireAmount + iceAmount + lightningAmount; }
        public int getTotalCapacity() { return capacity; }
        public int getRemaining() { return capacity - getTotalFilled(); }

        public int getAmount(DragonBloodType type) {
            return switch (type) {
                case FIRE -> fireAmount;
                case ICE -> iceAmount;
                case LIGHTNING -> lightningAmount;
            };
        }

        public void setAmount(DragonBloodType type, int amount) {
            int clamped = Math.max(0, Math.min(amount, capacity));
            switch (type) {
                case FIRE -> fireAmount = clamped;
                case ICE -> iceAmount = clamped;
                case LIGHTNING -> lightningAmount = clamped;
            }
        }

        public int fill(DragonBloodType type, int amount) {
            if (amount <= 0) return 0;
            int current = getAmount(type);
            int remaining = capacity - getTotalFilled();
            int available = capacity - current;
            int toFill = Math.min(amount, Math.min(remaining, available));
            if (toFill <= 0) return 0;
            setAmount(type, current + toFill);
            return toFill;
        }

        public void clearAll() {
            fireAmount = 0;
            iceAmount = 0;
            lightningAmount = 0;
        }
    }

    public enum DragonBloodType {
        FIRE("fire", "modifier.tinkersnewlife.dragon_blood_tank.fire"),
        ICE("ice", "modifier.tinkersnewlife.dragon_blood_tank.ice"),
        LIGHTNING("lightning", "modifier.tinkersnewlife.dragon_blood_tank.lightning");

        public final String name;
        public final String translationKey;

        DragonBloodType(String name, String translationKey) {
            this.name = name;
            this.translationKey = translationKey;
        }
    }
}