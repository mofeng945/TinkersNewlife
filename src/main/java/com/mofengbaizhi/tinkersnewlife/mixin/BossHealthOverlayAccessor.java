package com.mofengbaizhi.tinkersnewlife.mixin;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

/**
 * 读取客户端 Boss 血条表（{@code BossHealthOverlay#events} 是私有 final 字段）。
 *
 * <p>用途：第三方 mod（akaishi）给监守者加的血条用随机 UUID，服务端删不掉；
 * 我们只能在客户端把本地那条移掉 —— 见 {@code ServantBossBarCleaner}。
 */
@Mixin(BossHealthOverlay.class)
public interface BossHealthOverlayAccessor {

    /** 客户端当前的 Boss 血条：UUID → 血条对象 */
    @Accessor("events")
    Map<UUID, LerpingBossEvent> tinkersnewlife$events();
}
