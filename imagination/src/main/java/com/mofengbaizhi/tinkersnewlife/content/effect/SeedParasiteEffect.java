package com.mofengbaizhi.tinkersnewlife.content.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * 咒种寄生（草木操术·咒种）：
 * 敌人攻击力降低 40%（-0.4 乘算修正，随效果生效/结束自动挂卸）；
 * 咒力总量 / 咒力输出各降一级（不低于 1）、咒力亲和 -60（不低于 0）由
 * {@code CursePowerHelper} 按本效果读取扣减。
 */
public class SeedParasiteEffect extends MobEffect {

    private static final String ATTACK_MODIFIER = UUID.nameUUIDFromBytes(
            "tinkersnewlife.seed_parasite.attack".getBytes()).toString();

    public SeedParasiteEffect(MobEffectCategory category, int color) {
        super(category, color);
        addAttributeModifier(Attributes.ATTACK_DAMAGE, ATTACK_MODIFIER, -0.4D,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}
