package com.mofengbaizhi.tinkersnewlife.client.portal;

import com.mofengbaizhi.tinkersnewlife.content.ModBlockEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** TNL999 \u2605\u628a\u95e8\u9762\u6e32\u67d3\u5668\u6302\u5230 BE \u7c7b\u578b\u4e0a \u2713 */
@Mod.EventBusSubscriber(modid = com.mofengbaizhi.tinkersnewlife.TinkersNewlife.MOD_ID,
        value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PortalViewClientSetup {

    private PortalViewClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.WHITE_SPACE_PORTAL.get(),
                WhiteSpacePortalRenderer::new);
        com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("TNL999 portal view BER registered");
    }
}
