package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.FlyingSwordItem;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public class ModTooltips {

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
        MutableComponent desc = Component.translatable(descKey);
        if (!desc.getString().equals(descKey)) {
            event.getToolTip().add(desc.withStyle(ChatFormatting.GRAY));
            return;
        }

        // 尝试 block 前缀（方块物品）
        descKey = "block." + TinkersNewlife.MOD_ID + "." + path + ".desc";
        desc = Component.translatable(descKey);
        if (!desc.getString().equals(descKey)) {
            event.getToolTip().add(desc.withStyle(ChatFormatting.GRAY));
        }
    }
}