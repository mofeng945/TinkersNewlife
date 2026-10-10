package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * §1295 咒力亲和的**唯一一行**提示（用户口径：只显示"带在该槽位时加多少"，不要六行）。
 * 亲和存在物品自己的 NBT 键上，与玩家属性镜像解耦。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CurseAffinityTooltip {

    private CurseAffinityTooltip() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        try {
            ItemStack stack = event.getItemStack();
            if (stack.isEmpty()) {
                return;
            }
            int amount = CurseAffinityAttribute.getItemAffinity(stack);
            if (amount == 0) {
                return;
            }
            event.getToolTip().add(Component.translatable("tooltip.tinkersnewlife.curse_affinity", amount)
                    .withStyle(ChatFormatting.BLUE));
        } catch (Throwable ignored) {
        }
    }
}