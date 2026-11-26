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
 * <b>EE 抽取方块</b>的物品形态（§558 最新口径）：只负责把"怎么用"写在提示里 ✓
 * （行为全在 {@code block.EeExtractorBlockEntity} ✓ 这里没有任何逻辑 ✗）。
 *
 * <h2>口径（用户明确）</h2>
 * <b>提示要精简 —— 能看出效果就够，别写具体数字细则</b> ✗ ⇒
 * 这里只说清「<b>右键开 GUI → 往槽位里放有能量的水晶</b> ⇒ 抽进缓存 ⇒ 推给邻居」✓
 * 加上两条实时读数（速率 / 缓存上限 ✓ 都是配置值 ⇒ 与台座 tooltip 的既有做法一致 ✓）。
 */
public class EeExtractorItem extends BlockItem {

    public EeExtractorItem(Block block) {
        super(block, new Properties());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                               List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.tinkersnewlife.ee_extractor.tip.in")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("block.tinkersnewlife.ee_extractor.tip.out")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("block.tinkersnewlife.ee_extractor.tip.slot")
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("block.tinkersnewlife.ee_extractor.tip.rate",
                        ModConfig.eeExtractorPullPerTick(), ModConfig.eeExtractorPushPerTick())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("block.tinkersnewlife.ee_extractor.tip.buffer",
                        ModConfig.eeExtractorBuffer())
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
