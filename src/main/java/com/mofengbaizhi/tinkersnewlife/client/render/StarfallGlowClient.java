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
 * TNL1001 ★★ 星陨悲叹：★物品栏背景灰白光效 ✓
 *
 * <p>★参照无尽贪婪 ✓：★星空物品在物品栏里背后有一层
 * 会呼吸的光晕 ✓（★用它自己的 ``COSMIC_RENDER_TYPE`` ✓）。
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
     * TNL1007 ★★ 开关实验：★用户反复说"方形白底没消失" ✓
     * ★日志已证明这层**确实在画** ✓ ⇒ ★现在把它**关掉** ✓
     * ★看白底还在不在 ✓：★不在 ⇒ ★就是我们的光晕（★我继续改它 ✓）；
     * ★还在 ⇒ ★说明根本不是这里 ✓（★我去查别的模组/原版渲染 ✓）。
     */
    public static final boolean ENABLED = true;   // TNL1008 ★经对照实验确认方块就是它 ✓ ⇒ 重开 + 改成细环带 ✓

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
        } catch (Throwable t) {
        }
    }
}
