package com.mofengbaizhi.tinkersnewlife.content.entity.ktulu;

import net.minecraft.world.entity.Mob;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** TNL853 obfuscated vitals kernel. */
public final class a {

    private static final String A0 = "A17283699FC636FA6908EAD41FAF5CFF4DF22599FC262378ABB475A537B81565";
    private static final long A1 = (0x544E4C31L << 8) | 5L;
    /** TNL856 \u2605DLL \u5185\u90e8\u6307\u7eb9\u8868\u7684 CRC32\uff08\u2605\u6869 DLL/\u6539\u8fc7\u7684 DLL \u5bf9\u4e0d\u4e0a \u2713\uff09 */
    private static final long FP0 = 4020521105L;
    /** TNL861 \u2605DLL **\u4ee3\u7801\u6bb5(.text)** \u7684 CRC32\uff08\u2605\u6539\u4ee3\u7801\u6bb5\u5c31\u5bf9\u4e0d\u4e0a \u2713\uff09 */
    private static final long TC0 = 2270635665L;
    /** TNL856 \u2605\u4f1a\u8bdd\u7ea7\u968f\u673a\u76d0\uff08\u2605\u6bcf\u5c40\u4e0d\u540c \u2712 \u53cd\u5c04\u8bfb\u4e86\u4e5f\u96be\u590d\u73b0\uff09 */
    private static final long SS = new java.security.SecureRandom().nextLong();
    /** TNL856 \u2605\u7be1\u6539\u6807\u5fd7\uff08\u2605\u547d\u4e2d\u540e\u8840\u6761\u9690\u85cf \u002b \u514d\u4f24 \u2713\uff09 */
    private static volatile boolean TP = false;
    private static final int NS = 32;
    public static final float C0 = 0.06F;
    public static final int V0 = 30;

    private static volatile boolean N = false;
    private static long L = -1;
    /** TNL862 \u2605\u540e\u53f0\u81ea\u68c0\uff1a**\u4e0d\u5728\u4e3b\u7ebf\u7a0b\u505a SHA-256/CRC** \u2713\uff08\u2605\u7528\u6237\u53cd\u9988\uff1a\u4f1a\u6389\u5e27 \u2717\uff09 */
    private static final java.util.concurrent.atomic.AtomicBoolean BUSY =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static volatile long LAST_MS = 0L;
    /** \u2605\u62bd\u6837\u95f4\u9694\uff08ms\uff09\uff1a\u2605\u968f\u673a\u5316 \u2713\uff08\u2605\u4e0d\u7ed9\u5916\u6302\u6293\u8282\u594f \u2713\uff09 */
    private static final long GAP_MIN = 20000L;
    private static final long GAP_RANGE = 20000L;

    /* TNL855 \u2605\u955c\u50cf A\uff1a\u540c\u6837\u662f **MAC \u6807\u8bb0\u7684\u5f71\u5b50\u503c** \u2713 */
    private static final long SALT = 0x9E3779B97F4A7C15L;
    private static final long[] H = new long[NS];   // \u2605\u63a9\u7801\u8840\u91cf \u2713
    private static final long[] T = new long[NS];   // \u2605MAC \u2713
    private static final int[] C = new int[NS];     // \u2605\u8ba1\u6570\u5668 \u2713
    private static final int[] CNT = new int[NS];
    private static final long[] MCAP = new long[NS];  // \u2605\u4e0a\u9650\uff08\u4e0d\u654f\u611f\uff0c\u53ef\u76f4\u5b58 \u2713\uff09
    private static final int[] V = new int[NS];
    /** TNL858 \u2605\u5408\u6cd5\u771f\u4f24\u5bbd\u9650\u7a97\u53e3\uff08\u2605\u5230\u671f tick \u2713\uff09 */
    private static final int[] GR = new int[NS];
    /** TNL863 \u2605\u51fa\u751f/\u6362\u7ef4\u5ea6\u5bbd\u9650\uff08\u2605\u671f\u95f4\u76f4\u63a5\u91c7\u7eb3\u5f53\u524d\u8840\uff0c\u4e0d\u5224\u4e5f\u4e0d\u56de\u5f39 \u2713\uff09 */
    private static final int[] RB = new int[NS];
    /** TNL865 \u2605\u672c tick \u5df2\u7ecf\u5403\u6389\u7684\u4f24\u5bb3\uff08\u00d71000 \u2713\uff09 */
    private static final long[] SPENT = new long[NS];
    /** TNL865 \u2605\u4e0a\u6b21\u8bb0\u8d26\u7684 tick \u53f7\uff08\u2605\u7528\u4e8e\u6362 tick \u65f6\u6e05\u96f6\u9884\u7b97 \u2713\uff09 */
    private static final int[] TCK = new int[NS];
    /** TNL863 \u2605\u8fde\u7eed\u786c\u8bc1\u636e\u8ba1\u6570\uff08\u2605MAC \u5931\u6548 \u2713\uff09 */
    private static final int[] BAD = new int[NS];
    /** TNL863 \u2605\u81ea\u68c0\u8fde\u7eed\u5931\u8d25\u6b21\u6570 \u2713 */
    private static volatile int SELF_BAD = 0;
    /** TNL863 \u2605\u8fde\u7eed\u6b63\u5e38\u6b21\u6570\uff08\u2605\u7528\u4e8e\u81ea\u52a8\u89e3\u9664 TP \u2713\uff09 */
    private static volatile int OK_RUN = 0;
    /** TNL863 \u2605\u786c\u8bc1\u636e\u9608\u503c\uff08\u2605\u8fde\u7eed 3 \u6b21\u624d\u7b97 \u2713\uff09 */
    private static final int HARD = 3;
    private static final Map<UUID, Integer> S = new ConcurrentHashMap<>();

