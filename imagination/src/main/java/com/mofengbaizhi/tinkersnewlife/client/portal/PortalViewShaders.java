package com.mofengbaizhi.tinkersnewlife.client.portal;

import com.mojang.blaze3d.shaders.Shader;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** TNL999 \u2605\u2605 \u6301\u6709\u95e8\u9762\u7740\u8272\u5668\u5b9e\u4f8b \u2713 */
@Mod.EventBusSubscriber(modid = com.mofengbaizhi.tinkersnewlife.TinkersNewlife.MOD_ID,
        value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PortalViewShaders {

    private static ShaderInstance portalView;

    private PortalViewShaders() {
    }

    public static ShaderInstance portalView() {
        return portalView;
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                            new net.minecraft.resources.ResourceLocation(
                                    com.mofengbaizhi.tinkersnewlife.TinkersNewlife.MOD_ID, "white_space_portal"),
                            com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY),
                    s -> portalView = s);
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("TNL999 portal view shader register called");
        } catch (Throwable t) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("TNL999 shader register FAILED: {}", t.toString());
        }
    }
}
