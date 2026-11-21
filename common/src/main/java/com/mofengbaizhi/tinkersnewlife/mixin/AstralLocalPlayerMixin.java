package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.astral.AstralMovement;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
abstract class AstralLocalPlayerMixin {
    @Inject(method="moveTowardsClosestSpace(DD)V",at=@At("HEAD"),cancellable=true,require=1)
    private void tnl$astralPushOut(double x,double z,CallbackInfo ci) {
        if(AstralMovement.active((LocalPlayer)(Object)this)) ci.cancel();
    }
}
