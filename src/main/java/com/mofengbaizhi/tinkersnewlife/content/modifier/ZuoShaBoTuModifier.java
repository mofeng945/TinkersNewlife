package com.mofengbaizhi.tinkersnewlife.content.modifier;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.display.TooltipModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 坐杀搏徒（领域特性，占用领域槽）
 * <p>
 * 佩戴拥有该特性的咒力核心时，按下绑定按键展开半径为「咒力输出等级×5」格的领域；
 * 领域内生物无法离开，每秒消耗「半径×20」咒力，咒力耗尽自动关闭；
 * 展开期间每 3 秒摇一次奖（70% 小奖 / 29% 大奖 / 1% 特等奖）。
 * 实际逻辑由 {@code DomainHandler} 处理，本类仅作注册与工具提示。
 */
public class ZuoShaBoTuModifier extends Modifier implements TooltipModifierHook {

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        hookBuilder.addHook(this, ModifierHooks.TOOLTIP);
    }

    @Override
    public void addTooltip(IToolStackView tool, ModifierEntry modifier,
                           @Nullable Player player, List<Component> tooltip,
                           TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
        tooltip.add(Component.translatable("modifier.tinkersnewlife.zuosha_botu.description"));
    }
}
