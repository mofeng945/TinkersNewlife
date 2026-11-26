package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.modifier.PixieFriendModifier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.github.alexthe666.iceandfire.entity.ai.PixieAISteal", remap = false)
public abstract class PixieStealGoalMixin {
    @Shadow(remap = false) private Player temptingPlayer;

    // beta5 picks temptingPlayer during canUse, not in start.
    @Inject(method = {"canUse()Z", "m_8036_()Z", "canContinueToUse()Z", "m_8045_()Z"},
            at = @At("RETURN"), cancellable = true, remap = false)
    private void tnl$rejectFriend(CallbackInfoReturnable<Boolean> ci) {
        if (ci.getReturnValueZ() && PixieFriendModifier.rank(temptingPlayer) > 0) ci.setReturnValue(false);
    }

    // Equipment may have changed since the goal selector last checked continuation.
    @Inject(method = {"tick()V", "m_8037_()V"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void tnl$guardInventory(CallbackInfo ci) {
        if (PixieFriendModifier.rank(temptingPlayer) > 0) ci.cancel();
    }
}
