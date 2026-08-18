package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TooltipFlag;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.display.TooltipModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import javax.annotation.Nullable;
import java.util.List;

public class StarChildArmorTrait extends Modifier implements TooltipModifierHook {

    public static final ModifierId ID = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "star_child_armor"));
    private static final ResourceLocation TAG_KILLS = new ResourceLocation(TinkersNewlife.MOD_ID, "star_child_kills");

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        hookBuilder.addHook(this, ModifierHooks.TOOLTIP);
    }

    @Override
    public void addTooltip(IToolStackView tool, ModifierEntry modifier,
                           @Nullable Player player, List<Component> tooltip,
                           TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
        // 只在按下 Shift 时显示
        if (tooltipKey != TooltipKey.SHIFT) return;
        if (player == null) return;

        // 只读取当前工具（该盔甲部件）自身的击杀数和等级
        int level = modifier.getLevel();
        int kills = tool.getPersistentData().getInt(TAG_KILLS);

        if (level > 0) {
            float maxBonus = level * 50.0f;
            float bonus = Math.min(kills * 0.5f, maxBonus);
            if (bonus > 0) {
                tooltip.add(Component.translatable(
                        "modifier.tinkersnewlife.star_child_armor.total_bonus",
                        String.format("%.1f", bonus)
                ));
            }
        }
    }
}