package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.modifier.RaptorPounceModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.definition.module.weapon.SweepWeaponAttack;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

@Mixin(value = SweepWeaponAttack.class, remap = false)
public abstract class RaptorPounceSweepMixin {
    @Inject(method = "afterMeleeHit", at = @At("HEAD"), cancellable = true, remap = false)
    private void tnl$preventDuplicateSweep(IToolStackView tool, ToolAttackContext context, float damage, CallbackInfo ci) {
        if (!context.getAttacker().level().isClientSide && RaptorPounceModifier.applies(tool, context)) ci.cancel();
    }
}
