package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.handler.HeatLoverHandler;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 喜热（§980）：**盔甲 · 头盔「金睛」—— 熔岩下视野清晰**（客户端）。
 *
 * <p>做法照「海王之力」的水下清晰（§969）与**原模组自己的做法**一致 ✓
 * （熔岩钓鱼就是挂 {@code ViewportEvent.RenderFog} ✓ 实读它的 {@code ForgeEventClient#onFogRender} ✓）：
 * 戴着头盔那一件（带「喜热」）且**眼睛泡在熔岩里**时，把熔岩雾的远平面拉远、近平面推近并取消原版那套 ✓。
 *
 * <p>⚠ 只在客户端注册 ✓（{@code value = Dist.CLIENT} ✓）＋ 类里不碰任何服务端专用 API ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT)
public final class HeatLoverClientHandler {

    private HeatLoverClientHandler() {}

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        try {
            if (!(event.getCamera().getEntity() instanceof Player player)) return;
            if (!player.isEyeInFluid(FluidTags.LAVA)) return;
            if (!HeatLoverHandler.hasTrait(player.getItemBySlot(EquipmentSlot.HEAD))) return;
            // 熔岩下 ⇒ 看得又远又清楚 ✓（数值可调 ✓ 只影响"泡在熔岩里"这一种情况 ✓）
            event.setNearPlaneDistance(0.05F);
            event.setFarPlaneDistance(Math.max(event.getFarPlaneDistance(), 96.0F));
            event.setCanceled(true);
        } catch (Throwable ignored) {
            // 任何异常都不该影响渲染 ✓ 直接放行原版雾 ✓
        }
    }
}
