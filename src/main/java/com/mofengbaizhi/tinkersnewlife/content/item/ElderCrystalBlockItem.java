package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.content.block.ElderCrystalBlock;
import com.mofengbaizhi.tinkersnewlife.content.energy.ElderCrystalStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * <b>古老者水晶方块</b>的物品形态：容量 4000 EE，EE 存在 {@code BlockEntityTag.EE}。
 *
 * <p>为什么用 {@code BlockEntityTag} 而不是自定义键：见
 * {@link ElderCrystalStorage} 的类注释 —— 这是原版方块物品的通用约定，
 * 「挖掉掉自己 + 放下还原」由原版机制自动完成 ✓（潜影盒同款）。
 * 也就是说本类<b>不需要重写 place / playerDestroy</b> ✗，只负责显示 ✓。
 */
public class ElderCrystalBlockItem extends BlockItem {

    /** 耐久条颜色（与水晶同色系，稍深） */
    private static final int BAR_COLOR = 0x7C6BE0;

    public ElderCrystalBlockItem(ElderCrystalBlock block) {
        super(block, new Properties().stacksTo(1));
    }

    // ========== 耐久条 = 已存 EE ==========

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return ElderCrystalStorage.getBlockItemEe(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        double ratio = ElderCrystalStorage.getBlockItemEe(stack) / (double) ElderCrystalStorage.BLOCK_CAPACITY;
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
        int ee = ElderCrystalStorage.getBlockItemEe(stack);
        tooltip.add(Component.translatable("block.tinkersnewlife.elder_crystal_block.power",
                        ee, ElderCrystalStorage.BLOCK_CAPACITY)
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("block.tinkersnewlife.elder_crystal_block.rate")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("block.tinkersnewlife.elder_crystal_block.hint")
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
