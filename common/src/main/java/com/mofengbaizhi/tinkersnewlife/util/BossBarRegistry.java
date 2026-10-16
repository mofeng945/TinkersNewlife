package com.mofengbaizhi.tinkersnewlife.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TNL987 ★★ 用户：★"写上血条兜底" ✓
 *
 * <p>★为什么要自己记：★MC 里血条（{@code BossEvent} ✓）
 * ★在**服务端没有任何全局表** ✗，★只有**持有者手里**
 * 的引用 ✓ ⇒ ★想用"有没有血条"判 boss ✓
 * ★就必须自己在血条**创建时**把它的 UUID 记下来 ✓。
 *
 * <p>★用户担心：★"有模组没写标签" ✓ ⇒
 * ★这就是那条兜底（★有血条就算 boss ✓）。
 */
public final class BossBarRegistry {

    /** ★UUID → 最后一次看到血条的时间（★毫秒 ✓） */
    private static final Map<UUID, Long> SEEN = new ConcurrentHashMap<>();

    /** ★超过这么久没再看到就忘掉（★避免泄漏 ✓） */
    private static final long TTL_MS = 600_000L;

    private BossBarRegistry() {
    }

    /** ★看到一条血条（★服务端创建 / ★客户端渲染 ✓） */
    public static void mark(UUID id) {
        if (id == null) {
            return;
        }
        SEEN.put(id, System.currentTimeMillis());
        if (SEEN.size() > 256) {
            prune();
        }
    }

    /** ★这个 UUID 最近有过血条吗 ✓ */
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
