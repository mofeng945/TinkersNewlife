package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.modifier.events.SoulBodyProjectileHandler;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Override the gravity decision in flight only. Do not change isNoGravity() for entity saves. */
@Mixin(ThrowableProjectile.class)
public abstract class SoulBodyGravityMixin {
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/projectile/ThrowableProjectile;isNoGravity()Z"))
    private boolean tnl$throwGravity(ThrowableProjectile p) {
        return p.isNoGravity() || SoulBodyProjectileHandler.suppressGravity(p);
    }
}
