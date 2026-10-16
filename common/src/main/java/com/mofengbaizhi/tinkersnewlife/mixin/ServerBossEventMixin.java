package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.util.BossBarRegistry;
import net.minecraft.server.level.ServerBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TNL987 ★血条兜底（★服务端侧 ✓）：
 * ★任何模组/原版只要建了 {@code ServerBossEvent} ✓
 * ★就把它的 UUID 记进来 ✓ ⇒ ★"有血条"即可判 boss ✓。
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
