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
                            // TNL1035 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u5f13\u90fd\u5df2\u7ecf\u5b8c\u7f8e\u5b9e\u73b0\u8fc7\u4e00\u7248\u4e86" \u2713
                            //   \u2605\u8bfb\u5b8c\u5f13\u7684 `StarBodyClient` \u5168\u6587\u540e\u53d1\u73b0 \u2713\uff1a
                            //     \u2605\u5b83\u7684 RenderType **\u6709** ``setTextureState(new TextureStateShard(InventoryMenu.BLOCK_ATLAS, false, false))`` \u2713
                            //     \u2605\u800c\u6211\u8fd9\u4e2a**\u53ea\u6709** setShaderState \u2717
                            //   \u21d2 \u2605\u6240\u4ee5 `Sampler0` \u91c7\u7684\u662f"\u4e0a\u4e00\u6279\u6b8b\u7559\u7eb9\u7406" \u2717
                            //     \u2605\u624b\u6301\u91cc\u8bfb\u5230\u65e0\u5173\u7eb9\u7d20 \u21d2 \u2605\u5168\u5224\u4e3a\u9ed1 \u21d2 \u2605\u6574\u7247\u9ed1\u96fe \u21d2 \u2605"\u4e00\u5757\u677f" \u2713\u2713
                            //     \uff08\u2605GUI \u91cc\u78b0\u5de7\u4e0a\u4e00\u6279\u662f\u56fe\u96c6 \u21d2 \u2605\u770b\u7740\u6b63\u5e38 \u2713\uff09
                            //   \u2605\u73b0\u5728 \u2713\uff1a\u2605\u7167\u5f13\u90a3\u6837**\u628a\u7269\u54c1\u56fe\u96c6\u5199\u8fdb RenderType** \u2713
                            .setTextureState(new RenderStateShard.TextureStateShard(
                                    net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS, false, false))
                            .createCompositeState(true));
        }
        return type;
    }

    public static boolean ready() {
        return shader != null;
    }

    private static final net.minecraft.resources.ResourceLocation EYE =
            new net.minecraft.resources.ResourceLocation(
                    com.mofengbaizhi.tinkersnewlife.TinkersNewlife.MOD_ID, "textures/effect/aeonian_eye.png");

    public static void tick() {
        try {
            com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(1, EYE);
        } catch (Throwable ignored) {
        }
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
