package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.util.BossBarRegistry;
import net.minecraft.server.level.ServerBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TNL987 \u2605\u8840\u6761\u515c\u5e95\uff08\u2605\u670d\u52a1\u7aef\u4fa7 \u2713\uff09\uff1a
 * \u2605\u4efb\u4f55\u6a21\u7ec4/\u539f\u7248\u53ea\u8981\u5efa\u4e86 {@code ServerBossEvent} \u2713
 * \u2605\u5c31\u628a\u5b83\u7684 UUID \u8bb0\u8fdb\u6765 \u2713 \u21d2 \u2605"\u6709\u8840\u6761"\u5373\u53ef\u5224 boss \u2713\u3002
 */
@Mixin(ServerBossEvent.class)
public class ServerBossEventMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void tinkersnewlife$registerBossBar(CallbackInfo ci) {
        try {
            BossBarRegistry.mark(((ServerBossEvent) (Object) this).getId());
        } catch (Throwable ignored) {
        }
    }
}
