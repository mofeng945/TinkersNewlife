package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.FlyingSwordItem;

import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 物品额外描述提示（仅客户端）。
 * <p>
 * 注意：使用 I18n.get() 判断翻译键是否存在，而不是 Component.translatable().getString()——
 * 后者对含 % 占位符的翻译值会抛 MissingFormatArgumentException 导致崩溃。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT)
public class ModTooltips {

    /** 翻译键是否存在（I18n.get 缺失时返回键本身，且不做 % 格式化） */
    private static boolean hasKey(String key) {
        return !key.equals(I18n.get(key));
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        ResourceLocation regName = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (regName == null) {
            return;
        }

        if (stack.getItem() instanceof FlyingSwordItem) {
            int mode = FlyingSwordItem.getMode(stack);
            String modeKey = mode == 0 ?
                "tooltip.tinkersnewlife.flying_sword.mode.normal" :
                "tooltip.tinkersnewlife.flying_sword.mode.chase";
            event.getToolTip().add(Component.translatable(modeKey).withStyle(ChatFormatting.GOLD));
            event.getToolTip().add(Component.translatable("tooltip.tinkersnewlife.flying_sword.switch_hint")
                .withStyle(ChatFormatting.GRAY));
            return;
        }

        String path = regName.getPath();

        // 尝试 item 前缀
        String descKey = "item." + TinkersNewlife.MOD_ID + "." + path + ".desc";
        if (hasKey(descKey)) {
            event.getToolTip().add(Component.translatable(descKey).withStyle(ChatFormatting.GRAY));
            return;
        }

        // 尝试 block 前缀（方块物品）
        descKey = "block." + TinkersNewlife.MOD_ID + "." + path + ".desc";
        if (hasKey(descKey)) {
            event.getToolTip().add(Component.translatable(descKey).withStyle(ChatFormatting.GRAY));
        }
    }
}
