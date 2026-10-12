package com.mofengbaizhi.tinkersnewlife.content.entity.ktulu;

/**
 * TNL855 \u955c\u50cf B\uff08\u2605\u4e0e {@link a} \u4e92\u4e3a\u955c\u50cf \u2713\uff09
 *
 * <p>\u6bcf\u4e2a\u503c\u90fd\u662f **MAC \u6807\u8bb0\u7684\u5f71\u5b50\u503c** \u2713\uff1a
 * \u5b58\u7684\u662f\u63a9\u7801\u503c ``V`` \u002b \u6821\u9a8c\u503c ``T`` \u002b \u8ba1\u6570\u5668 ``C`` \u2713\u3002
 * \u2605\u9006\u5411\u8005\u53ea\u6539 ``V``\uff08\u6216\u53ea\u6539\u4e00\u8fb9\u7684\u6570\u7ec4\uff09\u2717
 * \u21d2 \u6821\u9a8c\u503c\u5bf9\u4e0d\u4e0a \u2717 \u21d2 **\u5f53\u573a\u5224\u5047** \u2713\u3002
 */
final class b {

    static final long SALT = 0x2545F4914F6CDD1DL;
    static final int NS = 32;

    private static final long[] V = new long[NS];   // \u2605\u63a9\u7801\u8840\u91cf \u2713
    private static final long[] T = new long[NS];   // \u2605MAC \u6821\u9a8c \u2713
    private static final int[] C = new int[NS];     // \u2605\u8ba1\u6570\u5668 \u2713
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

    /** \u2605\u5199\u5165\uff08\u2605\u540c\u65f6\u91cd\u7b97 MAC \u2713\uff09 */
    static void put(int s, long v) {
        if (s < 0 || s >= NS) {
            return;
        }
        int c = ++CNT[s];
        V[s] = v ^ key(c, SALT);
        T[s] = chk(v, c, SALT);
        C[s] = c;
    }

    /** \u2605\u8bfb\u53d6\uff08\u2605MAC \u4e0d\u5bf9\u5c31\u8fd4\u56de\u54e8\u5175\u503c \u2713\uff09 */
    static long get(int s) {
        if (s < 0 || s >= NS) {
            return Long.MIN_VALUE;
        }
        int c = C[s];
        long v = V[s] ^ key(c, SALT);
        if (T[s] != chk(v, c, SALT) || v < 0L || v > 100000000L) {
            return Long.MIN_VALUE;   // \u2605\u88ab\u6539\u8fc7 \u2717
        }
        return v;
    }
}
