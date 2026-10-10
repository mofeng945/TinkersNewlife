package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.List;

/**
 * <b>魔力台座</b>的物品形态：只负责<b>把充能规则写在物品提示里</b> ✓
 * （台座的行为全在 {@code block.ElderManaPedestalBlock} / {@code block.ElderManaPedestalBlockEntity} ✓
 * 这里没有任何逻辑 ✗）。走的是本模组既有口径：「新特性出厂三件套」里玩家第一眼能看到的
 * tooltip 要能自己说清用法 ✓。
 *
 * <p>⚠ 提示里的<b>数字全部来自 {@link ModConfig}</b>（不是写死的文案 ✓）——
 * 玩家改了 {@code elder_crystal.pedestal_charge_max_per_second} / {@code pedestal_light_cap}
 * 之后，tooltip 会立刻跟着变 ✓ 不会出现"手册说 5、实际是 10"这种事 ✗。
 */
public class ElderManaPedestalItem extends BlockItem {

    public ElderManaPedestalItem(Block block) {
        super(block, new Properties());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                               List<Component> tooltip, TooltipFlag flag) {
        // ① 满速（亮度 0 时）—— 数字取自配置 ✓
        tooltip.add(Component.translatable("block.tinkersnewlife.elder_mana_pedestal.rate",
                        trim(ModConfig.pedestalChargeMaxPerSecond()))
                .withStyle(ChatFormatting.AQUA));
        // ② 规则：亮度越低越快 + 亮到多少就停 —— 阈值同样取自配置 ✓
        tooltip.add(Component.translatable("block.tinkersnewlife.elder_mana_pedestal.rule",
                        ModConfig.pedestalLightCap())
                .withStyle(ChatFormatting.GRAY));
    }

    /** 去掉多余的小数尾巴（5.0 ⇒ "5"、0.25 ⇒ "0.25" ✓ 纯显示，不参与任何计算 ✓） */
    private static String trim(double value) {
        // 显示保留两位小数（本地化无关：小数点在中文/英文里都是 "." ✓）
        java.math.BigDecimal bd = java.math.BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP);
        return bd.stripTrailingZeros().toPlainString();
    }
}
