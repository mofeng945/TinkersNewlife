package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.renderer.FumoMoBlockEntityRenderer;
import com.mofengbaizhi.tinkersnewlife.client.renderer.FumoMoCurioRenderer;
import com.mofengbaizhi.tinkersnewlife.content.FumoMoDoll;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 客户端渲染注册（§880）：把 fufu 的方块实体渲染器接上 ✓（只在客户端加载 ✓ 专服不会碰 ✗） */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FumoMoRenderers {

    private FumoMoRenderers() {}

    /** §894：把 fufu 的**头部渲染器**注册进 Curios（Curios 5.x 的 CuriosRendererRegistry ✓） */
    @SubscribeEvent
    public static void onClientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> top.theillusivec4.curios.api.client.CuriosRendererRegistry.register(
                FumoMoDoll.FUMO_MO_ITEM.get(), FumoMoCurioRenderer::new));
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(FumoMoDoll.FUMO_MO_BE.get(), FumoMoBlockEntityRenderer::new);
    }
}
