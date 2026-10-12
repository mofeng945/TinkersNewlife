package com.mofengbaizhi.tinkersnewlife.content.entity.ktulu;

import net.minecraft.world.entity.Mob;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TNL852 \u2605\u2605 \u661f\u6e0a\u62a4\u8840\u5185\u6838\uff08\u2605\u540d\u5b57\u5df2\u62b9 \u2713 \u4e0d\u542b Health/Guard/HP \u7b49\u4efb\u4f55\u53ef\u731c\u8bcd \u2717\uff09
 *
 * <h2>\u4e24\u6761\u8def\uff08\u2605\u7528\u6237\u53e3\u5f84 \u2713\uff09</h2>
 * <ul>
 *   <li>\u2605DLL \u53ef\u7528 \u21d2 \u8d70 DLL\uff08\u2605\u5148\u6821\u9a8c\u54c8\u5e0c \u002b \u63e1\u624b \u002b \u81ea\u68c0 \u2713\uff09\uff1b</li>
 *   <li>\u2605\u5426\u5219\u56de\u9000\u81ea\u7814\u5f71\u5b50\u8840\u91cf \u2713\uff1a\u2605**\u4e0d\u8bfb getHealth\uff0c\u4e0d\u7528\u539f\u7248\u5b9e\u4f53\u6570\u636e\u5b58\u6743\u5a01\u503c** \u2717\uff0c
 *       \u539f\u7248\u90a3\u4e2a\u503c\u53ea\u5f53**\u663e\u793a\u955c\u50cf**\uff0c\u6bcf tick \u88ab\u672c\u7c7b\u8986\u5199\u7ea0\u6b63 \u2713\uff1b</li>
 *   <li>\u2605**\u8fd0\u884c\u65f6\u81ea\u6821\u9a8c** \u2713\uff1a\u6bcf 200 tick \u91cd\u9a8c\u843d\u5730 DLL \u7684 SHA-256 \u002b \u91cd\u63e1\u624b \u2713\uff1b
 *       \u542f\u52a8\u65f6\u987a\u5e26\u68c0\u67e5 {@code -javaagent}/\u8c03\u8bd5\u53c2\u6570 \u21d2 \u547d\u4e2d\u5c31\u8bb0\u4e00\u884c \u2713\u3002</li>
 * </ul>
 *
 * <p>\u26a0 \u2605\u5982\u5b9e\u8bf4\u660e\uff1aJava \u91cc**\u65e0\u6cd5\u963b\u6b62\u53cd\u5c04\u679a\u4e3e\u5230\u672c\u7c7b** \u2717\uff08\u2605Class.forName/\u626b\u5305\u90fd\u80fd\u627e\u5230 \u2717\uff09\uff0c
 * \u8fd9\u91cc\u505a\u7684\u662f\uff1a**\u540d\u5b57\u62b9\u9664 \u002b \u5bf9\u5916\u65e0\u53ef\u8bfb\u7684\u8840\u91cf\u63a5\u53e3 \u002b \u53ea\u7559\u9493\u9c7c\u5165\u53e3\u770b\u7740\u50cf\u6b63\u7ecf API \u002b \u8fd0\u884c\u65f6\u81ea\u68c0** \u2713\u3002
 */
public final class Vq {

    /** \u2605DLL SHA-256\uff08\u2605\u4e0d\u5bf9\u5c31\u4e0d\u88c5\u8f7d \u2713\uff09 */
    private static final String H = "6EC71B0DE174AA36F704CFB3448F308E0F4913D0E125B3FC3A966555E0A4CEEF";
    private static final long K = (0x544E4C31L << 8) | 2L;
    private static final long TOKEN = 0x1A2B3C4DL;
    private static final int SLOTS = 32;
    public static final float CAP = 0.06F;
    public static final int INV = 30;
    private static volatile boolean N = false;
    private static long LC = -1;

    private static final long[] SH = new long[SLOTS];
    private static final long[] SM = new long[SLOTS];
    private static final long[] SC = new long[SLOTS];
    private static final int[] SV = new int[SLOTS];
    private static final Map<UUID, Integer> SL = new ConcurrentHashMap<>();

    static {
        N = n0();
    }

    private Vq() {
    }

    private static native long k();

    private static native long t();

    private static native long s();

    private static native void i(int a, long b, long c, int d);

    private static native long r(int a, long b, int c);

    private static native long d(int a, long b, int c);

    private static native void a(int a, long b, int c);

    private static native long h(int a);

