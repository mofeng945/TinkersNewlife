package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.astral.AstralProjection;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Skip armor and toughness for the projected player only; don't rewrite the source or deal damage twice. */
@Mixin(LivingEntity.class)
public abstract class AstralArmorMixin {
    @Inject(method="getDamageAfterArmorAbsorb",at=@At("HEAD"),cancellable=true)
    private void tnl$unarmoredSpirit(DamageSource source,float amount,CallbackInfoReturnable<Float> ci){
        if((Object)this instanceof Player p && AstralProjection.active(p))ci.setReturnValue(amount);
    }
}
