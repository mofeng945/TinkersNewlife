package com.mofengbaizhi.tinkersnewlife.mixin;

import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.util.AttackManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * <b>§1020 拔刀剑 × 匠魂：范围/技能攻击也按匠魂数值结算</b>
 * —— <b>逐行照抄 TiCEX</b> 的 {@code moffy.ticex.mixin.slashblade.AttackManagerMixin} ✓。
 *
 * <p>与原文的**唯一差异**（有意为之 ✓，且已核对等价 ✓）：
 * 原文调了 TiCEX 自家的 {@code CriticalModifierHook}/{@code DamageSourceModifierHook} ✗ ——
 * 查其源码：这两个钩子在**没有任何修饰符实现时的默认行为就是"原样返回"** ✓
 * （{@code modifyCritical(...)} 默认产出 {@code (false, 1.0f)} ✓、{@code modifyDamageSource(...)} 默认返回原伤害源 ✓）
 * ⇒ 我们这边直接用原版 {@code ForgeHooks.getCriticalHit(player, target, false, 1.0F)} ✓
 * 并保持伤害源不变 ✓，与原文在默认配置下**完全等价** ✓（不必把 TiCEX 的 hook 注册体系也搬过来 ✗）。
 *
 * <p>⚠ 拔刀剑不在场时本 mixin 静默不生效 ✓。
 */
@Mixin(value = AttackManager.class, remap = false)
public class KatanaAttackManagerMixin {

    @Inject(at = @At("HEAD"), method = "doAttackWith", cancellable = true, remap = false)
    private static void doAttackWith(
            DamageSource src,
            float amount,
            Entity target,
            boolean forceHit,
            boolean resetHit,
            CallbackInfo cb
    ) {
        if (target instanceof EntityAbstractSummonedSword) return;

        Entity attacker = src.getEntity();
        if (attacker instanceof LivingEntity livingAttacker) {
            ItemStack mainHandStack = livingAttacker.getMainHandItem();
            if (mainHandStack.getItem() instanceof IModifiable) {
                ToolStack tool = ToolStack.from(mainHandStack);
                ToolAttackContext context = ToolAttackContext.attacker(livingAttacker)
                        .hand(InteractionHand.MAIN_HAND)
                        .target(target)
                        .build();

                tnl$dealToolDamage(tool, context, livingAttacker, src, amount, target, forceHit, resetHit);

                cb.cancel();
            }
        }
    }

    @Unique
    private static void tnl$dealToolDamage(
            IToolStackView tool,
            ToolAttackContext context,
            LivingEntity livingAttacker,
            DamageSource src,
            float amount,
            Entity target,
            boolean forceHit,
            boolean resetHit
    ) {
        float amplifier = (float) livingAttacker.getAttributeValue(Attributes.ATTACK_DAMAGE);

        float amplifierTmp = amplifier;

        for (ModifierEntry modifier : tool.getModifierList()) {
            amplifier = modifier
                    .getHook(ModifierHooks.MELEE_DAMAGE)
                    .getMeleeDamage(tool, modifier, context, amplifierTmp, amplifier);
        }

        if (amplifier <= 0) {
            return;
        }

        amount = (amount / (float) livingAttacker.getAttributeValue(Attributes.ATTACK_DAMAGE)) * amplifier;
        if (context.getPlayerAttacker() != null) {
            // 原文：CriticalModifierHook.modifyCritical(livingAttacker, false, 1.0f) ✓ —— 默认实现即 (false, 1.0f) ✓
            CriticalHitEvent criticalHitEvent = ForgeHooks.getCriticalHit(context.getPlayerAttacker(), target, false, 1.0F);
            if (criticalHitEvent != null) {
                amount = amount + amount * (criticalHitEvent.getDamageModifier() - 1.0f);
            }
        }

        if (forceHit) target.invulnerableTime = 0;

        for (ModifierEntry modifier : tool.getModifierList()) {
            modifier.getHook(ModifierHooks.MELEE_HIT).beforeMeleeHit(tool, modifier, context, amount, 0, 0);
        }

        // 原文：DamageSourceModifierHook.modifyDamageSource(tool, src) ✓ —— 默认实现即返回原伤害源 ✓
        boolean succeed = target.hurt(src, amount);

        if (resetHit) target.invulnerableTime = 0;

        if (succeed) {
            for (ModifierEntry modifier : tool.getModifierList()) {
                modifier.getHook(ModifierHooks.MELEE_HIT).afterMeleeHit(tool, modifier, context, amplifierTmp);
            }
        } else {
            for (ModifierEntry modifier : tool.getModifierList()) {
                modifier.getHook(ModifierHooks.MELEE_HIT).failedMeleeHit(tool, modifier, context, amplifierTmp);
            }
        }
    }
}
