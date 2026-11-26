package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.modifier.SoulStrikeModifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class SoulStrikeSourceMixin {
    // Work on the incoming amount before Forge hurt modifiers and vanilla armor calculations.
    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private float tnl$starAegis(float incoming) {
        return com.mofengbaizhi.tinkersnewlife.content.modifier.StarAegisModifier.mitigate((LivingEntity)(Object)this, incoming);
    }

    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private DamageSource tnl$replaceSoulStrikeSource(DamageSource original) {
        return SoulStrikeModifier.convert((LivingEntity)(Object)this, original);
    }
}
