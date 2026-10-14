package com.mofengbaizhi.tinkersnewlife.client.portal;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;

/**
 * TNL991 \u2605\u2605 \u767d\u8272\u7a7a\u95f4\u4f20\u9001\u95e8\uff1a\u2605"\u76f4\u63a5\u6e32\u67d3\u5bf9\u9762\u4e16\u754c"\u7b2c \u2460 \u6b65 \u2713
 *
 * <p>\u2605\u53c2\u7167\u539f\u578b\uff08\u2605\u6c89\u6d78\u5f0f\u4f20\u9001\u95e8 3.0.7 \u2713\uff09\uff1a\u2605\u5b83\u628a\u5bf9\u9762\u4e16\u754c
 * \u6e32\u67d3\u5230\u4e00\u4e2a**\u526f\u5e27\u7f13\u51b2**\uff08\u2605``SecondaryFrameBuffer`` \u2713\uff09\u518d\u8d34\u56de\u95e8\u9762 \u2713\u3002
 * \u2605\u6211\u4eec\u81ea\u5df1\u505a\u7684\u7b2c\u4e00\u6b65\u5c31\u662f\u628a\u8fd9\u4e2a\u7f13\u51b2\u7ba1\u8d77\u6765 \u2713\u3002
 *
 * <p>\u2605\u8bbe\u8ba1\u53e3\u5f84\uff1a
 * <ul>
 *   <li>\u2605\u5206\u8fa8\u7387\u53d6**\u7a97\u53e3\u7684\u4e00\u534a** \u2713\uff08\u2605\u770b\u7a7f\u5b54\u7528\u4e0d\u7740\u5168\u5206\u8fa8\u7387 \u2713\uff0c\u2605\u7701\u4e00\u534a\u586b\u5145\u7387 \u2713\uff09\uff1b</li>
 *   <li>\u2605\u7a97\u53e3\u53d8\u5927\u53d8\u5c0f\u81ea\u52a8\u91cd\u5efa \u2713\u3002</li>
 * </ul>
 */
public final class PortalViewTarget {

    private static RenderTarget target;
    private static int width;
    private static int height;
    private static long lastLog;

    private PortalViewTarget() {
    }

    /** \u2605\u53d6\uff08\u2605\u6309\u9700\u5efa\uff09\u5bf9\u9762\u4e16\u754c\u7684\u6e32\u67d3\u76ee\u6807 \u2713 */
    public static RenderTarget get() {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        int nw = Math.max(64, window.getWidth() / 2);
        int nh = Math.max(64, window.getHeight() / 2);
        if (target == null || width != nw || height != nh) {
            close();
            target = new TextureTarget(nw, nh, true, Minecraft.ON_OSX);
            target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            width = nw;
            height = nh;
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                    "TNL991 portal view target created {}x{}", nw, nh);
        }
        return target;
    }

    /** \u2605\u95e8\u9762\u8981\u7528\u7684\u6587\u7406\uff08\u2605\u5c31\u662f\u526f\u7f13\u51b2\u7684\u989c\u8272\u9644\u4ef6 \u2713\uff09 */
    public static int textureId() {
        RenderTarget t = get();
        return t.getColorTextureId();
    }

    public static boolean ready() {
        return target != null;
    }

    public static void close() {
        if (target != null) {
            try {
                target.destroyBuffers();
            } catch (Throwable ignored) {
            }
            target = null;
        }
    }
}
