package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.renderer.FumoMoBlockEntityRenderer;
import com.mofengbaizhi.tinkersnewlife.content.FumoMoDoll;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 客户端渲染注册（§880）：把 fufu 的方块实体渲染器接上 ✓（只在客户端加载 ✓ 专服不会碰 ✗） */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FumoMoRenderers {

    private FumoMoRenderers() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(FumoMoDoll.FUMO_MO_BE.get(), FumoMoBlockEntityRenderer::new);
    }
}
