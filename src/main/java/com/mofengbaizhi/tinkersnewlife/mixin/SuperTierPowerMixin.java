package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.modifier.SuperTierMagicModifier;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 材料「魔金」特性·<b>超位魔法</b>：刻印在超位魔法工具里的法术，<b>范围 ×5</b>。
 *
 * <p><b>为什么拦这里</b>：铁魔法没有任何"范围/持续时间"事件 ✗，
 * 而其范围与状态时长都从 {@code AbstractSpell#getSpellPower(int, Entity)} 派生
 * （反汇编：深渊庇佑 {@code onCast} 里就是 {@code new MobEffectInstance(effect, (int) getSpellPower(...) * 20, ...)} ✓）。
 * 所以把这一处 ×5 就等于范围 ×5 ✓ —— 但它同时也会把<b>伤害、治疗、状态时长</b>一起 ×5，
 * 这三项分别在下面两处还原：
 * <ul>
 *   <li>伤害 / 治疗 → {@code IronSpellsArcaneHandler} 里 <b>÷5</b> ✓；</li>
 *   <li>状态时长 → {@code mixin.SpellDurationMixin} 再 <b>×3/5</b> → 净得 <b>×3</b> ✓。</li>
 * </ul>
 *
 * <p>⚠ 字符串 target（不需要把铁魔法加进编译期依赖 ✓）；铁魔法不在场时只是"未应用" ✓。
 */
@Mixin(targets = "io.redspace.ironsspellbooks.api.spells.AbstractSpell")
public class SuperTierPowerMixin {

    @Inject(method = "getSpellPower", at = @At("RETURN"), cancellable = true)
    private void tinkersnewlife$superTierPower(int spellLevel, Entity caster, CallbackInfoReturnable<Float> cir) {
        try {
            float mult = SuperTierMagicModifier.powerMultiplier(this, caster);
            if (mult != 1.0F) {
                cir.setReturnValue(cir.getReturnValueF() * mult);
            }
        } catch (Throwable ignored) {
            // 出错就保持原值，绝不影响正常施法
        }
    }
}
