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
 * \u661f\u6e0a\u62a4\u8840\u5185\u6838\uff08\u2605\u771f\u6b63\u5e72\u6d3b\u7684\u7c7b \u2713\uff09
 *
 * <h2>\u4e24\u6761\u8def\uff08\u2605\u7528\u6237\u53e3\u5f84 \u2713\uff09</h2>
 * <ol>
 *   <li>\u2605**DLL \u53ef\u7528 \u21d2 \u8d70 DLL**\uff08\u2605`tnl_hpguard.dll` \u2713\uff09\uff1a
 *       \u6743\u5a01\u8840\u91cf/\u9650\u4f24/\u65e0\u654c\u7a97\u53e3\u5168\u5728\u539f\u751f\u5c42 \u2713\uff1b</li>
 *   <li>\u2605**DLL \u4e0d\u53ef\u7528 \u21d2 \u56de\u9000\u81ea\u7814\u5f71\u5b50\u8840\u91cf**\uff08\u2605`fakeGta` \u98ce\u683c \u2713\uff09\uff1a
 *       \u2605**\u4e0d\u8bfb {@code getHealth()}\uff0c\u4e5f\u4e0d\u7528\u539f\u7248\u5b9e\u4f53\u6570\u636e\u5b58\u6743\u5a01\u503c** \u2717
 *       \u6743\u5a01\u503c\u53ea\u5b58\u5728\u672c\u7c7b\u7684\u79c1\u6709\u8868\u91cc \u2713\uff08\u2605\u539f\u7248\u90a3\u4e2a\u53ea\u5f53**\u663e\u793a\u955c\u50cf** \u2713\uff09\u3002</li>
 * </ol>
 *
 * <p>\u2605\u9632\u9006\u5411\uff1a\u2605\u672c\u7c7b\u540d\u5b57\u4e0d\u542b Health/Guard \u7b49\u5173\u952e\u8bcd \u2713\uff1b
 * \u5bf9\u5916\u53ea\u66b4\u9732\u4e00\u4e2a\u7c7b\u4f3c\u201c\u53d6\u6700\u5927\u751f\u547d\u201d\u7684\u65e0\u5bb3\u63a5\u53e3 \u2713\uff1b
 * \u771f\u6b63\u7684\u8c03\u7528\u70b9\u5728 {@code KtuluMomo}\u91cc\u76f4\u63a5\u786c\u5199 \u2713\u3002
 */
public final class AstralVitalsKernel {

    /** \u2605DLL \u7684 SHA-256\uff08\u2605\u7248\u672c\u4e0d\u5bf9\u5c31\u4e0d\u88c5\u8f7d \u2713\uff09 */
    private static final String EXPECTED_SHA256 =
            "4A0D0825FFFA8995DF5187B27AFEBA9F765BA3063489A3D647A8ABF4EBE01B55";
    /** \u2605\u63e1\u624b\u503c\uff1a(\u9b54\u6570 << 8) | ABI \u2713 */
    private static final long EXPECTED_HANDSHAKE = (0x544E4C31L << 8) | 1L;
    /** \u2605\u5355\u6b21\u4f24\u5bb3\u4e0a\u9650\uff1a\u6700\u5927\u751f\u547d\u7684 6% \u2713 */
    public static final float DAMAGE_CAP_RATIO = 0.06F;
    /** \u2605\u53d7\u4f24\u540e\u65e0\u654c\uff1a1.5 \u79d2 \u2713 */
    public static final int INVULN_TICKS = 30;

    private static final int SLOTS = 32;
    private static final boolean NATIVE;
    private static final long[] SHADOW = new long[SLOTS];      // \u2605\u81ea\u7814\u5f71\u5b50\u8840\u91cf\uff08\u00d71000 \u2713\uff09
    private static final long[] SHADOW_MAX = new long[SLOTS];
    private static final int[] SHADOW_INVULN = new int[SLOTS];
    private static final long[] SHADOW_CAP = new long[SLOTS];
    private static final Map<UUID, Integer> SLOT_OF = new ConcurrentHashMap<>();

    static {
        NATIVE = tnl$loadNative();
    }

    private AstralVitalsKernel() {
    }

    // ==================== \u539f\u751f\u58f0\u660e\uff08\u2605\u7b26\u53f7\u5fc5\u987b\u4e0e\u7c7b\u540d\u4e00\u81f4 \u2713\uff09 ====================
    private static native long nativeHandshake();

    private static native long nativeSelfCheck();

    private static native void nativeInit(int slot, long maxMilli, long capMilli, int invulnTicks);

    private static native long nativeRecord(int slot, long healthMilli, int ticks);

    private static native long nativeRequestDamage(int slot, long amountMilli, int ticks);

    private static native void nativeApply(int slot, long amountMilli, int invulnTicks);

    private static native long nativeHealth(int slot);

