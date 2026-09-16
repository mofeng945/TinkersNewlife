package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.modifier.SuperTierMagicModifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 材料「魔金」特性·<b>超位魔法</b>：把"范围 ×5"顺带抬起来的两项<b>还原</b>回去。
 *
 * <p>背景：铁魔法没有范围/持续时间事件 ✗，范围与状态时长都从
 * {@code AbstractSpell#getSpellPower} 派生，所以 {@link SuperTierPowerMixin} 在那里 ×5
 * （范围 ×5 ✓）—— 但伤害、治疗、状态时长会跟着一起 ×5 ✗。三者的还原方式：
 *
 * <table border="1">
 *   <tr><th>项</th><th>还原点</th><th>结果</th></tr>
 *   <tr><td>伤害</td><td>{@code IronSpellsArcaneHandler#onSpellDamage} ÷5</td><td>只吃"50 级"曲线 ✓</td></tr>
 *   <tr><td>治疗</td><td>本类 {@code heal} ÷5（{@code SpellHealEvent} <b>没有 setter</b> ✗ 改不了）</td><td>只吃"50 级"曲线 ✓</td></tr>
 *   <tr><td>状态时长</td><td>本类 {@code addEffect} ×3/5</td><td><b>×3</b>（用户要的倍数）✓</td></tr>
 * </table>
 *
 * <p>"是不是超位魔法施法中"由 {@link SuperTierCastContextMixin} 打的 {@code ThreadLocal} 标记判定
 * （只在这一发法术的结算过程中为真）✓ —— 所以普通药水、别的 mod 的效果、别的法术<b>完全不受影响</b> ✓。
 *
 * <p>⚠ 目标方法名用 <b>SRG 名</b>且 {@code remap = false}：
 * <pre>
 *   m_7292_(MobEffectInstance)  = LivingEntity#addEffect   （反汇编深渊庇佑确认 ✓）
 *   m_5634_(F)                  = LivingEntity#heal        （反汇编高等治疗确认 ✓）
 * </pre>
 * 本模组没启用 Mixin 注解处理器、refmap 是手写的，而游戏里跑的就是 SRG 名 ✓
 * （与 {@code BlockDropsMixin} 同一套做法 ✓）。注入失败也只是退回原值（配置 {@code defaultRequire: 0}）✓。
 */
@Mixin(LivingEntity.class)
public class SuperTierEffectMixin {

    /** 状态时长：×(3/5) → 与"范围 ×5"相乘后净得 ×3 ✓ */
    @ModifyVariable(method = "m_7292_", at = @At("HEAD"), argsOnly = true, remap = false)
    private MobEffectInstance tinkersnewlife$superTierDuration(MobEffectInstance effect) {
        try {
            if (effect == null || !SuperTierMagicModifier.inSuperTierCast()) return effect;
            int duration = effect.getDuration();
            if (duration <= 0) return effect;
            int scaled = Math.max(1, Math.round(duration * SuperTierMagicModifier.EFFECT_DURATION_FACTOR));
            return new MobEffectInstance(effect.getEffect(), scaled, effect.getAmplifier(),
                    effect.isAmbient(), effect.isVisible(), effect.showIcon());
        } catch (Throwable ignored) {
            return effect;
        }
    }

    /** 治疗量：÷5（把 getSpellPower 的 ×5 抵掉） */
    @ModifyVariable(method = "m_5634_", at = @At("HEAD"), argsOnly = true, remap = false)
    private float tinkersnewlife$superTierHeal(float amount) {
        try {
            if (amount <= 0.0F || !SuperTierMagicModifier.inSuperTierCast()) return amount;
            return amount / SuperTierMagicModifier.RANGE_MULTIPLIER;
        } catch (Throwable ignored) {
            return amount;
        }
    }
}
