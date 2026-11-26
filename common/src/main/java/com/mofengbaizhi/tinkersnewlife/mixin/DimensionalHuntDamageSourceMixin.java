package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.modifier.DimensionalHuntModifier;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DamageSource.class)
public abstract class DimensionalHuntDamageSourceMixin {
    @Inject(method = "is(Lnet/minecraft/tags/TagKey;)Z", at = @At("RETURN"), cancellable = true)
    private void tnl$hideProjectileTag(TagKey<DamageType> tag, CallbackInfoReturnable<Boolean> ci) {
        if (tag.equals(DamageTypeTags.IS_PROJECTILE) && ci.getReturnValueZ()
                && DimensionalHuntModifier.hasHunt((DamageSource)(Object)this)) {
            ci.setReturnValue(false);
        }
    }
}
