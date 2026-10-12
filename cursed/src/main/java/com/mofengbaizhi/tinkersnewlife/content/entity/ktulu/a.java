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

    private static final String A0 = "91CC1ED70763ACE2D49BBAED99F1473BB18C66F89A1C234990DD25E95A124CE2";
    private static final long A1 = (0x544E4C31L << 8) | 3L;
    private static final int NS = 32;
    public static final float C0 = 0.06F;
    public static final int V0 = 30;

    private static volatile boolean N = false;
    private static long L = -1;

    private static final long[] H = new long[NS];
    private static final long[] M = new long[NS];
    private static final long[] C = new long[NS];
    private static final int[] V = new int[NS];
    private static final Map<UUID, Integer> S = new ConcurrentHashMap<>();

    static {
        N = z();
    }

    private a() {
    }

    private static native long a();
    private static native long b();
    private static native long c();
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

    private static boolean z() {
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
            if (hs != A1) {
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("TNL853 handshake mismatch, Java shadow path");
                return false;
            }
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info(
                    "TNL853 native vitals online (hs={}, sc={})", hs, c());
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
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("TNL853 runtime self-check FAILED, Java shadow path");
            }
        } catch (Throwable th) {
            N = false;
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("TNL853 self-check error: {}", th.toString());
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

    public static void q0(Mob m) {
        try {
            long mx = m0(m.getMaxHealth());
            long cp = m0(m.getMaxHealth() * C0);
            int s0 = k(m);
            if (N) {
                d(s0, mx, cp, V0);
            } else {
                M[s0] = mx;
                C[s0] = cp;
                H[s0] = mx;
                V[s0] = 0;
            }
        } catch (Throwable ignored) {
        }
    }

    public static float q1(Mob m, int t0) {
        try {
            w(t0);
            int s0 = k(m);
            long now = m0(m.getHealth());
            long fx;
            if (N) {
                fx = e(s0, now, t0);
            } else {
                if (V[s0] > 0) {
                    V[s0]--;
                }
                long lo = H[s0] - C[s0];
                if (lo < 0) {
                    lo = 0;
                }
                H[s0] = now < lo ? lo : now;
                fx = H[s0];
            }
            return m1(fx);
        } catch (Throwable t2) {
            return 0.0F;
        }
    }

    public static float q2(Mob m, float amt, int t0) {
        try {
            int s0 = k(m);
            long want = m0(amt);
            long gg;
            if (N) {
                gg = f(s0, want, t0);
            } else {
                gg = V[s0] > 0 ? 0L : Math.min(want, C[s0]);
            }
            return m1(gg);
        } catch (Throwable t2) {
            return 0.0F;
        }
    }

    public static void q3(Mob m, float amt) {
        try {
            int s0 = k(m);
            long x = m0(amt);
            if (N) {
                g(s0, x, V0);
            } else {
                H[s0] = Math.max(0L, H[s0] - x);
                V[s0] = V0;
            }
        } catch (Throwable ignored) {
        }
    }

    public static void q4(Mob m) {
        try {
            S.remove(m.getUUID());
        } catch (Throwable ignored) {
        }
    }
}