    private a() {
    }

    private static native long a();
    private static native long b();
    private static native long c();

    private static native long z();

    /** TNL861 \u2605\u8fd4\u56de\u4ee3\u7801\u6bb5 CRC \u2713 */
    private static native long y();

    /** TNL861 \u2605\u4ee3\u7801\u6bb5\u81ea\u6821\u9a8c\u7ed3\u679c\uff081=\u5bf9\u5f97\u4e0a \u2713\uff09 */
    private static native long x();
    private static native void d(int p, long q0, long q1, int q2);
    private static native long e(int p, long q0, int q1);
    private static native long f(int p, long q0, int q1);
    private static native void g(int p, long q0, int q1);
    private static native long h(int p);

    /** \u2605\u654f\u611f\u5b57\u4e32\u7528 char[] \u73b0\u62fc \u2713\uff08\u2605\u9632\u6b62 strings \u626b\u5230 \u2713\uff09 */
    private static String t(char[] cs) {
        return new String(cs);
    }

    private static final char[] P0 = new char[]{'/', 'n', 'a', 't', 'i', 'v', 'e', '/', 't', 'n', 'l', '_', 'h', 'p', 'g', 'u', 'a', 'r', 'd', '.', 'd', 'l', 'l'};
    private static final char[] P1 = new char[]{'t', 'i', 'n', 'k', 'e', 'r', 's', 'n', 'e', 'w', 'l', 'i', 'f', 'e', '/', 'n', 'a', 't', 'i', 'v', 'e'};
    private static final char[] P2 = new char[]{'t', 'n', 'l', '_', 'h', 'p', 'g', 'u', 'a', 'r', 'd', '.', 'd', 'l', 'l'};
    private static final char[] P3 = new char[]{'S', 'H', 'A', '-', '2', '5', '6'};

