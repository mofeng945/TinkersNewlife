package com.mofengbaizhi.tinkersnewlife.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * TNL1001 \u2605\u2605 \u661f\u9668\u60b2\u53f9\uff1a\u2605\u7269\u54c1\u680f\u80cc\u666f\u7070\u767d\u5149\u6548 \u2713
 *
 * <p>\u2605\u53c2\u7167\u65e0\u5c3d\u8d2a\u5a6a \u2713\uff1a\u2605\u661f\u7a7a\u7269\u54c1\u5728\u7269\u54c1\u680f\u91cc\u80cc\u540e\u6709\u4e00\u5c42
 * \u4f1a\u547c\u5438\u7684\u5149\u6655 \u2713\uff08\u2605\u7528\u5b83\u81ea\u5df1\u7684 ``COSMIC_RENDER_TYPE`` \u2713\uff09\u3002
 */
@Mod.EventBusSubscriber(modid = com.mofengbaizhi.tinkersnewlife.TinkersNewlife.MOD_ID,
        value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class StarfallGlowClient {

    private static ShaderInstance glowShader;
    private static RenderType glowType;

    private StarfallGlowClient() {
    }

    public static RenderType glowType() {
        if (glowType == null && glowShader != null) {
            glowType = RenderType.create("tnl_starfall_glow",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(new RenderStateShard.ShaderStateShard(() -> glowShader))
                            .createCompositeState(true));
        }
        return glowType;
    }

    public static boolean ready() {
        return glowShader != null;
    }

    public static void tick() {
        if (glowShader != null) {
            try {
                var u = glowShader.safeGetUniform("EffectTime");
                if (u != null) {
                    u.set((float) (System.currentTimeMillis() % 600000L) / 1000.0F);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    new ResourceLocation(com.mofengbaizhi.tinkersnewlife.TinkersNewlife.MOD_ID, "starfall_glow"),
                    DefaultVertexFormat.NEW_ENTITY), s -> glowShader = s);
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("TNL1001 starfall glow shader register called");
        } catch (Throwable t) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("TNL1001 glow shader FAILED: {}", t.toString());
        }
    }
}
