package com.mofengbaizhi.tinkersnewlife.mixin;

import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TNL1152 user: "when did I ever write a bar clearing skill" -- right, I was blaming the wrong
 * thing. The bar carries its own visible flag and addPlayer never resets it, so whoever flips it
 * off makes the bar disappear for good. This logs that flip with a stack trace so we can see it.
 */
@Mixin(BossEvent.class)
public class BossEventVisibleMixin {

    @Inject(method = "setVisible", at = @At("HEAD"))
    private void tinkersnewlife$traceVisible(boolean visible, CallbackInfo ci) {
        try {
            if (!visible) {
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                        "TNL1152 setVisible(false) on boss bar {}", ((BossEvent) (Object) this).getName().getString());
                new Throwable("TNL1152 setVisible(false)").printStackTrace();
            }
        } catch (Throwable ignored) {
        }
    }
}
