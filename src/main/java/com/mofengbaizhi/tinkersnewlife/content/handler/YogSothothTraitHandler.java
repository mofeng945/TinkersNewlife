package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class YogSothothTraitHandler {

    private static final ModifierId YOG_SOTHOTH_GIFT = new ModifierId(
            new ResourceLocation(TinkersNewlife.MOD_ID, "yog_sothoth_gift")
    );

    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.XpChange event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        // ★ 检查主手，若为空则检查副手
        ItemStack weapon = player.getMainHandItem();
        if (weapon.isEmpty()) {
            weapon = player.getOffhandItem();
        }
        if (weapon.isEmpty()) return;

        // ✅ 使用 ToolHelper 安全获取，避免 "non-modifiable tool" 警告
        ToolStack tool = ToolHelper.getToolStack(weapon);
        if (tool == null) return;

        int level = tool.getModifierLevel(YOG_SOTHOTH_GIFT);
        if (level <= 0) return;

        int original = event.getAmount();
        if (original > 0) {
            // 经验 ×6（原设定）
            event.setAmount(original * 6);
        }
    }
}