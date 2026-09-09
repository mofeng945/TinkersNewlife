package com.mofengbaizhi.tinkersnewlife.content.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 禁疗（anti_heal）：莱万汀命中附加的诅咒——持有者的任何治疗被取消（LivingHealEvent 拦截）。
 */
public class AntiHealEffect extends MobEffect {

    public AntiHealEffect() {
        super(MobEffectCategory.HARMFUL, 0x3A0CA3);
    }
}
