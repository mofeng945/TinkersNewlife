package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import com.mofengbaizhi.tinkersnewlife.content.energy.EnergyUnits;
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
 * <b>万用能量转化器</b>的物品形态（§557）：只负责把"怎么用 + 汇率表"写在提示里 ✓
 * （行为全在 {@code block.EnergyConverterBlockEntity} ✓ 这里没有任何逻辑 ✗）。
 *
 * <h2>口径（用户明确）</h2>
 * <b>提示精简、别写具体数字细则</b> ✗ ⇒ 这里<b>不</b>逐条列 "1 EU = 4 FE" 之类 ✗，
 * 只给一句话的"输入有哪些、输出是 FE、单向"✓ 加上一条实时速率读数（配置值 ✓ 与台座同一做法 ✓）。
 * <p>完整汇率表在 {@link EnergyUnits.Fe#describeRate()} 里 ✓ 需要的地方（日志/手册）自己取 ✓。
 */
public class EnergyConverterItem extends BlockItem {

    public EnergyConverterItem(Block block) {
        super(block, new Properties());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                               List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.tinkersnewlife.energy_converter.tip.in")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("block.tinkersnewlife.energy_converter.tip.out")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("block.tinkersnewlife.energy_converter.tip.oneway")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("block.tinkersnewlife.energy_converter.tip.rate",
                        ModConfig.converterOutputFePerTick())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("block.tinkersnewlife.energy_converter.tip.buffer",
                        ModConfig.converterBufferFe())
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
