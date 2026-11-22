package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.modifier.DragonSlayerShield;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only admits eligible breath to vanilla hurt's existing Forge ShieldBlockEvent; TCon still owns amount, angle and wear. */
@Mixin(LivingEntity.class)
abstract class DragonSlayerShieldMixin {
    @Inject(method="isDamageSourceBlocked", at=@At("RETURN"), cancellable=true, require=1)
    private void tnl$blockBreath(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && DragonSlayerShield.canBlockBypassedBreath((LivingEntity)(Object)this, source)) {
            cir.setReturnValue(true);
        }
    }
}
