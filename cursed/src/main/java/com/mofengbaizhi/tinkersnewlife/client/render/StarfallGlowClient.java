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

    /**
     * TNL1007 \u2605\u2605 \u5f00\u5173\u5b9e\u9a8c\uff1a\u2605\u7528\u6237\u53cd\u590d\u8bf4"\u65b9\u5f62\u767d\u5e95\u6ca1\u6d88\u5931" \u2713
     * \u2605\u65e5\u5fd7\u5df2\u8bc1\u660e\u8fd9\u5c42**\u786e\u5b9e\u5728\u753b** \u2713 \u21d2 \u2605\u73b0\u5728\u628a\u5b83**\u5173\u6389** \u2713
     * \u2605\u770b\u767d\u5e95\u8fd8\u5728\u4e0d\u5728 \u2713\uff1a\u2605\u4e0d\u5728 \u21d2 \u2605\u5c31\u662f\u6211\u4eec\u7684\u5149\u6655\uff08\u2605\u6211\u7ee7\u7eed\u6539\u5b83 \u2713\uff09\uff1b
     * \u2605\u8fd8\u5728 \u21d2 \u2605\u8bf4\u660e\u6839\u672c\u4e0d\u662f\u8fd9\u91cc \u2713\uff08\u2605\u6211\u53bb\u67e5\u522b\u7684\u6a21\u7ec4/\u539f\u7248\u6e32\u67d3 \u2713\uff09\u3002
     */
    public static final boolean ENABLED = false;

    public static boolean ready() {
        return ENABLED && glowShader != null;
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
