package com.mofengbaizhi.tinkersnewlife.client.portal;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;

/**
 * TNL992 \u2605\u2605 \u767d\u8272\u7a7a\u95f4\u4f20\u9001\u95e8\uff1a\u2605"\u76f4\u63a5\u6e32\u67d3\u5bf9\u9762\u4e16\u754c"\u7b2c \u2461 \u6b65 \u2713
 *
 * <p>\u2605\u53c2\u7167\u539f\u578b\uff08\u2605\u6c89\u6d78\u5f0f\u4f20\u9001\u95e8 \u2713\uff09\uff1a
 * \u2605``StaticFieldsSwappingManager.swapAndInvoke(\u7ef4\u5ea6, Runnable)`` \u2713
 * \u002b \u2605``DimensionRenderHelper``\uff08\u2605\u624b\u91cc\u62ff\u7684\u5c31\u662f ``Level world`` \u002b \u2605\u5149\u7167\u8d34\u56fe \u2713\uff09\u3002
 *
 * <p>\u2605\u6211\u4eec\u81ea\u5df1\u7684\u505a\u6cd5\uff1a\u2605\u6e32\u67d3\u524d\u628a"\u5f53\u524d\u5ba2\u6237\u7aef\u4e16\u754c"
 * \u4e34\u65f6\u6362\u6210\u5bf9\u9762\u7ef4\u5ea6 \u2713\uff0c\u2605\u628a\u76f8\u673a\u6446\u5230\u955c\u50cf\u4f4d\u7f6e \u2713\uff0c
 * \u2605\u6e32\u5b8c\u5728 ``finally`` \u91cc**\u4e00\u6b65\u4e0d\u6f0f\u5730\u8fd8\u539f** \u2713\u3002
 * \u2605\u6f0f\u4e00\u6837\u5c31\u662f"\u753b\u9762\u53d8\u9ed1 / \u5b9e\u4f53\u9519\u4f4d" \u2713 \u21d2 \u2605\u6240\u4ee5\u5168\u90e8\u8d70\u4e00\u4e2a\u51fd\u6570\u6210\u5bf9\u8c03\u7528 \u2713\u3002
 *
 * <p>\u2605\u672c\u6b65\u53ea\u642d\u6846\u67b6\uff1a\u7b2c \u2462 \u6b65\uff08\u2605\u9012\u5f52\u6e32\u67d3\u4e00\u6b21\uff09\u624d\u4f1a\u771f\u6b63\u8c03\u7528\u5b83 \u2713\u3002
 */
public final class PortalCameraSwap {

    private static boolean active;
    private static ClientLevel savedLevel;
    private static Object savedRendererLevel;
    private static Vec3 savedCamPos;
    private static float savedYaw;
    private static float savedPitch;
    private static long lastLog;

    private PortalCameraSwap() {
    }

    public static boolean isActive() {
        return active;
    }

