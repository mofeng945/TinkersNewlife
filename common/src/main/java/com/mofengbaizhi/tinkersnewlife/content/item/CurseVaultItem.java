package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.CurseVaultData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 呪蔵的物品形态：放下时绑定放置者，回收时把方块里的咒力带回物品 NBT。
 *
 * <p>与封呪瓶一样：耐久条显示存量占比，tooltip 显示 咒力/容量 与 mb 折算。
 */
public class CurseVaultItem extends BlockItem {

    /** 物品 NBT：携带的咒力（放置时写进 world data） */
    public static final String KEY_POWER = "tinkersnewlife.curse_vault_power";
    /** 耐久条颜色（与封呪瓶同色系，稍深一点以区分） */
    private static final int BAR_COLOR = 0x8B5CF6;

    public CurseVaultItem(net.minecraft.world.level.block.Block block) {
        super(block, new Properties().stacksTo(1));
    }

    // ========== 咒力读写（与方块共享 NBT 键） ==========

    public static double getPower(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getTag() == null) return 0;
        double v = stack.getTag().getDouble(KEY_POWER);
        return Math.max(0, Math.min(v, CurseVaultData.CAPACITY));
    }

    public static void setPower(ItemStack stack, double value) {
        if (stack == null || stack.isEmpty()) return;
        double v = Math.max(0, Math.min(value, CurseVaultData.CAPACITY));
        if (v <= 0) {
            if (stack.getTag() != null) stack.getTag().remove(KEY_POWER);
            return;
        }
        stack.getOrCreateTag().putDouble(KEY_POWER, v);
    }

    // ========== 耐久条 ==========

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getPower(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        double ratio = getPower(stack) / CurseVaultData.CAPACITY;
        return (int) Math.round(Math.max(0.0, Math.min(1.0, ratio)) * 13.0);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    // ========== tooltip ==========

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                               List<Component> tooltip, TooltipFlag flag) {
        double power = getPower(stack);
        tooltip.add(Component.translatable("block.tinkersnewlife.curse_vault.power",
                        CursePowerHelper.formatAmount(power),
                        CursePowerHelper.formatAmount(CurseVaultData.CAPACITY))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("block.tinkersnewlife.curse_vault.fluid",
                        (long) Math.floor(power / CurseVaultData.POWER_PER_MB), CurseVaultData.CAPACITY_MB)
                .withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("block.tinkersnewlife.curse_vault.hint")
                .withStyle(ChatFormatting.GRAY));
    }

}