    /** \u2605\u8f7d\u5165\u539f\u751f\u5e93\uff1a\u6821\u9a8c\u54c8\u5e0c \u2712 \u63e1\u624b \u2712 \u81ea\u68c0 \u2713\uff08\u2605\u5931\u8d25\u5c31\u56de\u9000 Java \u2713\uff09 */
    private static boolean tnl$loadNative() {
        try {
            InputStream in = AstralVitalsKernel.class.getResourceAsStream("/native/tnl_hpguard.dll");
            if (in == null) {
                return false;
            }
            byte[] bytes = in.readAllBytes();
            in.close();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : dig) {
                sb.append(String.format("%02X", b));
            }
            if (!EXPECTED_SHA256.equalsIgnoreCase(sb.toString())) {
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                        "TNL851 native kernel hash mismatch, falling back to the Java shadow kernel");
                return false;
            }
            Path dir = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("tinkersnewlife/native");
            Files.createDirectories(dir);
            Path out = dir.resolve("tnl_hpguard.dll");
            Files.write(out, bytes);
            System.load(out.toAbsolutePath().toString());
            long hs = nativeHandshake();
            if (hs != EXPECTED_HANDSHAKE) {
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                        "TNL851 native kernel handshake mismatch, falling back to the Java shadow kernel");
                return false;
            }
            long check = nativeSelfCheck();
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info(
                    "TNL851 native vitals kernel online (handshake={}, selfCheck={})", hs, check);
            return true;
        } catch (Throwable t) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                    "TNL851 native vitals kernel unavailable, using the Java shadow kernel: {}", t.toString());
            return false;
        }
    }

    /** \u2605\u662f\u5426\u8d70 DLL\uff08\u2605\u4ec5\u4f9b\u65e5\u5fd7\u4e0e\u8bca\u65ad \u2713\uff09 */
    public static boolean nativeActive() {
        return NATIVE;
    }

    private static int slotOf(Mob mob) {
        return SLOT_OF.computeIfAbsent(mob.getUUID(), u -> {
            synchronized (SLOT_OF) {
                return SLOT_OF.size() % SLOTS;
            }
        });
    }

    private static long toMilli(float v) {
        return (long) Math.ceil(v * 1000.0F);
    }

    private static float fromMilli(long v) {
        return v / 1000.0F;
    }

    /** \u2605\u9996\u6b21\u51c6\u5907\uff08\u2605\u4e24\u6761\u8def\u90fd\u8d70\u8fd9\u91cc \u2713\uff09 */
    public static void ensure(Mob mob) {
        try {
            long max = toMilli(mob.getMaxHealth());
            long cap = toMilli(mob.getMaxHealth() * DAMAGE_CAP_RATIO);
            int slot = slotOf(mob);
            if (NATIVE) {
                nativeInit(slot, max, cap, INVULN_TICKS);
            } else {
                SHADOW_MAX[slot] = max;
                SHADOW_CAP[slot] = cap;
                SHADOW[slot] = max;
                SHADOW_INVULN[slot] = 0;
            }
        } catch (Throwable ignored) {
        }
    }

    /** \u2605\u6743\u5a01\u8840\u91cf\uff08\u2605\u4e0d\u8bfb\u539f\u7248 getHealth \u2717\uff09 */
    public static float health(Mob mob) {
        try {
            int slot = slotOf(mob);
            long v = NATIVE ? nativeHealth(slot) : SHADOW[slot];
            return v < 0 ? mob.getMaxHealth() : fromMilli(v);
        } catch (Throwable t) {
            return 0.0F;
        }
    }

    /** \u2605\u6bcf tick \u56de\u5f39\u6821\u9a8c\uff1a\u4f20\u5165\u5f53\u524d\u5c55\u793a\u503c\uff0c\u8fd4\u56de**\u5e94\u4e3a**\u7684\u503c \u2713 */
    public static float record(Mob mob, int ticks) {
        try {
            int slot = slotOf(mob);
            long now = toMilli(mob.getHealth());
            long fixed = NATIVE ? nativeRecord(slot, now, ticks)
                    : tnl$shadowRecord(slot, now);
            return fromMilli(fixed);
        } catch (Throwable t) {
            return 0.0F;
        }
    }

    /** \u2605\u81ea\u7814\u5f71\u5b50\u56de\u5f39\uff08\u2605DLL \u4e0d\u5728\u65f6\u7684\u552f\u4e00\u5224\u5b9a \u2713\uff09 */
    private static long tnl$shadowRecord(int slot, long now) {
        if (SHADOW_INVULN[slot] > 0) {
            SHADOW_INVULN[slot]--;
        }
        long lowest = SHADOW[slot] - SHADOW_CAP[slot];
        if (lowest < 0) {
            lowest = 0;
        }
        SHADOW[slot] = now < lowest ? lowest : now;
        return SHADOW[slot];
    }

    /** \u2605\u8bf7\u6c42\u4f24\u5bb3\uff1a\u8fd4\u56de**\u771f\u6b63\u5141\u8bb8**\u7684\u4f24\u5bb3\uff08\u9650\u4f24\uff0b\u65e0\u654c\u7a97\u53e3 \u2713\uff09 */
    public static float requestDamage(Mob mob, float amount, int ticks) {
        try {
            int slot = slotOf(mob);
            long want = toMilli(amount);
            long ok = NATIVE ? nativeRequestDamage(slot, want, ticks)
                    : (SHADOW_INVULN[slot] > 0 ? 0L : Math.min(want, SHADOW_CAP[slot]));
            return fromMilli(ok);
        } catch (Throwable t) {
            return 0.0F;
        }
    }

    /** \u2605\u4f24\u5bb3\u843d\u5730\u540e\u8bb0\u8d26\uff08\u2605\u5e76\u8fdb\u5165\u65e0\u654c\u7a97\u53e3 \u2713\uff09 */
    public static void apply(Mob mob, float amount) {
        try {
            int slot = slotOf(mob);
            long amt = toMilli(amount);
            if (NATIVE) {
                nativeApply(slot, amt, INVULN_TICKS);
            } else {
                SHADOW[slot] = Math.max(0L, SHADOW[slot] - amt);
                SHADOW_INVULN[slot] = INVULN_TICKS;
            }
        } catch (Throwable ignored) {
        }
    }

    /** \u2605\u53d6\u6d88\u767b\u8bb0\uff08\u2605\u5b9e\u4f53\u6d88\u5931\u65f6 \u2713\uff09 */
    public static void release(Mob mob) {
        try {
            SLOT_OF.remove(mob.getUUID());
        } catch (Throwable ignored) {
        }
    }
}
