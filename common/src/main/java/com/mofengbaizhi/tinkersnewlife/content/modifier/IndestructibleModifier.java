package com.mofengbaizhi.tinkersnewlife.content.modifier;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.behavior.ToolDamageModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import javax.annotation.Nullable;

/**
 * TNL1388 <b>百折不断</b>（杜兰达尔材料特性）—— 用来替换匠魂本体的 {@code tconstruct:unbreakable}。
 *
 * <h2>为什么不能直接用本体「不毁」当材料特性</h2>
 * 本体 {@code unbreakable} 的定义里带了一个 {@code ModifierRequirementsModule}（反编译实证）：
 * <pre>
 * buildModifier(TinkerModifiers.unbreakable)
 *   .addModule(ModifierRequirementsModule.builder()
 *       .requireModifier(ModifierIds.netherite, 1)
 *       .requireModifier(ModifierIds.reinforced, 5) ... )
 *   .addModule(ReduceToolDamageModule.builder().flat(1.0f));
 * </pre>
 * 这个 module 是 <b>ValidateModifierHook</b>：只要工具上有「不毁」且等级在范围内、又**没有**下界合金 + 加固 V，
 * {@code ToolStack#tryValidate()} 就返回错误 ⇒ <b>任何强化/改造配方都会失败</b> ✗
 * （正常玩法里「不毁」本来就是"加固 V + 下界合金之后才解锁的能力"，所以本体这么写没错；
 *  但被当成**材料自带特性**时，工具一造出来就满足"有特性但没前置"⇒ 整把工具无法强化 ✗）
 *
 * <h2>本特性做什么</h2>
 * 只做一件事：挂在 {@link ModifierHooks#TOOL_DAMAGE} 上，每次都把耐久损耗置 0 ⇒ **永不损坏** ✓
 * 不附带任何前置要求 ✓ 也不会写 {@code Unbreakable} 标签（不干扰别的模组判断）✓
 */
public class IndestructibleModifier extends Modifier implements ToolDamageModifierHook {

    public static final ModifierId ID =
            new ModifierId(new ResourceLocation("tinkersnewlife", "indestructible"));

    /** 固定 1 级：显示名不带等级后缀 */
    @Override
    public Component getDisplayName(int level) {
        return getDisplayName();
    }

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        hookBuilder.addHook(this, ModifierHooks.TOOL_DAMAGE);
    }

    /** 老签名（接口里仍是 abstract）—— 必须实现 ✓ */
    @Override
    public int onDamageTool(IToolStackView tool, ModifierEntry modifier, int amount,
                            @Nullable LivingEntity entity) {
        // 0 ⇒ ToolDamageUtil.damage 里 `if (amount <= 0) return false;` 直接结束 ✓ 不消耗耐久 ✓
        return 0;
    }

    /** 新签名（带 stack）也覆写一份，避免多余委托 ✓ */
    @Override
    public int onDamageTool(IToolStackView tool, ModifierEntry modifier, int amount,
                            @Nullable LivingEntity entity, @Nullable ItemStack stack) {
        return 0;
    }
}