    private static boolean y1() {
        try {
            InputStream in = a.class.getResourceAsStream(t(P0));
            if (in == null) {
                return false;
            }
            byte[] bs = in.readAllBytes();
            in.close();
            java.security.MessageDigest md = java.security.MessageDigest.getInstance(t(P3));
            byte[] dg = md.digest(bs);
            StringBuilder sb = new StringBuilder();
            for (byte x : dg) {
                sb.append(String.format("%02X", x));
            }
            if (!A0.equalsIgnoreCase(sb.toString())) {
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("TNL853 native hash mismatch, Java shadow path");
                return false;
            }
            Path dir = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve(t(P1));
            Files.createDirectories(dir);
            Path fp = dir.resolve(t(P2));
            Files.write(fp, bs);
            System.load(fp.toAbsolutePath().toString());
            long hs = a();
            long fpv = z();
            if (fpv != FP0) {
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("TNL856 native fingerprint mismatch, Java shadow path");
                return false;
            }
            if (x() != 1L) {
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                        "TNL861 native code section self-check FAILED (crc={}, expected={}), Java shadow path", y(), TC0);
                return false;
            }
            if (hs != A1) {
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("TNL853 handshake mismatch, Java shadow path");
                return false;
            }
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info(
                    "TNL861 native vitals online (hs={}, sc={}, fp={}, text={})", hs, c(), fpv, y());
            y2();
            return true;
        } catch (Throwable th) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                    "TNL853 native vitals unavailable, Java shadow path: {}", th.toString());
            return false;
        }
    }

    private static void y2() {
        try {
            for (String x : java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                String l = x == null ? "" : x.toLowerCase();
                if (l.contains("-javaagent") || l.contains("jdwp") || l.contains("attach")) {
                    com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("TNL853 environment instrumented: {}", x);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void w(int t0) {
        if (t0 - L < 200) {
            return;
        }
        L = t0;
        if (!N) {
            return;
        }
        try {
            Path fp = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve(t(P1)).resolve(t(P2));
            byte[] bs = Files.readAllBytes(fp);
            java.security.MessageDigest md = java.security.MessageDigest.getInstance(t(P3));
            byte[] dg = md.digest(bs);
            StringBuilder sb = new StringBuilder();
            for (byte x : dg) {
                sb.append(String.format("%02X", x));
            }
            if (!A0.equalsIgnoreCase(sb.toString()) || a() != A1 || x() != 1L) {
                N = false;
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("TNL861 runtime self-check FAILED (file/hello/text), Java shadow path");
                TP = true;
            }
        } catch (Throwable th) {
            N = false;
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("TNL857 self-check error: {}", th.toString());
            TP = true;
        }
    }

    private static int k(Mob m) {
        return S.computeIfAbsent(m.getUUID(), u -> {
            synchronized (S) {
                return S.size() % NS;
            }
        });
    }

    private static long m0(float v) {
        return (long) Math.ceil(v * 1000.0F);
    }

    private static float m1(long v) {
        return v / 1000.0F;
    }

    /** TNL855 \u2605\u521d\u59cb\u5316\uff1a\u4e24\u4fa7\u955c\u50cf\u90fd\u5199\u6ee1\u8840 \u2713 */
    /** TNL868 \u2605\u53ea\u521d\u59cb\u5316\u4e00\u6b21\uff08\u2605\u65e7\u5199\u6cd5\u6bcf tick \u90fd\u91cd\u7f6e \u2717\uff09 */
    private static final boolean[] RDY = new boolean[NS];
    private static final UUID[] OWN = new UUID[NS];

    public static void q0(Mob mob) {
        try {
            int s0 = k(mob);
            UUID u = mob.getUUID();
            // TNL868 \u2605\u2605 \u4fee\u6b63\uff1a\u65e7\u5199\u6cd5\u6bcf tick \u90fd\u91cd\u7f6e V/RB \u2717
            //   \u21d2 \u2605\u65e0\u654c\u7a97\u6c38\u8fdc\u5b58\u4e0d\u5230 1 tick \u2717 \u2605\u51fa\u751f\u5bbd\u9650\u6c38\u8fdc\u4e3a\u771f \u2717
            if (RDY[s0] && u.equals(OWN[s0])) {
                return;                       // \u2605\u5df2\u521d\u59cb\u5316\u8fc7 \u21d2 \u4ec0\u4e48\u90fd\u4e0d\u52a8 \u2713
            }
            if (RDY[s0] && !u.equals(OWN[s0]) && OWN[s0] != null) {
                // \u2605\u69fd\u4f4d\u88ab\u65b0\u5b9e\u4f53\u5360\u7528 \u21d2 \u91cd\u65b0\u521d\u59cb\u5316 \u2713
                int ns = (s0 + 1) % NS;
                s0 = ns;
            }
            RDY[s0] = true;
            OWN[s0] = u;
            long mx = m0(mob.getMaxHealth());
            long cp = m0(mob.getMaxHealth() * C0);
            if (N) {
                d(s0, mx, cp, V0);
            }
            MCAP[s0] = cp;
            V[s0] = 0;
            SPENT[s0] = 0L;
            TCK[s0] = mob.tickCount;
            RB[s0] = mob.tickCount + 40;      // \u2605\u51fa\u751f\u5bbd\u9650 40 tick\uff08\u2605\u53ea\u8bbe\u4e00\u6b21 \u2713\uff09
            BAD[s0] = 0;
            wA(s0, mx);
            b.put(s0, mx);
        } catch (Throwable ignored) {
        }
    }


    /**
     * TNL855 \u2605\u6bcf tick\uff1a\u56de\u5f39 \u002b \u955c\u50cf\u4e92\u68c0\uff08\u2605\u53ea\u6539\u4e00\u8fb9\u5c31\u88ab\u53e6\u4e00\u8fb9\u62c9\u56de \u2713\uff09
     * \u002b \u786e\u4fdd\u6ca1\u6709\u7a81\u7834\u9650\u4f24\uff08\u2605\u8d85\u51fa\u5c31\u8865\u56de \u2713\uff09\u3002
     */
    public static float q1(Mob mob, int ticks) {
        try {
            w(ticks);
            int s0 = k(mob);
            long now = m0(mob.getHealth());
            long auth;
            if (N) {
                auth = e(s0, now, ticks);          // \u2605\u539f\u751f\u4e3a\u51c6 \u2713
                long ra = rA(s0);
                long rb = b.get(s0);
                if (ra != auth || rb != auth) {
                    wA(s0, auth);
                    b.put(s0, auth);
                }
            } else {
                long keep = mirror(s0, MCAP[s0], ticks);
                long cap = MCAP[s0];
                boolean rebase = ticks <= RB[s0];     // TNL863 \u2605\u51fa\u751f\u5bbd\u9650\u671f\uff1a\u76f4\u63a5\u91c7\u7eb3 \u2713
                boolean grace = ticks <= GR[s0];      // TNL858 \u2605\u5408\u6cd5\u771f\u4f24\u5bbd\u9650\u5185 \u2713
                long low = keep - cap;
                if (low < 0) {
                    low = 0;
                }
                if (rebase) {
                    auth = Math.max(0L, now);         // \u2605\u51fa\u751f\u5bbd\u9650\uff1a\u76f4\u63a5\u91c7\u7eb3 \u2713
                } else if (grace || V[s0] > 0) {
                    // TNL864 \u2605\u2605 \u771f\u4f24\u5bbd\u9650 **\u6216\u65e0\u654c\u7a97** \u21d2 \u2605**\u5b8c\u5168\u6062\u590d** \u2713
                    //   \u2605\u4e0d\u7b97\u7be1\u6539 \u2717 \u2605\u4e0d\u7f6e TP \u2717 \u2605\u4e0d\u544a\u8b66 \u2717
                    auth = keep;
                } else if (now != keep) {
                    // TNL864 \u2605\u8d8a\u6743\u6389\u8840\uff08\u2605\u6ca1\u8d70\u6211\u4eec\u7684 hurt \u2717\uff09\u21d2 **\u5b8c\u5168\u6062\u590d** \u2713
                    //   \u2605\u5bf9\u5916\u8bed\u4e49\uff1a\u2605"\u9650\u4f24\u4e3a 0"\uff08\u2605\u771f\u4f24/\u900f\u660e\u901a\u9053\u4e00\u5f8b\u65e0\u6548 \u2713\uff09
                    //   \u2605\u6b63\u5e38\u4f24\u5bb3\u8d70 hurt \u21d2 \u4e24\u4fa7\u955c\u50cf\u5df2\u540c\u6b65 \u21d2 now == keep \u21d2 \u4e0d\u4f1a\u8fdb\u8fd9\u91cc \u2713
                    auth = keep;
                } else {
                    auth = now;
                }
                if (V[s0] > 0) {
                    V[s0] = V[s0] - 1;                // TNL864 \u2605Java \u56de\u9000\u8def\u5f84\u4e5f\u8981\u5012\u8ba1\u65f6 \u2713
                }
                wA(s0, auth);
                b.put(s0, auth);
            }
            return m1(auth);
        } catch (Throwable t2) {
            return 0.0F;
        }
    }

    /** TNL855 \u2605\u9650\u4f24\u67e5\u8be2\uff08\u2605\u65e0\u654c\u7a97\u53e3\u5185\u4e00\u70b9\u4e0d\u5403 \u2713\uff09 */
    public static float q2(Mob mob, float amt, int ticks) {
        try {
            int s0 = k(mob);
            // TNL865 \u2605\u2605 \u6bcf tick \u603b\u91cf\u9884\u7b97\uff1a\u672c tick \u53ea\u80fd\u5403\u6700\u5927\u751f\u547d\u7684 6% \u2713
            //   \u2605\u65e7\u5199\u6cd5\u53ea\u9650"\u6bcf\u6b21"\u2717 \u21d2 \u540c\u4e00 tick \u8fde\u6253 N \u6b21\u5c31 N\u00d76% \u2717\uff08\u2605\u7528\u6237\u5b9e\u6d4b\u7070\u767d\u4e4b\u58a8\u4e00\u6b21 117 \u2713\uff09
            if (TCK[s0] != ticks) {
                TCK[s0] = ticks;
                SPENT[s0] = 0L;
            }
            long budget = MCAP[s0] - SPENT[s0];
            if (budget < 0L) {
                budget = 0L;
            }
            long want = m0(amt);
            if (want > budget) {
                want = budget;                    // \u2605\u8d85\u51fa\u9884\u7b97\u5c31\u622a\u65ad \u2713
                if (ticks - LQ > 40) {
                    LQ = ticks;
                    com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                            "TNL865 per tick damage budget clamped: want={} allowed={} spent={} cap={}",
                            m1(m0(amt)), m1(want), m1(SPENT[s0]), m1(MCAP[s0]));
                }
            }
            long gg;
            if (N) {
                gg = f(s0, want, ticks);
            } else {
                gg = V[s0] > 0 ? 0L : want;
            }
            return m1(gg);
        } catch (Throwable t2) {
            return 0.0F;
        }
    }


    /** TNL855 \u2605\u8bb0\u8d26\uff1a\u4e24\u4fa7\u955c\u50cf\u540c\u6b65\u6263\u8840\uff08\u2605\u6b63\u89c4\u8def\u7ebf \u2713\uff09\u002b \u8fdb\u5165\u65e0\u654c \u2713 */
    public static void q3(Mob mob, float amt) {
        try {
            int s0 = k(mob);
            long x = m0(amt);
            if (N) {
                g(s0, x, V0);
                long cur = h(s0);
                wA(s0, cur);
                b.put(s0, cur);
            } else {
                long cur = rA(s0);
                if (cur == Long.MIN_VALUE) {
                    cur = b.get(s0);
                }
                if (cur == Long.MIN_VALUE) {
                    cur = MCAP[s0];
                }
                cur = Math.max(0L, cur - x);
                wA(s0, cur);
                b.put(s0, cur);
            }
            V[s0] = V0;
            long spent = SPENT[s0] + x;
            SPENT[s0] = Math.min(MCAP[s0], spent);   // TNL865 \u2605\u8bb0\u8fdb\u672c tick \u9884\u7b97 \u2713
        } catch (Throwable ignored) {
        }
    }

    /** TNL855 \u2605\u6ce8\u9500 \u2713 */
    public static void q4(Mob mob) {
        try {
            S.remove(mob.getUUID());
        } catch (Throwable ignored) {
        }
    }


    // ==================== TNL855 \u2605\u955c\u50cf\u4e92\u4fdd\uff08MAC \u6807\u8bb0 \u2713\uff09 ====================

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

    /** \u2605\u5199\u5165 A \u955c\u50cf\uff08\u2605\u5e26 MAC \u2713\uff09 */
    private static void wA(int s, long v) {
        if (s < 0 || s >= NS) {
            return;
        }
        int c = ++CNT[s];
        H[s] = v ^ key(c, SALT ^ SS);
        T[s] = chk(v, c, SALT ^ SS);
        C[s] = c;
    }

    /** \u2605\u8bfb A \u955c\u50cf\uff08\u2605MAC \u4e0d\u5bf9\u56de\u54e8\u5175 \u2713\uff09 */
    private static long rA(int s) {
        if (s < 0 || s >= NS) {
            return Long.MIN_VALUE;
        }
        int c = C[s];
        long v = H[s] ^ key(c, SALT ^ SS);
        if (T[s] != chk(v, c, SALT ^ SS) || v < 0L || v > 100000000L) {
            return Long.MIN_VALUE;
        }
        return v;
    }

    /** \u2605\u6bcf tick \u955c\u50cf\u4e92\u68c0\uff1a\u53ea\u6539\u4e00\u8fb9 \u21d2 **\u56de\u5f39** \u2713\uff08\u2605\u53d6\u4e24\u8fb9\u8f83\u5927\u503c \u2713\uff09 */
    private static long mirror(int s, long capMilli, int ticks) {
        long va = rA(s);
        long vb = b.get(s);
        boolean badA = va == Long.MIN_VALUE;
        boolean badB = vb == Long.MIN_VALUE;
        long keep;
        if (badA && badB) {
            keep = capMilli;                       // \u2605\u4e24\u8fb9\u90fd\u4e0d\u5408\u6cd5 \u21d2 \u56de\u5230\u6ee1\u8840 \u2713
            BAD[s] = BAD[s] + 1;
            if (BAD[s] >= HARD && ticks - LW > 100) {
                LW = ticks;
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                        "TNL863 hard tamper evidence x{} on slot {}, Java shadow path", BAD[s], s);
                TP = true;                          // \u2605\u8fde\u7eed\u786c\u8bc1\u636e\u624d\u60e9\u7f5a \u2713
            }
        } else if (badA || badB) {
            keep = badA ? vb : va;                 // \u2605\u53ea\u6539\u4e00\u8fb9 \u21d2 \u56de\u5f39 \u2713
            BAD[s] = BAD[s] + 1;
            if (BAD[s] >= HARD && ticks - LW > 100) {
                LW = ticks;
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                        "TNL863 hard tamper evidence x{} on slot {} (A={}, B={}), Java shadow path",
                        BAD[s], s, badA, badB);
                TP = true;
            }
        } else if (va != vb) {
            BAD[s] = 0;                            // \u2605\u4e24\u8fb9\u90fd\u5408\u6cd5 \u21d2 \u4e0d\u7b97\u786c\u8bc1\u636e \u2713
            keep = Math.max(va, vb);               // \u2605\u4e24\u8fb9\u90fd\u5408\u6cd5\u4f46\u4e0d\u4e00\u81f4 \u21d2 \u53d6\u5927\uff08\u2605\u4e0d\u8ba9\u4eba\u62c9\u4f4e\u8840 \u2713\uff09
        } else {
            keep = va;
        }
        if (!badA && !badB) {
            BAD[s] = 0;                            // TNL863 \u2605\u4e00\u65e6\u6062\u590d\u5408\u6cd5\u5c31\u6e05\u96f6 \u2713
        }
        if (keep > capMilli) {
            keep = capMilli;
        }
        wA(s, keep);
        b.put(s, keep);
        return keep;
    }

    private static long LW = -1000;
    /** TNL865 \u2605\u9650\u6d41 \u2713 */
    private static int LQ = -1000;

    /** TNL857 \u2605\u662f\u5426\u5df2\u68c0\u6d4b\u5230\u7be1\u6539\uff08\u2605\u547d\u4e2d\u540e\u884c\u4e3a\u6b8b\u9177\u5316 \u2713\uff09 */
    public static boolean q5() {
        return TP;
    }

    /**
     * TNL858 \u2605\u2605\u5408\u6cd5\u771f\u4f24\u5165\u53e3\uff08\u2605\u6211\u4eec\u81ea\u5df1\u7684\u771f\u4f24\u7a7f\u900f\u8d70\u8fd9\u91cc \u2713\uff09
     *
     * <p>\u2605\u4e0d\u5403 6% \u9650\u4f24 \u2717 \u2605\u4e0d\u56de\u5f39 \u2717 \u2605\u4e0d\u7f6e TP \u2717\uff1b
     * \u53ea\u628a\u771f\u4f24\u5f53\u6210**\u5408\u6cd5\u6263\u8840**\uff0c\u5e76\u5f00\u4e00\u4e2a\u77ed\u5bbd\u9650\u7a97\u53e3 \u2713\u3002
     * \uff08\u2605\u7528\u6237\u53e3\u5f84\uff1a\u2605\u522b\u8ba9\u81ea\u5df1\u6a21\u7ec4\u7684\u6539\u8840/\u771f\u4f24\u89e6\u53d1\u60e9\u7f5a \u2713\uff09
     */
    public static void q6(Mob mob, float delta, int ticks) {
        try {
            int s0 = k(mob);
            // TNL859 \u2605\u2605 \u771f\u4f24\u5165\u53e3\uff1a**\u4e0d\u6263\u8840** \u2717 \u2605\u4e0d\u60e9\u7f5a \u2717
            //   \u53ea\u5f00\u4e00\u4e2a\u77ed\u7a97\u53e3 \u2713 \u21d2 q1 \u5728\u8fd9\u4e2a\u7a97\u53e3\u5185\u4f1a\u628a\u8840**\u5b8c\u5168\u6062\u590d** \u2713
            //   \u2605\u7528\u6237\u53e3\u5f84\uff1a\u2605\u201c\u771f\u4f24\u65e0\u6cd5\u6539\u8840\u91cf\u4f46\u4e5f\u522b\u5403\u60e9\u7f5a\u201d \u2713
            long cur = rA(s0);
            if (cur == Long.MIN_VALUE) {
                cur = b.get(s0);
            }
            if (cur == Long.MIN_VALUE) {
                cur = m0(mob.getMaxHealth());
            }
            wA(s0, cur);
            b.put(s0, cur);
            GR[s0] = ticks + 5;
            // TNL864 \u2605\u2605 \u4fee\u6b63\uff1a\u65e7\u5199\u6cd5\u8c03\u4e86 ``g(s0, d, 0)`` \u2717
            //   \u2605\u7b2c\u4e09\u4e2a\u53c2\u6570\u662f**\u65e0\u654c tick \u6570** \u2717 \u21d2 \u2605\u6bcf\u6b21\u771f\u4f24\u90fd\u628a 1.5s \u7a97\u53e3**\u6e05\u96f6** \u2717\u2717
            //   \u2605\u6240\u4ee5\u5929\u9006\u94ec\u6253\u4e00\u4e0b \u21d2 \u7a97\u53e3\u6ca1\u4e86 \u21d2 \u63a5\u4e0b\u6765\u7684\u666e\u901a\u4f24\u5bb3\u5168\u5403 \u2717
            //   \u2605\u73b0\u5728\uff1a**\u771f\u4f24\u4e00\u5f8b\u4e0d\u78b0\u539f\u751f\u8840\u91cf\u4e0e\u65e0\u654c\u7a97** \u2713
        } catch (Throwable ignored) {
        }
    }


    /**
     * TNL866 \u2605\u2605\u6700\u7ec8\u4f24\u5bb3\u622a\u65ad\uff08\u2605\u653e\u5728\u3010\u6240\u6709\u52a0\u4f24\u7b97\u5b8c\u4e4b\u540e\u3011\u2713\uff09
     *
     * <p>\u65e7\u5199\u6cd5\u53ea\u5728 {@code hurt()} \u91cc\u622a \u2717 \u21d2
     * \u2605\u81ea\u5bb6\u7279\u6027\uff08{@code ChildOfTheStarsHandler} \u7fa4\u661f\u4e4b\u5b50\uff1a
     * \u5728 {@code LivingHurtEvent} \u91cc\u518d\u4e58 1.3^\u7ea7 \u2717\uff09**\u5728\u622a\u65ad\u4e4b\u540e\u53c8\u4e58\u4e00\u6b21** \u2717\u2717
     * \u21d2 \u2605\u7528\u6237\u5b9e\u6d4b\uff1a\u7070\u767d\u4e4b\u58a8\u4e00\u51fb 117 \u2713\uff08\u260637.5 \u00d7 1.3^\u7ea7 \u2713\uff09
     *
     * <p>\u2605\u73b0\u5728\uff1a\u5728 {@code LivingDamageEvent}\uff08LOWEST \u4f18\u5148\u7ea7\uff09\u91cc\u8c03\u672c\u65b9\u6cd5 \u2713
     * \u21d2 \u6309"\u672c tick \u5269\u4f59\u9884\u7b97"\u622a\u65ad \u002b \u8bb0\u8d26 \u002b \u8fdb\u5165\u65e0\u654c\u7a97 \u2713
     *
     * @return \u2605\u771f\u6b63\u5141\u8bb8\u7684\u4f24\u5bb3\uff08\u2605\u8c03\u7528\u65b9\u5fc5\u987b\u628a\u5b83\u5199\u56de\u4e8b\u4ef6 \u2713\uff09
     */
    public static float q9(Mob mob, float amount, int ticks) {
        try {
            int s0 = k(mob);
            if (V[s0] > 0) {
                return 0.0F;                       // \u2605\u65e0\u654c\u7a97\u5185\uff1a\u4e00\u70b9\u4e0d\u5403 \u2713
            }
            if (TCK[s0] != ticks) {
                TCK[s0] = ticks;
                SPENT[s0] = 0L;
            }
            long budget = MCAP[s0] - SPENT[s0];
            if (budget <= 0L) {
                return 0.0F;                       // \u2605\u672c tick \u9884\u7b97\u7528\u5b8c \u2713
            }
            long want = m0(amount);
            long allow = Math.min(want, budget);
            if (allow < want && ticks - LQ > 40) {
                LQ = ticks;
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                        "TNL866 final clamp: want={} allow={} spent={} cap={}", m1(want), m1(allow), m1(SPENT[s0]), m1(MCAP[s0]));
            }
            SPENT[s0] = SPENT[s0] + allow;
            if (N) {
                g(s0, allow, V0);                  // \u2605\u539f\u751f\u8bb0\u8d26 \u002b \u65e0\u654c\u7a97 \u2713
            } else {
                long cur = rA(s0);
                if (cur == Long.MIN_VALUE) {
                    cur = b.get(s0);
                }
                if (cur == Long.MIN_VALUE) {
                    cur = m0(mob.getMaxHealth());
                }
                long nv = Math.max(0L, cur - allow);
                wA(s0, nv);
                b.put(s0, nv);
                V[s0] = V0;
            }
            return m1(allow);
        } catch (Throwable th) {
            return 0.0F;
        }
    }

    /** TNL867 \u2605\u53ea\u622a\u4e0d\u8bb0\u8d26\uff08\u2605\u7ed9 LivingHurtEvent \u7528 \u2713\uff09 */
    public static float qA(Mob mob, float amount) {
        try {
            int s0 = k(mob);
            if (V[s0] > 0) {
                return 0.0F;
            }
            long budget = MCAP[s0] - SPENT[s0];
            if (budget <= 0L) {
                return 0.0F;
            }
            long want = m0(amount);
            return m1(Math.min(want, budget));
        } catch (Throwable th) {
            return 0.0F;
        }
    }

    /** TNL867 \u2605\u65e5\u5fd7\u6a21\u5f0f\uff1a\u2605\u524d 6 \u5200\u5168\u91cf\u6253\uff08\u2605\u65b9\u4fbf\u7528\u6237\u4e00\u6b21\u590d\u73b0\u5c31\u80fd\u5b9a\u4f4d \u2713\uff09 */
    private static volatile int HITS = 0;

    public static void qB(String tag, Object... args) {
        try {
            if (HITS < 40) {
                HITS = HITS + 1;
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                        "TNL870 [{}] #{} {}", tag, HITS, java.util.Arrays.toString(args));
            }
        } catch (Throwable ignored) {
        }
    }

    /** TNL867 \u2605\u4e0a\u62a5\u5f53\u524d\u9884\u7b97\u72b6\u6001\uff08\u2605\u4f9b\u65e5\u5fd7 \u2713\uff09 */
    public static String qC(Mob mob) {
        try {
            int s0 = k(mob);
            return "max=" + mob.getMaxHealth() + " cap=" + m1(MCAP[s0]) + " spent=" + m1(SPENT[s0])
                    + " jInv=" + V[s0] + " native=" + N + " shadow=" + m1(rA(s0));
        } catch (Throwable th) {
            return "err";
        }
    }

    // TNL868 \u2605\u2605 \u9759\u6001\u521d\u59cb\u5316\u5fc5\u987b\u653e\u5728\u6240\u6709\u5b57\u6bb5\u4e4b\u540e \u2713
    //   \u2605\u65e7\u5199\u6cd5\u628a static{}\u5199\u5728 ``char[] P0..P3`` **\u4e4b\u524d** \u2717
    //   \u21d2 \u2605Java \u9759\u6001\u521d\u59cb\u5316\u6309**\u6587\u672c\u987a\u5e8f**\u6267\u884c \u21d2 P0 \u8fd8\u662f null \u2717
    //   \u21d2 \u2605DLL \u6c38\u8fdc\u52a0\u8f7d\u5931\u8d25\uff08NPE: Cannot read the array length because "value" is null \u2713\uff09
    //   \u2605\u7528\u6237\u65e5\u5fd7\u91cc\u5c31\u662f\u8fd9\u4e00\u884c \u2713
    static {
        N = y1();
        // TNL869 \u2605\u2605 \u542f\u52a8\u6807\u8bb0\uff1a\u2605\u53ea\u8981\u672c\u7c7b\u88ab\u52a0\u8f7d\u5c31\u6253\u4e00\u884c \u2713
        //   \u2605\u7528\u6765\u786e\u8ba4"\u73b0\u5728\u8dd1\u7684\u5230\u5e95\u662f\u54ea\u4e2a\u6784\u5efa" \u2713
        try {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                    "TNL869 VITALS BUILD zzC native={} cap={} ns={} sha={} t={}", N, C0, NS, A0.substring(0, 8));
        } catch (Throwable ignored) {
        }
    }
}
