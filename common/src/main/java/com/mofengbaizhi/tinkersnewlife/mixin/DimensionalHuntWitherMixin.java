package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.modifier.DimensionalHuntModifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WitherBoss.class)
public abstract class DimensionalHuntWitherMixin {
    // This particular isPowered() lookup governs hard-coded arrow immunity, not spawn invulnerability.
    @Redirect(method = "hurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/boss/wither/WitherBoss;isPowered()Z"))
    private boolean tnl$allowDimensionalProjectile(WitherBoss boss, DamageSource source, float amount) {
        return boss.isPowered() && !DimensionalHuntModifier.hasHunt(source);
    }
}
