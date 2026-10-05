package com.mofengbaizhi.tinkersnewlife.mixin;

import mods.flammpfeil.slashblade.util.AttackHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * <b>§1020 拔刀剑 × 匠魂：把匠魂的"近战伤害"修饰符注入拔刀剑自己的结算里</b>
 * （照 <b>TiCEX</b> 的 {@code PlayerAttackHelperMixin} 移植 ✓）。
 *
 * <p>为什么需要它：本刀的伤害最终由**拔刀剑自己的** {@code AttackHelper#attack} 结算 ✓
 * （真·左键 → AttackManager → AttackHelper ✓）。这样"原版机制"（否决语义、连段、技能、
 * 击退与手感）都归本体 ✓，但匠魂的"近战伤害"类修饰符（锋利/强化等 ✓）就没人过问了 ✗
 * ⇒ 在这里拦一次：把本体算出的伤害再交给匠魂的 {@code MELEE_DAMAGE} 钩子过一遍 ✓。
 *
 * <p>为什么用 {@code @Redirect} 而不是 MixinExtras 的 {@code @ModifyExpressionValue} ✗：
 * 本仓的 mixin 一直只用**原生注解**（且 refmap 是手写的 ✓，见 build.gradle ✓），
 * 不引入额外依赖更稳 ✓ —— 效果等价：拦下 {@code calculateTotalDamage} 这一次调用，
 * 先取本体的原值 ✓，再串一遍匠魂修饰符 ✓，把结果还回去 ✓。
 *
 * <p>⚠ 目标类来自拔刀剑（`compileOnly` 依赖 ✓）。mixin 配置里 {@code required:false} ＋
 * {@code defaultRequire:0} ✓ ⇒ 拔刀剑不在场时本 mixin 静默不生效 ✓，不会影响别的整合包 ✓。
 */
@Mixin(value = AttackHelper.class, remap = false)
public class KatanaAttackHelperMixin {

    /**
     * 拦下 {@code AttackHelper#calculateTotalDamage(...)} 的调用 ✓。
     *
     * @param attacker   攻击者（本体传入 ✓）
     * @param target     被打的目标 ✓
     * @param damage     本体传入的基础伤害系数 ✓
     * @param isCritical 本体判定的暴击 ✓
     * @return 过完匠魂"近战伤害"修饰符之后的伤害 ✓（非匠魂物品则原样返回 ✓）
     */
    @Redirect(
            method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lmods/flammpfeil/slashblade/util/AttackHelper;calculateTotalDamage(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;FZ)D"),
            remap = false)
    private static double tnl$applyMeleeDamage(LivingEntity attacker, Entity target, float damage, boolean isCritical) {
        // 先取本体自己算出来的伤害 ✓（这里调的是原方法 ✓，不会递归回本 mixin ✓）
        double original = AttackHelper.calculateTotalDamage(attacker, target, damage, isCritical);

        ItemStack stack = attacker.getMainHandItem();
        if (!(stack.getItem() instanceof IModifiable)) {
            return original;
        }

        ToolStack tool = ToolStack.from(stack);
        ToolAttackContext context = ToolAttackContext.attacker(attacker)
                .hand(InteractionHand.MAIN_HAND)
                .target(target)
                .cooldown(1.0F)
                .build();

        double result = original;
        for (ModifierEntry entry : tool.getModifiers()) {
            // getMeleeDamage(tool, entry, context, originalDamage, currentDamage) ✓ —— 与 TiCEX 的调用完全一致 ✓
            result = entry.getHook(ModifierHooks.MELEE_DAMAGE)
                    .getMeleeDamage(tool, entry, context, (float) original, (float) result);
        }
        return result;
    }
}
