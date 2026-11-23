package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.modifier.HivePrideModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Pseudo
@Mixin(targets = "com.github.alexthe666.iceandfire.entity.util.MyrmexHive", remap = false)
public abstract class MyrmexHiveReputationMixin {
    @Inject(method = "getPlayerReputation", at = @At("RETURN"), cancellable = true, remap = false)
    private void tnl$hivePrideFloor(UUID player, CallbackInfoReturnable<Integer> ci) {
        ci.setReturnValue(HivePrideModifier.effectiveReputation(player, ci.getReturnValueI()));
    }
}
