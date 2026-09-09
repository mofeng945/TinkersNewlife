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

/**
 * 近战特性·莱万汀（神灵金材料近战头自带）：模仿启示录「断曜流光」。
 * 具体结算见 {@code LaevatainHandler}：
 * <ul>
 *   <li>命中改写为 true_pierce（无视伤害减免/无敌帧/无敌效果的真伤，两段式穿透）；</li>
 *   <li>命中受诅咒 → 禁疗（anti_heal）；</li>
 *   <li>概率砍血量上限（MAX_HEALTH 属性下调）；</li>
 *   <li>击中诡厄受限 Boss → 直接拆保护柱；</li>
 *   <li>潜行右键突刺；</li>
 *   <li>持有时给周围仆从上抗性提升；</li>
 *   <li>致死不触发复活/锁血（标记 + GoetyBridge 抑制）。</li>
 * </ul>
 */
public class LaevatainModifier extends Modifier implements TooltipModifierHook {

    public static final ModifierId ID = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "laevatain"));

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        hookBuilder.addHook(this, ModifierHooks.TOOLTIP);
    }

    @Override
    public void addTooltip(IToolStackView tool, ModifierEntry modifier,
                           @Nullable Player player, List<Component> tooltip,
                           TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
        tooltip.add(Component.translatable("modifier.tinkersnewlife.laevatain.tip"));
    }
}
