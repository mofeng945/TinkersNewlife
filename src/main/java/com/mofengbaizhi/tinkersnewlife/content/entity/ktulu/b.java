package com.mofengbaizhi.tinkersnewlife.content.entity.ktulu;

/**
 * TNL855 镜像 B（★与 {@link a} 互为镜像 ✓）
 *
 * <p>每个值都是 **MAC 标记的影子值** ✓：
 * 存的是掩码值 ``V`` + 校验值 ``T`` + 计数器 ``C`` ✓。
 * ★逆向者只改 ``V``（或只改一边的数组）✗
 * ⇒ 校验值对不上 ✗ ⇒ **当场判假** ✓。
 */
final class b {

    static final long SALT = 0x2545F4914F6CDD1DL;
    static final int NS = 32;

    private static final long[] V = new long[NS];   // ★掩码血量 ✓
    private static final long[] T = new long[NS];   // ★MAC 校验 ✓
    private static final int[] C = new int[NS];     // ★计数器 ✓
    private static final int[] CNT = new int[NS];

    private b() {
    }

    private static long key(int c, long salt) {
        long x = salt + (long) c * 0x9E3779B97F4A7C15L;
        x ^= x >>> 33;
        x *= 0xFF51AFD7ED558CCDL;
        x ^= x >>> 33;
        return x;
    }

    private static long chk(long v, int c, long salt) {
        long x = v + key(c, salt);
        x ^= x >>> 27;
        x *= 0x94D049BB133111EBL;
        x ^= x >>> 31;
        return x;
    }

    /** ★写入（★同时重算 MAC ✓） */
    static void put(int s, long v) {
        if (s < 0 || s >= NS) {
            return;
        }
        int c = ++CNT[s];
        V[s] = v ^ key(c, SALT);
        T[s] = chk(v, c, SALT);
        C[s] = c;
    }

    /** ★读取（★MAC 不对就返回哨兵值 ✓） */
    static long get(int s) {
        if (s < 0 || s >= NS) {
            return Long.MIN_VALUE;
        }
        int c = C[s];
        long v = V[s] ^ key(c, SALT);
        if (T[s] != chk(v, c, SALT) || v < 0L || v > 100000000L) {
            return Long.MIN_VALUE;   // ★被改过 ✗
        }
        return v;
    }
}
