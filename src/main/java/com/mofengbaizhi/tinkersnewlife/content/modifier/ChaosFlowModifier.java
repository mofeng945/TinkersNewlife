package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
 * 铁魔法联动特性·<b>混沌之流</b>（材料「源钻合金」工具自带，<b>无等级</b>）：
 * 你的<b>近战 / 弹射物</b>伤害会被均分为多段 —— <b>1 段物理 + 每学派 1 段</b>
 * （学分数<b>动态</b>读取铁魔法的学派注册表，所以附属模组新加的学派也算 ✓）。
 *
 * <p>例：铁魔法默认 9 个学派 → 共 <b>10 段</b>，每段是总伤害的 1/10 ✓；
 * 每段各按自己的伤害类型结算，因此会被目标<b>各自的抗性</b>分别减免 ✓。
 *
 * <p>结算在 {@code content.modifier.events.ChaosFlowHandler}（{@code LivingHurtEvent} 里
 * 取消原始那一次、再按类型逐段施加 ✓）。
 */
public class ChaosFlowModifier extends Modifier implements TooltipModifierHook {

    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "chaos_flow"));

    /** 效果固定 → 显示名不带等级 */
    @Override
    public Component getDisplayName(int level) {
        return this.getDisplayName();
    }

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        hookBuilder.addHook(this, ModifierHooks.TOOLTIP);
    }

    @Override
    public void addTooltip(IToolStackView tool, ModifierEntry modifier,
                           @Nullable Player player, List<Component> tooltip,
                           TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
        tooltip.add(Component.translatable("modifier.tinkersnewlife.chaos_flow.tip"));
    }

    /** 该物品是否带混沌之流 */
    public static boolean has(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var tool = ToolHelper.getToolStack(stack);
        return ToolHelper.getActiveModifierLevel(tool, ID) > 0;
    }
}
