package com.mofengbaizhi.tinkersnewlife.client.portal;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * TNL993 \u2605\u2605 \u767d\u8272\u7a7a\u95f4\u4f20\u9001\u95e8\uff1a\u2605"\u76f4\u63a5\u6e32\u67d3\u5bf9\u9762\u4e16\u754c"\u7b2c \u2462 \u6b65 \u2713
 *
 * <p>\u2605\u672c\u6b65\u5148\u628a**\u4e0a\u4e0b\u6587\u94fe\u8def\u5168\u90e8\u63a5\u901a\u5e76\u9a8c\u8bc1** \u2713\uff0c
 * \u2605\u4f46\u2605**\u6682\u4e0d\u9012\u5f52\u8c03\u7528 `LevelRenderer#renderLevel`** \u2717 \u2014\u2014
 * \u2605\u90a3\u4e2a\u65b9\u6cd5\u5728 1.20.1 \u6709 **8 \u4e2a\u53c2\u6570** \u2717\uff0c\u2605\u800c\u4e14\u6e32\u67d3\u524d\u540e\u8981\u6446\u5f88\u591a
 * `RenderSystem` \u72b6\u6001 \u2713 \u21d2 \u2605\u5148\u628a"\u7ed1 FBO / \u6e05\u5c4f / \u8fd8\u539f"\u8dd1\u901a \u2713\uff0c
 * \u2605\u4e0b\u4e00\u6b65\u518d\u628a\u90a3\u4e00\u53e5\u52a0\u4e0a\u53bb \u2713\u3002
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT)
public final class PortalViewRenderer {

    private static long lastLog;
    private static long lastLog2;
    private static long lastLog3;
    private static java.lang.reflect.Method renderLevelMethod;
    /** \u2605\u76ee\u6807\u7ef4\u5ea6\uff08\u2605\u767d\u7a7a\u95f4\u672c\u4f53\uff09\u2014\u2014 \u2605\u7b2c \u2463 \u6b65\u7531\u4f20\u9001\u95e8/\u767d\u7a7a\u95f4\u8bbe\u7f6e \u2713 */
    public static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> DEST =
            net.minecraft.world.level.Level.OVERWORLD;

