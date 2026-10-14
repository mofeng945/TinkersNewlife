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

/** TNL1024 \u2605\u2605 \u6052\u6b63\u79e9\u5668\u7684"\u865a\u7a7a\u955c"\u7740\u8272\u5668\uff08\u2605\u9ed1\u5904\u51fa\u9ed1\u96fe\u002b\u773c\u775b \u2713\uff09 */
@Mod.EventBusSubscriber(modid = com.mofengbaizhi.tinkersnewlife.TinkersNewlife.MOD_ID,
        value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AeonianOrderClient {

    private static ShaderInstance shader;
    private static RenderType type;

    private AeonianOrderClient() {
    }

    public static RenderType orderType() {
        if (type == null && shader != null) {
            type = RenderType.create("tnl_aeonian_order",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(new RenderStateShard.ShaderStateShard(() -> shader))
                            .createCompositeState(true));
        }
        return type;
    }

    public static boolean ready() {
        return shader != null;
    }

    public static void tick() {
        if (shader != null) {
            try {
                var u = shader.safeGetUniform("EffectTime");
                if (u != null) {
                    u.set((float) (System.currentTimeMillis() % 600000L) / 1000.0F);
                }
                var m = shader.safeGetUniform("MaskThreshold");
                if (m != null) {
                    m.set(0.22F);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    new ResourceLocation(com.mofengbaizhi.tinkersnewlife.TinkersNewlife.MOD_ID, "aeonian_order"),
                    DefaultVertexFormat.NEW_ENTITY), s -> shader = s);
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("TNL1024 aeonian order shader register called");
        } catch (Throwable t) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("TNL1024 shader FAILED: {}", t.toString());
        }
    }
}
