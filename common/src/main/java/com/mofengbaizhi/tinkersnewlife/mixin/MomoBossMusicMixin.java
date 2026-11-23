package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.client.MomoBossMusic;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.Music;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MomoBossMusicMixin {
    @Inject(method = "getSituationalMusic", at = @At("HEAD"), cancellable = true)
    private void tnl$momoMusic(CallbackInfoReturnable<Music> callback) {
        Music bossMusic = MomoBossMusic.forCurrentFight((Minecraft) (Object) this);
        if (bossMusic != null) callback.setReturnValue(bossMusic);
    }
}