    private PortalViewRenderer() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            PortalViewLevels.remember(Minecraft.getInstance().level);
        } catch (Throwable ignored) {
        }
    }

    /** \u2605\u6309\u53c2\u6570\u7c7b\u578b\u627e `LevelRenderer#renderLevel` \u2713\uff08\u2605SRG \u540d\u4e0d\u7528\u77e5\u9053 \u2713\uff09 */
    private static boolean invokeRenderLevel(Minecraft mc, RenderLevelStageEvent event,
                                             net.minecraft.client.multiplayer.ClientLevel dest) {
        try {
            if (renderLevelMethod == null) {
                for (java.lang.reflect.Method m : mc.levelRenderer.getClass().getMethods()) {
                    Class<?>[] t = m.getParameterTypes();
                    if (t.length == 8
                            && t[0] == com.mojang.blaze3d.vertex.PoseStack.class
                            && t[1] == float.class
                            && t[2] == long.class
                            && t[3] == boolean.class
                            && t[4] == net.minecraft.client.Camera.class
                            && t[5] == net.minecraft.client.renderer.GameRenderer.class
                            && t[6] == net.minecraft.client.renderer.LightTexture.class
                            && t[7] == org.joml.Matrix4f.class) {
                        renderLevelMethod = m;
                        break;
                    }
                }
                if (renderLevelMethod == null) {
                    TinkersNewlife.LOGGER.warn("TNL994 renderLevel not found by signature");
                    return false;
                }
            }
            net.minecraft.client.renderer.LightTexture lt = lightTexture(mc);
            renderLevelMethod.invoke(mc.levelRenderer, event.getPoseStack(),
                    event.getPartialTick(), net.minecraft.Util.getNanos(), false,
                    event.getCamera(), mc.gameRenderer, lt, event.getProjectionMatrix());
            return true;
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("TNL994 renderLevel FAILED: {}", t.toString());
            return false;
        }
    }

    private static net.minecraft.client.renderer.LightTexture lightTexture(Minecraft mc) {
        try {
            for (java.lang.reflect.Method m : mc.gameRenderer.getClass().getMethods()) {
                if (m.getParameterCount() == 0
                        && m.getReturnType() == net.minecraft.client.renderer.LightTexture.class) {
                    return (net.minecraft.client.renderer.LightTexture) m.invoke(mc.gameRenderer);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (PortalCameraSwap.isActive()) {
            return;                       // \u2605\u9632\u9012\u5f52 \u2713
        }
        Minecraft mc = Minecraft.getInstance();
        try {
            RenderTarget rt = PortalViewTarget.get();
            // \u2605\u2460 \u7ed1\u526f\u7f13\u51b2 \u002b \u6e05\u5c4f \u2713
            rt.bindWrite(true);
            rt.clear(Minecraft.ON_OSX);
            // TNL994 \u2605\u2605 \u7b2c \u2462 \u6b65\u540e\u534a\uff1a\u2605\u771f\u6b63\u9012\u5f52\u6e32\u67d3\u4e00\u6b21"\u5bf9\u9762\u4e16\u754c" \u2713
            //   \u2605\u4e3a\u4ec0\u4e48\u7528**\u53cd\u5c04**\uff1a\u2605\u8fd0\u884c\u671f\u662f SRG \u540d \u2717
            //     \u2605\u800c\u6211\u4e0d\u60f3\u628a\u4e00\u4e2a\u731c\u7684\u540d\u5b57\u5199\u6b7b \u2713
            //     \u21d2 \u2605\u6309**\u53c2\u6570\u7c7b\u578b**\u627e\u5230\u90a3\u4e2a\u65b9\u6cd5 \u2713\uff08\u2605\u627e\u4e0d\u5230\u5c31\u4e0d\u753b \u2713\uff09\u3002
            boolean drew = false;
            net.minecraft.client.multiplayer.ClientLevel dest = null;
            try {
                dest = PortalViewLevels.get(DEST);
            } catch (Throwable ignored) {
            }
            // TNL996 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u6d4b\u4e86"\u4f46 ``TNL992/994`` **\u8fd8\u662f 0 \u884c** \u2717
            //   \u21d2 \u2605\u4e0d\u731c\uff1a\u2605\u76f4\u63a5\u628a"\u76ee\u6807\u7ef4\u5ea6 / \u7f13\u5b58\u5185\u5bb9 / begin \u7ed3\u679c"\u6253\u51fa\u6765 \u2713
            boolean beginOk = false;
            if (dest != null) {
                beginOk = PortalCameraSwap.begin(dest.dimension(), event.getCamera().getPosition(),
                        event.getCamera().getYRot(), event.getCamera().getXRot());
            }
            if (System.currentTimeMillis() - lastLog3 > 5000L) {
                lastLog3 = System.currentTimeMillis();
                TinkersNewlife.LOGGER.warn("TNL996 dest={} cache={} beginOk={}",
                        dest == null ? "null" : dest.dimension().location(),
                        PortalViewLevels.keys(), beginOk);
            }
            if (beginOk) {
                try {
                    drew = PortalViewRenderer.invokeRenderLevel(mc, event, dest);
                } finally {
                    PortalCameraSwap.end();
                }
            }
            // \u2605\u2461 \u56de\u5230\u4e3b\u7f13\u51b2 \u2713
            mc.getMainRenderTarget().bindWrite(true);
            if (drew && System.currentTimeMillis() - lastLog2 > 5000L) {
                lastLog2 = System.currentTimeMillis();
                TinkersNewlife.LOGGER.warn("TNL994 far side rendered into the portal target, dest={}",
                        dest == null ? "null" : dest.dimension().location());
            }
            if (System.currentTimeMillis() - lastLog > 5000L) {
                lastLog = System.currentTimeMillis();
                TinkersNewlife.LOGGER.warn("TNL993 portal view stage ok: target={}x{} tex={} knownLevels={}",
                        rt.width, rt.height, PortalViewTarget.textureId(),
                        PortalViewLevels.has(mc.level == null ? null : mc.level.dimension()));
            }
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("TNL993 portal view stage FAILED: {}", t.toString());
        }
    }
}
