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

/** TNL1024 ★★ 恒正秩器的"虚空镜"着色器（★黑处出黑雾+眼睛 ✓） */
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
                            // TNL1035 ★★ 用户：★"弓都已经完美实现过一版了" ✓
                            //   ★读完弓的 `StarBodyClient` 全文后发现 ✓：
                            //     ★它的 RenderType **有** ``setTextureState(new TextureStateShard(InventoryMenu.BLOCK_ATLAS, false, false))`` ✓
                            //     ★而我这个**只有** setShaderState ✗
                            //   ⇒ ★所以 `Sampler0` 采的是"上一批残留纹理" ✗
                            //     ★手持里读到无关纹素 ⇒ ★全判为黑 ⇒ ★整片黑雾 ⇒ ★"一块板" ✓✓
                            //     （★GUI 里碰巧上一批是图集 ⇒ ★看着正常 ✓）
                            //   ★现在 ✓：★照弓那样**把物品图集写进 RenderType** ✓
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