    /**
     * \u2605\u8fdb\u5165"\u5bf9\u9762\u4e16\u754c"\u7684\u6e32\u67d3\u4e0a\u4e0b\u6587 \u2713\u3002
     *
     * @return \u2605\u771f\u7684\u6362\u6210\u529f\u4e86\u5417\uff08\u2605\u76ee\u6807\u7ef4\u5ea6\u5ba2\u6237\u7aef\u6ca1\u52a0\u8f7d\u5c31\u8fd4\u56de false \u2713\uff09
     */
    public static boolean begin(ResourceKey<Level> destination, Vec3 camPos, float yaw, float pitch) {
        if (active) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getConnection() == null) {
            return false;
        }
        // \u2605\u76ee\u6807\u7ef4\u5ea6\u7684\u5ba2\u6237\u7aef\u4e16\u754c\uff08\u2605\u6ca1\u52a0\u8f7d\u5c31\u6ca1\u5f97\u753b \u2713\uff09
        ClientLevel dest = null;
        try {
            for (ClientLevel lvl : mc.level.getLevelData() == null
                    ? java.util.Collections.<ClientLevel>emptyList()
                    : java.util.Collections.<ClientLevel>emptyList()) {
                dest = lvl;
            }
        } catch (Throwable ignored) {
        }
        if (dest == null) {
            // \u2605\u5ba2\u6237\u7aef API \u6ca1\u6709"\u6309\u7ef4\u5ea6\u53d6\u5ba2\u6237\u7aef\u4e16\u754c"\u7684\u516c\u5f00\u5165\u53e3 \u2717
            // \u2605\u21d2 \u2605\u7b2c \u2462 \u6b65\u6211\u4eec\u7528**\u81ea\u5df1\u7f13\u5b58**\u7684\u65b9\u5f0f\uff08\u2605\u8fdb\u8fc7\u767d\u7a7a\u95f4\u5c31\u628a\u90a3\u4e2a ClientLevel \u8bb0\u4e0b\u6765 \u2713\uff09
            return false;
        }
        savedLevel = mc.level;
        savedCamPos = mc.gameRenderer.getMainCamera().getPosition();
        savedYaw = mc.gameRenderer.getMainCamera().getYRot();
        savedPitch = mc.gameRenderer.getMainCamera().getXRot();
        savedRendererLevel = readRendererLevel(mc);
        mc.level = dest;
        writeRendererLevel(mc, dest);
        Camera cam = mc.gameRenderer.getMainCamera();
        // TNL992b \u2605\u26051.20.1 \u91cc ``Camera#setPosition/setRotation`` \u662f **protected** \u2717
        //   \u2605\u539f\u578b\u91cc\u5c31\u6709 ``mixin/client/render/MixinCamera`` \u2713 \u21d2 \u2605\u8bf4\u660e IP \u4e5f\u662f\u6539\u76f8\u673a \u2713
        //   \u2605\u6211\u4eec\u5148\u7528**\u53cd\u5c04**\u8c03\u5b83\uff08\u2605\u4e0d\u5f15\u5165\u6df7\u5165 \u2713\uff09\u3002
        setCamera(cam, camPos, yaw, pitch);
        active = true;
        if (System.currentTimeMillis() - lastLog > 5000L) {
            lastLog = System.currentTimeMillis();
            TinkersNewlife.LOGGER.warn("TNL992 portal camera swap -> {} at {}", destination.location(), camPos);
        }
        return true;
    }

    /** \u2605\u65e0\u8bba\u4e2d\u95f4\u53d1\u751f\u4ec0\u4e48\uff0c\u5747\u5fc5\u987b\u8c03\u7528\u5b83\u8fd8\u539f \u2713 */
    public static void end() {
        if (!active) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (savedLevel != null) {
                mc.level = savedLevel;
            }
            writeRendererLevel(mc, savedRendererLevel);
            Camera cam = mc.gameRenderer.getMainCamera();
            setCamera(cam, savedCamPos, savedYaw, savedPitch);
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("TNL992 portal camera restore FAILED: {}", t.toString());
        } finally {
            savedLevel = null;
            savedRendererLevel = null;
            savedCamPos = null;
            active = false;
        }
    }

    private static void setCamera(Camera cam, Vec3 pos, float yaw, float pitch) {
        try {
            java.lang.reflect.Method m = cam.getClass().getDeclaredMethod("setPosition", double.class, double.class, double.class);
            m.setAccessible(true);
            if (pos != null) {
                m.invoke(cam, pos.x, pos.y, pos.z);
            }
        } catch (Throwable ignored) {
        }
        try {
            java.lang.reflect.Method m = cam.getClass().getDeclaredMethod("setRotation", float.class, float.class);
            m.setAccessible(true);
            m.invoke(cam, yaw, pitch);
        } catch (Throwable ignored) {
        }
    }

    private static Object readRendererLevel(Minecraft mc) {
        try {
            Field f = findField(mc.levelRenderer.getClass(), "level");
            if (f == null) {
                return null;
            }
            f.setAccessible(true);
            return f.get(mc.levelRenderer);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void writeRendererLevel(Minecraft mc, Object value) {
        if (value == null) {
            return;
        }
        try {
            Field f = findField(mc.levelRenderer.getClass(), "level");
            if (f != null) {
                f.setAccessible(true);
                f.set(mc.levelRenderer, value);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Field findField(Class<?> c, String name) {
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredField(name);
            } catch (Throwable ignored) {
                c = c.getSuperclass();
            }
        }
        return null;
    }
}
