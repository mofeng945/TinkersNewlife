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

    private static final String A0 = "FC6442A65814ABB744FD304976C1FAC103F8ABD68A5172792A4D5C6FE195FD78";
    private static final long A1 = (0x544E4C31L << 8) | 4L;
    /** TNL856 \u2605DLL \u5185\u90e8\u6307\u7eb9\u8868\u7684 CRC32\uff08\u2605\u6869 DLL/\u6539\u8fc7\u7684 DLL \u5bf9\u4e0d\u4e0a \u2713\uff09 */
    private static final long FP0 = 4020521105L;
    /** TNL856 \u2605\u4f1a\u8bdd\u7ea7\u968f\u673a\u76d0\uff08\u2605\u6bcf\u5c40\u4e0d\u540c \u2712 \u53cd\u5c04\u8bfb\u4e86\u4e5f\u96be\u590d\u73b0\uff09 */
    private static final long SS = new java.security.SecureRandom().nextLong();
    /** TNL856 \u2605\u7be1\u6539\u6807\u5fd7\uff08\u2605\u547d\u4e2d\u540e\u8840\u6761\u9690\u85cf \u002b \u514d\u4f24 \u2713\uff09 */
    private static volatile boolean TP = false;
    private static final int NS = 32;
    public static final float C0 = 0.06F;
    public static final int V0 = 30;

    private static volatile boolean N = false;
    private static long L = -1;

    /* TNL855 \u2605\u955c\u50cf A\uff1a\u540c\u6837\u662f **MAC \u6807\u8bb0\u7684\u5f71\u5b50\u503c** \u2713 */
    private static final long SALT = 0x9E3779B97F4A7C15L;
    private static final long[] H = new long[NS];   // \u2605\u63a9\u7801\u8840\u91cf \u2713
    private static final long[] T = new long[NS];   // \u2605MAC \u2713
    private static final int[] C = new int[NS];     // \u2605\u8ba1\u6570\u5668 \u2713
    private static final int[] CNT = new int[NS];
    private static final long[] MCAP = new long[NS];  // \u2605\u4e0a\u9650\uff08\u4e0d\u654f\u611f\uff0c\u53ef\u76f4\u5b58 \u2713\uff09
    private static final int[] V = new int[NS];
    private static final Map<UUID, Integer> S = new ConcurrentHashMap<>();

    static {
        N = y1();
    }

    private a() {
    }

    private static native long a();
    private static native long b();
    private static native long c();

    private static native long z();
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
            if (hs != A1) {
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("TNL853 handshake mismatch, Java shadow path");
                return false;
            }
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info(
                    "TNL856 native vitals online (hs={}, sc={}, fp={})", hs, c(), fpv);
            y();
            return true;
        } catch (Throwable th) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                    "TNL853 native vitals unavailable, Java shadow path: {}", th.toString());
            return false;
        }
    }

    private static void y() {
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
            if (!A0.equalsIgnoreCase(sb.toString()) || a() != A1) {
                N = false;
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("TNL857 runtime self-check FAILED, Java shadow path");
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
    public static void q0(Mob mob) {
        try {
            long mx = m0(mob.getMaxHealth());
            long cp = m0(mob.getMaxHealth() * C0);
            int s0 = k(mob);
            if (N) {
                d(s0, mx, cp, V0);
            }
            MCAP[s0] = cp;
            V[s0] = 0;
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
                long low = keep - cap;
                if (low < 0) {
                    low = 0;
                }
                if (now < low) {
                    auth = low;                     // \u2605\u56de\u5f39\u8865\u56de \u2713
                } else if (now > keep) {
                    auth = keep;
                } else {
                    auth = now;
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
            long want = m0(amt);
            long gg;
            if (N) {
                gg = f(s0, want, ticks);
            } else {
                gg = V[s0] > 0 ? 0L : Math.min(want, MCAP[s0]);
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
            keep = capMilli;                       // \u2605\u4e24\u8fb9\u90fd\u88ab\u6539 \u21d2 \u56de\u5230\u6ee1\u8840\uff08\u2605\u5931\u8d25\u5373\u95ed \u2713\uff09
            if (ticks - LW > 100) {
                LW = ticks;
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("TNL857 both mirrors tampered, reset to max");
                TP = true;
            }
        } else if (badA || badB) {
            keep = badA ? vb : va;                 // \u2605\u53ea\u6539\u4e00\u8fb9 \u21d2 \u7528\u53e6\u4e00\u8fb9\u7684\u503c\uff08\u2605\u56de\u5f39 \u2713\uff09
            if (ticks - LW > 100) {
                LW = ticks;
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                        "TNL857 mirror tamper detected (A={}, B={}), rebound to {}", badA, badB, keep);
                TP = true;
            }
        } else if (va != vb) {
            keep = Math.max(va, vb);               // \u2605\u4e24\u8fb9\u90fd\u5408\u6cd5\u4f46\u4e0d\u4e00\u81f4 \u21d2 \u53d6\u5927\uff08\u2605\u4e0d\u8ba9\u4eba\u62c9\u4f4e\u8840 \u2713\uff09
        } else {
            keep = va;
        }
        if (keep > capMilli) {
            keep = capMilli;
        }
        wA(s, keep);
        b.put(s, keep);
        return keep;
    }

    private static long LW = -1000;

    /** TNL857 \u2605\u662f\u5426\u5df2\u68c0\u6d4b\u5230\u7be1\u6539\uff08\u2605\u547d\u4e2d\u540e\u884c\u4e3a\u6b8b\u9177\u5316 \u2713\uff09 */
    public static boolean q5() {
        return TP;
    }
}
