package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.ClientCurseData;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.item.CurseCoreItem;
import com.mofengbaizhi.tinkersnewlife.content.item.FlyingSwordItem;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;

import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

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

        // ⭐ 咒力核心：只额外提示当前总咒力亲和与咒力值（其余机制见帕秋莉手册/JEI）
        if (stack.getItem() instanceof CurseCoreItem) {
            Player player = event.getEntity();
            if (player != null) {
                int affinity = CursePowerHelper.getCurseAffinity(player);
                ToolStack tool = ToolHelper.getToolStack(stack);
                if (tool != null) {
                    int output = tool.getModifierLevel(Modifiers.CURSE_OUTPUT.getId());
                    int total = tool.getModifierLevel(Modifiers.CURSE_TOTAL.getId());
                    double max = total * (output + affinity / 10.0) * 500.0;
                    int curse = (int) Math.floor(ClientCurseData.getCurse());
                    event.getToolTip().add(Component.translatable("tooltip.tinkersnewlife.curse_affinity", affinity)
                            .withStyle(ChatFormatting.LIGHT_PURPLE));
                    event.getToolTip().add(Component.translatable("tooltip.tinkersnewlife.curse_amount", curse, (int) max)
                            .withStyle(ChatFormatting.GOLD));
                }
            }
            return;
        }

        // ⭐ 咒力亲和（战利品生成的饰品可能携带）
        int affinity = CursePowerHelper.getCurseAffinity(stack);
        if (affinity > 0) {
            event.getToolTip().add(Component.translatable("tooltip.tinkersnewlife.curse_affinity", affinity)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }

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
        boolean descAdded = false;
        if (hasKey(descKey)) {
            event.getToolTip().add(Component.translatable(descKey).withStyle(ChatFormatting.GRAY));
            descAdded = true;
        }

        // 尝试 block 前缀（方块物品）
        if (!descAdded) {
            descKey = "block." + TinkersNewlife.MOD_ID + "." + path + ".desc";
            if (hasKey(descKey)) {
                event.getToolTip().add(Component.translatable(descKey).withStyle(ChatFormatting.GRAY));
            }
        }

        // ⭐ 获取配方提示（不依赖 JEI 的获取方式指引）：item.{modid}.{path}.acquire
        String acquireKey = "item." + TinkersNewlife.MOD_ID + "." + path + ".acquire";
        if (hasKey(acquireKey)) {
            event.getToolTip().add(Component.translatable(acquireKey).withStyle(ChatFormatting.DARK_PURPLE));
        }
    }
}
