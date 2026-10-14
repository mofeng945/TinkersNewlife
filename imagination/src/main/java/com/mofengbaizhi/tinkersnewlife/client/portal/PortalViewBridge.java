package com.mofengbaizhi.tinkersnewlife.client.portal;

/**
 * TNL999 \u2605\u2605 \u8de8\u6a21\u5757\u6865\uff1a\u2605`cursed` \u90a3\u8fb9\u6e32\u597d\u7684"\u5bf9\u9762\u4e16\u754c"\u6587\u7406 id \u2713
 * \u2605\u4ece\u8fd9\u91cc\u4f20\u7ed9 `imagination` \u7684\u95e8\u9762\u6e32\u67d3\u5668 \u2713\uff08\u2605\u4e0d\u5f15\u5165\u786c\u4f9d\u8d56 \u2713\uff09\u3002
 */
public final class PortalViewBridge {

    private static volatile int textureId;
    private static volatile boolean valid;

    private PortalViewBridge() {
    }

    public static void publish(int id, boolean ok) {
        textureId = id;
        valid = ok;
    }

    public static int textureId() {
        return textureId;
    }

    public static boolean valid() {
        return valid && textureId > 0;
    }
}