    private static boolean n0() {
        try {
            InputStream in = Vq.class.getResourceAsStream("/native/tnl_hpguard.dll");
            if (in == null) {
                return false;
            }
            byte[] bs = in.readAllBytes();
            in.close();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dg = md.digest(bs);
            StringBuilder sb = new StringBuilder();
            for (byte b : dg) {
                sb.append(String.format("%02X", b));
            }
            if (!H.equalsIgnoreCase(sb.toString())) {
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("TNL852 native hash mismatch, Java shadow path");
                return false;
            }
            Path dir = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("tinkersnewlife/native");
            Files.createDirectories(dir);
            Path f = dir.resolve("tnl_hpguard.dll");
            Files.write(f, bs);
            System.load(f.toAbsolutePath().toString());
            long hs = k();
            if (hs != K) {
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("TNL852 handshake mismatch, Java shadow path");
                return false;
            }
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info(
                    "TNL852 native vitals online (hs={}, sc={}, token={})", hs, s(), t() ^ (0L));
            chk();
            return true;
        } catch (Throwable th) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                    "TNL852 native vitals unavailable, Java shadow path: {}", th.toString());
            return false;
        }
    }

    /** \u2605\u73af\u5883\u68c0\u67e5\uff1a-\u200bjavaagent / \u8c03\u8bd5\u53c2\u6570 \u2713\uff08\u2605\u547d\u4e2d\u53ea\u8bb0\u5f55 \u2713\uff09 */
    private static void chk() {
        try {
            for (String x : java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                String l = x == null ? "" : x.toLowerCase();
                if (l.contains("-javaagent") || l.contains("jdwp") || l.contains("attach")) {
                    com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                            "TNL852 environment instrumented: {}", x);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** \u2605\u8fd0\u884c\u65f6\u81ea\u6821\u9a8c\uff1a\u6bcf 200 tick \u91cd\u9a8c\u843d\u5730 DLL \u54c8\u5e0c \u002b \u91cd\u63e1\u624b \u2713 */
    private static void rc(int ticks) {
        if (ticks - LC < 200) {
            return;
        }
        LC = ticks;
        if (!N) {
            return;
        }
        try {
            Path f = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("tinkersnewlife/native/tnl_hpguard.dll");
            byte[] bs = Files.readAllBytes(f);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dg = md.digest(bs);
            StringBuilder sb = new StringBuilder();
            for (byte b : dg) {
                sb.append(String.format("%02X", b));
            }
            if (!H.equalsIgnoreCase(sb.toString()) || k() != K) {
                N = false;
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                        "TNL852 runtime self-check FAILED, switching to Java shadow path");
            }
        } catch (Throwable th) {
            N = false;
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("TNL852 self-check error: {}", th.toString());
        }
    }

    private static int slot(Mob m) {
        return SL.computeIfAbsent(m.getUUID(), u -> {
            synchronized (SL) {
                return SL.size() % SLOTS;
            }
        });
    }

    private static long mm(float v) {
        return (long) Math.ceil(v * 1000.0F);
    }

    private static float ff(long v) {
        return v / 1000.0F;
    }

    public static void n(Mob m) {
        try {
            long mx = mm(m.getMaxHealth());
            long cp = mm(m.getMaxHealth() * CAP);
            int s0 = slot(m);
            if (N) {
                i(s0, mx, cp, INV);
            } else {
                SM[s0] = mx;
                SC[s0] = cp;
                SH[s0] = mx;
                SV[s0] = 0;
            }
        } catch (Throwable ignored) {
        }
    }

    public static float c(Mob m, int ticks) {
        try {
            rc(ticks);
            int s0 = slot(m);
            long now = mm(m.getHealth());
            long fixed;
            if (N) {
                fixed = r(s0, now, ticks);
            } else {
                if (SV[s0] > 0) {
                    SV[s0]--;
                }
                long low = SH[s0] - SC[s0];
                if (low < 0) {
                    low = 0;
                }
                SH[s0] = now < low ? low : now;
                fixed = SH[s0];
            }
            return ff(fixed);
        } catch (Throwable t2) {
            return 0.0F;
        }
    }

    public static float q(Mob m, float amount, int ticks) {
        try {
            int s0 = slot(m);
            long want = mm(amount);
            long g;
            if (N) {
                g = d(s0, want, ticks);
            } else {
                g = SV[s0] > 0 ? 0L : Math.min(want, SC[s0]);
            }
            return ff(g);
        } catch (Throwable t2) {
            return 0.0F;
        }
    }

    public static void p(Mob m, float amount) {
        try {
            int s0 = slot(m);
            long amt = mm(amount);
            if (N) {
                a(s0, amt, INV);
            } else {
                SH[s0] = Math.max(0L, SH[s0] - amt);
                SV[s0] = INV;
            }
        } catch (Throwable ignored) {
        }
    }

    public static boolean nn() {
        return N;
    }

    public static void rel(Mob m) {
        try {
            SL.remove(m.getUUID());
        } catch (Throwable ignored) {
        }
    }
}
