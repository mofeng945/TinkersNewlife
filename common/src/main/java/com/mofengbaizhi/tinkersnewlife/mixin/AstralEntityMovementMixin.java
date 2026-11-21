package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.astral.AstralMovement;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
abstract class AstralEntityMovementMixin {
    @Inject(method="collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",at=@At("HEAD"),cancellable=true,require=1)
    private void tnl$astralMove(Vec3 wanted, CallbackInfoReturnable<Vec3> cir) {
        if((Object)this instanceof Player p && AstralMovement.active(p))
            cir.setReturnValue(AstralMovement.collide(p,p.getBoundingBox(),wanted));
    }
    @Inject(method="isInWall",at=@At("HEAD"),cancellable=true,require=1)
    private void tnl$astralBreathing(CallbackInfoReturnable<Boolean> cir) {
        if((Object)this instanceof Player p && AstralMovement.active(p)) cir.setReturnValue(false);
    }
}
