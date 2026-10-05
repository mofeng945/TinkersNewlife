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
 * <p>§1030：此前我"化简"掉的两处**已恢复成 TiCEX 原文** ✓ ——
 * {@code CriticalModifierHook.modifyCritical(livingAttacker, false, 1.0f)} 与
 * {@code DamageSourceModifierHook.modifyDamageSource(tool, src)} ✓
 * （两个钩子已按 1:1 搬进 {@code integration/slashblade/hook/} ✓ 并在模组初始化里注册 ✓）。
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
                // §1025 临时调试 ✓：本体的范围/技能结算有没有走到我们这里
                com.mofengbaizhi.tinkersnewlife.integration.slashblade.KatanaDebug.log(
                        "AttackManager.doAttackWith 命中匠魂物品 ✓ 目标=" + target.getType() + " 传入伤害=" + amount);
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
            // §1030 **恢复成 TiCEX 原文** ✓：先问一遍"暴击钩子"（我们已把 CriticalModifierHook 一并搬过来 ✓），
            //   再用它的结论去问 Forge 的暴击事件 ✓ —— 原文：
            //     CriticalModifierHook.CriticalContext criticalContext = CriticalModifierHook.modifyCritical(livingAttacker, false, 1.0f);
            //     ForgeHooks.getCriticalHit(context.getPlayerAttacker(), target, criticalContext.isCritical(), criticalContext.criticalModifier());
            com.mofengbaizhi.tinkersnewlife.integration.slashblade.hook.CriticalModifierHook.CriticalContext criticalContext =
                    com.mofengbaizhi.tinkersnewlife.integration.slashblade.hook.CriticalModifierHook
                            .modifyCritical(livingAttacker, false, 1.0f);
            CriticalHitEvent criticalHitEvent = ForgeHooks.getCriticalHit(
                    context.getPlayerAttacker(), target, criticalContext.isCritical(), criticalContext.criticalModifier());
            if (criticalHitEvent != null) {
                amount = amount + amount * (criticalHitEvent.getDamageModifier() - 1.0f);
            }
        }

        if (forceHit) target.invulnerableTime = 0;

        for (ModifierEntry modifier : tool.getModifierList()) {
            modifier.getHook(ModifierHooks.MELEE_HIT).beforeMeleeHit(tool, modifier, context, amount, 0, 0);
        }

        // §1030 **恢复成 TiCEX 原文** ✓：`target.hurt(DamageSourceModifierHook.modifyDamageSource(tool, src), amount)`
        //   —— 未注册任何实现时该钩子返回原伤害源 ✓，与"直接用 src"在默认配置下等价 ✓，但注册了就能改写 ✓。
        boolean succeed = target.hurt(
                com.mofengbaizhi.tinkersnewlife.integration.slashblade.hook.DamageSourceModifierHook
                        .modifyDamageSource(tool, src),
                amount);

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
