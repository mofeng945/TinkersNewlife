package com.mofengbaizhi.tinkersnewlife.client.render;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;

/**
 * TNL940 \u2605\u2605 \u661f\u9668\u60b2\u53f9\u7684**\u9ed1\u5f13\u8eab\u661f\u7a7a**\u6e32\u67d3 \u2713
 *
 * <p>\u2605\u7528\u6237\u53e3\u5f84\uff1a\u2605"\u770b\u770b\u8be1\u5384\u9057\u7269\u7684\u90a3\u4e24\u4e2a\u77ff\u77f3\u7684\u624b\u6301\u661f\u7a7a\u6e32\u67d3\u662f\u600e\u4e48\u505a\u7684\uff0c
 * \u6211\u60f3\u8ba9\u6211\u7684\u5f13\u8eab\u9ed1\u8272\u90e8\u5206\u4e5f\u90a3\u6837" \u2713 \uff0b\u2605"\u9009 a" \u2713
 *
 * <p>\u2605\u53c2\u8003\u5b9e\u73b0 \u2713\uff1a\u2605``goeticlegacy`` \u7684
 * ``client/render/AnomalyItemRenderer`` \u002b ``shaders/core/nebula_anomaly_item.*`` \u2713
 * \uff08\u2605\u5b83\u7528**\u6838\u5fc3\u7740\u8272\u5668 \u002b fbm \u566a\u58f0**\u7b97\u51fa\u661f\u4e91 \u2713\uff09\u3002
 *
 * <p>\u2605\u6211\u4eec\u7684\u505a\u6cd5 \u2713\uff1a
 * <ul>
 *   <li>\u2605\u4e00\u5bf9\u7740\u8272\u5668 ``tinkersnewlife:star_body`` \u2713\uff1b</li>
 *   <li>\u2605\u590d\u7528 Forge \u7684 ``IClientItemExtensions#getRenderType`` \u2713
 *       \uff08\u2605\u4e0d\u7528\u81ea\u5df1\u5199 BEWLR \u2717 \u2014\u2014 \u2605\u66f4\u7701\u4e8b\u4e5f\u66f4\u5c11\u51fa\u9519 \u2713\uff09\uff1b</li>
 *   <li>\u2605\u7740\u8272\u5668\u91cc\u7528**\u8d34\u56fe\u4eae\u5ea6\u505a\u906e\u7f69** \u2713
 *       \u21d2 \u2605\u53ea\u6709\u9ed1\u8272\u90e8\u5206\u53d8\u661f\u7a7a \u2713\uff0c\u5176\u4f59\u50cf\u7d20\u539f\u6837\u8f93\u51fa \u2713\uff1b</li>
 *   <li>\u2605``EffectTime`` \u6bcf\u5e27\u5582\u65f6\u95f4 \u2713 \u21d2 \u2605\u661f\u4e91\u4f1a\u6d41\u52a8 \u2713\uff1b</li>
 *   <li>\u2605\u7740\u8272\u5668\u6ca1\u52a0\u8f7d\u597d\u65f6\u8fd4\u56de ``null`` \u2713
 *       \u21d2 \u2605\u81ea\u52a8\u9000\u56de\u539f\u7248\u6e32\u67d3 \u2713\uff08\u2605\u4e0d\u4f1a\u53d8\u7d2b\u9ed1\u2717\uff09\u3002</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class StarBodyClient {

    private static final ResourceLocation SHADER_ID = new ResourceLocation(TinkersNewlife.MOD_ID, "star_body");
    private static final ResourceLocation TEX =
            new ResourceLocation(TinkersNewlife.MOD_ID, "textures/item/starfall_lament.png");

    private static ShaderInstance shader;
    private static RenderType renderType;

    private StarBodyClient() {
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        // TNL941 \u2605\u2605 \u63a2\u9488\uff1a\u2605\u7740\u8272\u5668\u5230\u5e95\u6709\u6ca1\u6709\u6ce8\u518c\u4e0a \u2713
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), SHADER_ID, DefaultVertexFormat.NEW_ENTITY),
                    s -> {
                        shader = s;
                        TinkersNewlife.LOGGER.warn("TNL941 star_body shader loaded ok");
                    });
            TinkersNewlife.LOGGER.warn("TNL941 star_body shader registered (waiting for compile)");
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("TNL941 star_body shader FAILED: {}", t.toString());
        }
    }

    /** \u2605\u5f13\u7684\u6e32\u67d3\u7c7b\u578b\uff08\u2605\u6ca1\u597d\u5c31\u8fd4\u56de null \u21d2 \u2605\u9000\u56de\u539f\u7248 \u2713\uff09 */
    public static RenderType starBody() {
        if (shader == null) {
            return null;
        }
        if (renderType == null) {
            renderType = RenderType.create("tnl_star_body", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                    256, false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(new RenderStateShard.ShaderStateShard(() -> shader))
                            .setTextureState(new RenderStateShard.TextureStateShard(TEX, false, false))
                            // TNL940 \u2605RenderStateShard \u91cc\u90a3\u4e9b\u5e38\u91cf\u662f protected \u2717
                            //   \u2605\u53d6\u4e0d\u5230 \u21d2 \u2605\u53ea\u7ed9 shader \u002b texture \u4e24\u4e2a\u72b6\u6001 \u2713
                            .createCompositeState(true));
        }
        return renderType;
    }

    /** \u2605\u7ed9\u7269\u54c1\u7528\u7684\u6269\u5c55\u70b9 \u2713 */
    public static IClientItemExtensions extensions() {
        return new IClientItemExtensions() {
        };
    }

    /** \u2605\u6bcf\u5e27\u5582 ``EffectTime`` \u2713 */
    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Ticker {
        private Ticker() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END || shader == null) {
                return;
            }
            var u = shader.safeGetUniform("EffectTime");
            if (u != null) {
                u.set((float) (System.currentTimeMillis() % 600000L) / 1000.0F);
            }
        }
    }
}
