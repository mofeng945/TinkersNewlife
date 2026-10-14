package com.mofengbaizhi.tinkersnewlife.client.portal;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TNL993 \u2605\u2605 \u767d\u8272\u7a7a\u95f4\u4f20\u9001\u95e8\uff1a\u2605"\u6309\u7ef4\u5ea6\u8bb0\u4f4f\u5ba2\u6237\u7aef\u4e16\u754c" \u2713
 *
 * <p>\u2605\u4e3a\u4ec0\u4e48\u9700\u8981\u5b83\uff1a\u2605\u539f\u7248\u5ba2\u6237\u7aef**\u6ca1\u6709**
 * "\u6309\u7ef4\u5ea6\u53d6\u5ba2\u6237\u7aef\u4e16\u754c"\u7684\u516c\u5f00\u5165\u53e3 \u2717\uff0c
 * \u2605\u800c\u6e32\u67d3\u5bf9\u9762\u5c31\u5fc5\u987b\u62ff\u5230\u90a3\u4e2a {@code ClientLevel} \u2713\u3002
 *
 * <p>\u2605\u505a\u6cd5\uff1a\u2605\u51e1\u662f\u73a9\u5bb6\u8fdb\u8fc7\u7684\u7ef4\u5ea6 \u2713\uff0c\u2605\u6211\u4eec\u5c31\u628a\u5b83\u7684
 * {@code ClientLevel} \u8bb0\u4e0b\u6765 \u2713\uff08\u2605\u539f\u578b\u91cc\u5bf9\u5e94 ``DimensionRenderHelper`` \u2713\uff09\u3002
 * \u2605\u8bb0\u4e0d\u5230\u5c31\u4e0d\u753b \u2713 \u2014\u2014 \u2605\u5b81\u53ef\u4e0d\u753b\uff0c\u4e5f\u4e0d\u753b\u9519 \u2713\u3002
 */
public final class PortalViewLevels {

    private static final Map<ResourceKey<Level>, ClientLevel> KNOWN = new ConcurrentHashMap<>();

    private PortalViewLevels() {
    }

    /** \u2605\u6bcf tick \u8c03\u4e00\u6b21\uff1a\u628a"\u5f53\u524d\u5ba2\u6237\u7aef\u4e16\u754c"\u8bb0\u8fdb\u6765 \u2713 */
    public static void remember(ClientLevel level) {
        if (level != null) {
            KNOWN.put(level.dimension(), level);
        }
    }

    /** \u2605\u53d6\u67d0\u4e2a\u7ef4\u5ea6\u7684\u5ba2\u6237\u7aef\u4e16\u754c\uff08\u2605\u6ca1\u8fdb\u8fc7\u5c31\u662f null \u2713\uff09 */
    public static ClientLevel get(ResourceKey<Level> dimension) {
        return dimension == null ? null : KNOWN.get(dimension);
    }

    /** TNL996 \u2605\u8c03\u8bd5\u7528\uff1a\u2605\u5df2\u8bb0\u4e0b\u7684\u7ef4\u5ea6\u6e05\u5355 \u2713 */
    public static java.util.Set<ResourceKey<Level>> keys() {
        return KNOWN.keySet();
    }

    /** \u2605\u6709\u6ca1\u6709\u8bb0\u4e0b\u8fc7\u8fd9\u4e2a\u7ef4\u5ea6 \u2713 */
    public static boolean has(ResourceKey<Level> dimension) {
        return get(dimension) != null;
    }
}
