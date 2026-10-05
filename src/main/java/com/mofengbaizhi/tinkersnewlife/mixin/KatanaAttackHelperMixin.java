package com.mofengbaizhi.tinkersnewlife.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import mods.flammpfeil.slashblade.util.AttackHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * <b>§1020 拔刀剑 × 匠魂：把匠魂的近战修饰符注入本体 {@code AttackHelper#attack}</b>
 * —— <b>逐行照抄 TiCEX</b> 的 {@code moffy.ticex.mixin.slashblade.PlayerAttackHelperMixin}
 * （源码取自 <a href="https://github.com/mofumofumoffy/ticex">github.com/mofumofumoffy/ticex</a> 的 `1.20.1` 分支 ✓）。
 *
 * <p>只改了两处：包名/类名 ✓、把它的 {@code moffy.ticex.mixin.CriticalAccessor}
 * 换成我们自己的 {@link KatanaCriticalAccessor} ✓（访问器本体逐字相同 ✓）。
 * 其余注入点、`@Local`/`@Share` 的用法与循环体都与原版一致 ✓。
 *
 * <p>⚠ 拔刀剑不在场时本 mixin 静默不生效 ✓（`required:false` ＋ `defaultRequire:0` ✓）。
 */
@Mixin(value = AttackHelper.class, remap = false)
public abstract class KatanaAttackHelperMixin {

    @Inject(method = "attack", at = @At(value = "INVOKE", target = "Lmods/flammpfeil/slashblade/util/AttackHelper;calculateTotalDamage(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;FZ)D"))
    private static void setContext(LivingEntity attacker, Entity target, float comboRatio, CallbackInfo ci,
                                   @Local boolean isCritical,
                                   @Share(value = "context") LocalRef<ToolAttackContext> contextRef) {
        ToolAttackContext context = ToolAttackContext.attacker(attacker)
                .hand(InteractionHand.MAIN_HAND)
                .target(target)
                .cooldown(1)
                .build();
        ((KatanaCriticalAccessor) context).setCriticalModifier(isCritical ? 1.5F : 1.0F);
        contextRef.set(context);
    }

    @ModifyExpressionValue(method = "attack", at = @At(value = "INVOKE", target = "Lmods/flammpfeil/slashblade/util/AttackHelper;calculateTotalDamage(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;FZ)D"))
    private static double applyAttackDamage(double damageAmount,
                                            @Local(argsOnly = true) LivingEntity attacker,
                                            @Share(value = "context") LocalRef<ToolAttackContext> contextRef) {
        ToolAttackContext context = contextRef.get();
        ItemStack stack = attacker.getItemInHand(context.getHand());
        if (stack.getItem() instanceof IModifiable) {
            ToolStack tool = ToolStack.from(stack);

            double originalDamage = damageAmount;

            for (ModifierEntry entry : tool.getModifiers()) {
                damageAmount = entry.getHook(ModifierHooks.MELEE_DAMAGE).getMeleeDamage(tool, entry, context, (float) originalDamage, (float) damageAmount);
            }
            // §1025 临时调试 ✓：本体那一击的伤害有没有经过我们（匠魂数值）
            com.mofengbaizhi.tinkersnewlife.integration.slashblade.KatanaDebug.log(
                    "AttackHelper.calculateTotalDamage 经手 ✓ 本体伤害=" + originalDamage + " → 匠魂后=" + damageAmount);
        }

        return damageAmount;
    }

    @ModifyExpressionValue(method = "attack", at = @At(value = "INVOKE", target = "Lmods/flammpfeil/slashblade/util/AttackHelper;calculateKnockback(Lnet/minecraft/world/entity/LivingEntity;)F"))
    private static float applyKnockback(float knockback,
                                        @Local double baseDamage,
                                        @Local(argsOnly = true) LivingEntity attacker,
                                        @Share(value = "context") LocalRef<ToolAttackContext> contextRef) {
        ToolAttackContext context = contextRef.get();
        ItemStack stack = attacker.getItemInHand(context.getHand());
        if (stack.getItem() instanceof IModifiable) {
            ToolStack tool = ToolStack.from(stack);

            float originalKnockback = knockback;
            for (ModifierEntry entry : tool.getModifiers()) {
                knockback = entry.getHook(ModifierHooks.MELEE_HIT).beforeMeleeHit(tool, entry, context, (float) baseDamage, originalKnockback, knockback);
            }
        }

        return knockback;
    }

    @Inject(method = "attack", at = @At(value = "INVOKE", target = "Lmods/flammpfeil/slashblade/util/AttackHelper;handlePostAttackEffects(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;Lmods/flammpfeil/slashblade/util/AttackHelper$FireAspectResult;)V", shift = At.Shift.AFTER))
    private static void applyAttackSuccess(LivingEntity attacker, Entity target, float comboRatio, CallbackInfo ci,
                                           @Local double baseDamage,
                                           @Share(value = "context") LocalRef<ToolAttackContext> contextRef) {
        ToolAttackContext context = contextRef.get();
        ItemStack stack = attacker.getItemInHand(context.getHand());
        if (stack.getItem() instanceof IModifiable) {
            ToolStack tool = ToolStack.from(stack);

            for (ModifierEntry entry : tool.getModifiers()) {
                entry.getHook(ModifierHooks.MELEE_HIT).afterMeleeHit(tool, entry, context, (float) baseDamage);
            }
        }
    }

    @Inject(method = "attack", at = @At(value = "INVOKE", target = "Lmods/flammpfeil/slashblade/util/AttackHelper;handleFailedAttack(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;Lmods/flammpfeil/slashblade/util/AttackHelper$FireAspectResult;)V", shift = At.Shift.AFTER))
    private static void applyAttackFailed(LivingEntity attacker, Entity target, float comboRatio, CallbackInfo ci,
                                          @Local double baseDamage,
                                          @Share(value = "context") LocalRef<ToolAttackContext> contextRef) {
        ToolAttackContext context = contextRef.get();
        ItemStack stack = attacker.getItemInHand(context.getHand());

        if (stack.getItem() instanceof IModifiable) {
            ToolStack tool = ToolStack.from(stack);

            for (ModifierEntry entry : tool.getModifiers()) {
                entry.getHook(ModifierHooks.MELEE_HIT).failedMeleeHit(tool, entry, context, (float) baseDamage);
            }
        }
    }
}
