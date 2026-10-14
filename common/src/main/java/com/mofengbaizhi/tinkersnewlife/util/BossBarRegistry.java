package com.mofengbaizhi.tinkersnewlife.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TNL987 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u5199\u4e0a\u8840\u6761\u515c\u5e95" \u2713
 *
 * <p>\u2605\u4e3a\u4ec0\u4e48\u8981\u81ea\u5df1\u8bb0\uff1a\u2605MC \u91cc\u8840\u6761\uff08{@code BossEvent} \u2713\uff09
 * \u2605\u5728**\u670d\u52a1\u7aef\u6ca1\u6709\u4efb\u4f55\u5168\u5c40\u8868** \u2717\uff0c\u2605\u53ea\u6709**\u6301\u6709\u8005\u624b\u91cc**
 * \u7684\u5f15\u7528 \u2713 \u21d2 \u2605\u60f3\u7528"\u6709\u6ca1\u6709\u8840\u6761"\u5224 boss \u2713
 * \u2605\u5c31\u5fc5\u987b\u81ea\u5df1\u5728\u8840\u6761**\u521b\u5efa\u65f6**\u628a\u5b83\u7684 UUID \u8bb0\u4e0b\u6765 \u2713\u3002
 *
 * <p>\u2605\u7528\u6237\u62c5\u5fc3\uff1a\u2605"\u6709\u6a21\u7ec4\u6ca1\u5199\u6807\u7b7e" \u2713 \u21d2
 * \u2605\u8fd9\u5c31\u662f\u90a3\u6761\u515c\u5e95\uff08\u2605\u6709\u8840\u6761\u5c31\u7b97 boss \u2713\uff09\u3002
 */
public final class BossBarRegistry {

    /** \u2605UUID \u2192 \u6700\u540e\u4e00\u6b21\u770b\u5230\u8840\u6761\u7684\u65f6\u95f4\uff08\u2605\u6beb\u79d2 \u2713\uff09 */
    private static final Map<UUID, Long> SEEN = new ConcurrentHashMap<>();

    /** \u2605\u8d85\u8fc7\u8fd9\u4e48\u4e45\u6ca1\u518d\u770b\u5230\u5c31\u5fd8\u6389\uff08\u2605\u907f\u514d\u6cc4\u6f0f \u2713\uff09 */
    private static final long TTL_MS = 600_000L;

    private BossBarRegistry() {
    }

    /** \u2605\u770b\u5230\u4e00\u6761\u8840\u6761\uff08\u2605\u670d\u52a1\u7aef\u521b\u5efa / \u2605\u5ba2\u6237\u7aef\u6e32\u67d3 \u2713\uff09 */
    public static void mark(UUID id) {
        if (id == null) {
            return;
        }
        SEEN.put(id, System.currentTimeMillis());
        if (SEEN.size() > 256) {
            prune();
        }
    }

    /** \u2605\u8fd9\u4e2a UUID \u6700\u8fd1\u6709\u8fc7\u8840\u6761\u5417 \u2713 */
    public static boolean isBoss(UUID id) {
        if (id == null) {
            return false;
        }
        Long t = SEEN.get(id);
        if (t == null) {
            return false;
        }
        if (System.currentTimeMillis() - t > TTL_MS) {
            SEEN.remove(id);
            return false;
        }
        return true;
    }

    private static void prune() {
        long now = System.currentTimeMillis();
        SEEN.entrySet().removeIf(e -> now - e.getValue() > TTL_MS);
    }
}
