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
 * 你的<b>近战 / 弹射物</b>伤害，每次攻击会<b>随机二选一</b> —— 以<b>物理伤害</b>打出，
 * 或以<b>某一学派的法术伤害</b>打出；<b>只结算一次、总数值不变</b>（<b>不再拆分</b> ✓）。
 *
 * <p>例：这一刀 100 点 ⇒ 要么就是那一下物理 100 点（伤害源与数值原样不动 ✓），
 * 要么被改判成"火焰/冰霜/…某一学派的法术伤害"100 点 ✓（学派从铁魔法学派注册表<b>动态</b>随机取，
 * 所以附属模组新加的学派也算 ✓）。
 *
 * <p><b>三连禁令</b>（用户新口径）：<b>同一"类型"不会连续出现 3 次</b> ✓
 * —— 这里"类型"= <b>物理</b> 或 <b>某一个学派</b>；<b>相邻两次相同是允许的</b> ✓。
 * 也就是说：<b>连出两次之后，下一发强制换类型</b> ✓
 * （{@code 物 物 ⇒ 必出法术}；{@code 火 火 ⇒ 必出非火}：可以是物理，也可以是冰/神圣/…任一其它学派 ✓）。
 * 其余情况仍是原来的随机二选一 ✓。每次攻击<b>只结算一次、总量不变</b> → 这条规则<b>只影响"选哪一种"</b> ✓。
 *
 * <p>结算在 {@code content.modifier.events.ChaosFlowHandler}（{@code LivingHurtEvent} @ LOWEST：
 * 掷骰 → 物理就放行原始那一次；法术就取消原始那一次、换学派的伤害源、以同样的数值重发<b>一次</b> ✓；
 * 三连禁令在同一个类里按<b>施法者 UUID</b> 记"最近两次选了什么"来落实 ✓）。
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
