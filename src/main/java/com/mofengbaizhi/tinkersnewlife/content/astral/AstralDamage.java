package com.mofengbaizhi.tinkersnewlife.content.astral;

import com.mofengbaizhi.tinkersnewlife.content.modifier.ManaShieldTrait;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.LivingEntity;

final class AstralDamage {
    private AstralDamage() {}
    private static final TagKey<DamageType> MAGIC=TagKey.create(Registries.DAMAGE_TYPE,new ResourceLocation("forge","is_magic"));
    static boolean physical(DamageSource source) {
        // A spell can have both a caster and a projectile. Neither alone implies physical damage.
        if(source.is(MAGIC)||source.is(DamageTypes.MAGIC)||source.is(DamageTypes.INDIRECT_MAGIC)||source.is(DamageTypes.DRAGON_BREATH)
                ||source.is(DamageTypes.WITHER)||source.is(DamageTypes.SONIC_BOOM)||source.is(DamageTypes.LIGHTNING_BOLT)
                ||source.is(DamageTypeTags.IS_FIRE)||source.is(DamageTypeTags.IS_FREEZING)||source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))return false;
        if(ManaShieldTrait.isPhysicalDamage(source))return true;
        // Unknown modded spell projectiles must not become immune merely because they fly.
        return source.is(DamageTypeTags.IS_PROJECTILE) && source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow
                && !com.mofengbaizhi.tinkersnewlife.content.modifier.MagicConductionModifier.isMagicDamage(source);
    }
}
