package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.handler.SeaKingsPowerHandler;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 海王之力（§960）：**盔甲 · 水中视野更清晰**（§969 · 客户端）。
 *
 * <p>做法：挂在 Forge 的 {@link ViewportEvent.RenderFog} ✓ —— 玩家戴着带「海王之力」的盔甲
 * 且**眼睛泡在水里**时，把水下雾的**远平面拉远、近平面推到很近** ✓ 并取消原版那套 ✓
 * ⇒ 水里看得又远又清楚 ✓（不依赖雾模式枚举 ✓ 直接判 {@code isEyeInFluid(WATER)} ✓ 更稳 ✓）。
 *
 * <p>⚠ 只在客户端注册 ✓（{@code value = Dist.CLIENT} ✓）＋ 类里不碰任何服务端专用 API ✓
 * （本仓 §801 那条"公共代码不许引客户端类"的反向同理 ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT)
public final class SeaKingsClientHandler {

    private SeaKingsClientHandler() {}

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        try {
            if (!(event.getCamera().getEntity() instanceof Player player)) return;
            if (!player.isEyeInFluid(FluidTags.WATER)) return;
            if (!SeaKingsPowerHandler.wearsOrHolds(player)) return;
            // 水里 ⇒ 看得又远又清楚 ✓（数值可调 ✓ 只影响"水里"这一种情况 ✓）
            event.setNearPlaneDistance(0.05F);
            event.setFarPlaneDistance(Math.max(event.getFarPlaneDistance(), 96.0F));
            event.setCanceled(true);
        } catch (Throwable ignored) {
            // 任何异常都不该影响渲染 ✓ 直接放行原版雾 ✓
        }
    }
}