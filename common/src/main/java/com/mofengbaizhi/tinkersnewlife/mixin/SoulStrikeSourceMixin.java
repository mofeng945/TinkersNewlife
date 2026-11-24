package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.modifier.SoulStrikeModifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class SoulStrikeSourceMixin {
    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private DamageSource tnl$replaceSoulStrikeSource(DamageSource original) {
        return SoulStrikeModifier.convert((LivingEntity)(Object)this, original);
    }
}
