package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.modifier.HivePrideModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Pseudo
@Mixin(targets = "com.github.alexthe666.iceandfire.entity.util.MyrmexHive", remap = false)
public abstract class MyrmexHiveReputationMixin {
    @Inject(method = "getPlayerReputation", at = @At("RETURN"), cancellable = true, remap = false)
    private void tnl$hivePrideFloor(UUID player, CallbackInfoReturnable<Integer> ci) {
        ci.setReturnValue(HivePrideModifier.effectiveReputation(player, ci.getReturnValueI()));
    }

    /** The actual Myrmex target predicate uses isPlayerReputationLowEnoughToFight; override its queried score too. */
    @Redirect(method = "isPlayerReputationLowEnoughToFight", at = @At(value = "INVOKE",
            target = "Lcom/github/alexthe666/iceandfire/entity/util/MyrmexHive;getPlayerReputation(Ljava/util/UUID;)I"), remap = false)
    private int tnl$hivePrideCombatReputation(@Coerce Object hive, UUID player) {
        return HivePrideModifier.effectiveReputation(player, HivePrideModifier.vanillaReputation(hive, player));
    }
}
