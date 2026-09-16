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
 * 铁魔法联动特性·<b>奥法支配</b>（材料「魔金」通用自带，<b>无等级</b>）：
 * <b>全学派法术强度提升 200%</b>。
 *
 * <h2>为什么只加 {@code SPELL_POWER} 一项</h2>
 * 铁魔法的 {@code AbstractSpell#getSpellPower} 是这么算的（反汇编 ✓）：
 * <pre>
 *   强度 = (基础 + 每级成长 × 等级) × SPELL_POWER 属性 × 该学派法术强度属性 × 配置倍率
 * </pre>
 * 也就是说 {@code SPELL_POWER}（默认 <b>1.0</b>）本身就是<b>对所有学派生效</b>的乘数 ✓ ——
 * 所以 +2.0 就是"全学派 +200%"（1.0 → 3.0 = 三倍）✓。
 * <b>不能</b>再往九个学派属性上各加 +2.0 ✗：那会变成 3.0 × 3.0 = <b>9 倍</b>（重复计算）✗。
 *
 * <p>持有/穿戴期间生效（transient 修饰符，每 10 tick 维持），见
 * {@code content.modifier.events.MagicGoldHandler} ✓。
 */
public class ArcaneDominationModifier extends Modifier implements TooltipModifierHook {

    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "arcane_domination"));

    /** 法术强度加成：+2.0 = +200%（属性默认 1.0） */
    public static final double POWER_BONUS = 2.0D;

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
        tooltip.add(Component.translatable("modifier.tinkersnewlife.arcane_domination.tip",
                String.format("%.0f", POWER_BONUS * 100)));
    }

    // ============================================================
    //  查询工具（结算器用）
    // ============================================================

    /** 该物品是否带奥法支配 */
    public static boolean has(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var tool = ToolHelper.getToolStack(stack);
        return tool != null && tool.getModifierLevel(ID) > 0;
    }

    /** 身上（主手/副手/护甲）是否带着奥法支配 */
    public static boolean wornBy(LivingEntity entity) {
        if (entity == null) return false;
        if (has(entity.getMainHandItem()) || has(entity.getOffhandItem())) return true;
        for (ItemStack armor : entity.getArmorSlots()) {
            if (has(armor)) return true;
        }
        return false;
    }
}
