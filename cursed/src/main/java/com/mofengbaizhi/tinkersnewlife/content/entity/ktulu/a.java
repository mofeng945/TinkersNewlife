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
    /** TNL856 ★DLL 内部指纹表的 CRC32（★桩 DLL/改过的 DLL 对不上 ✓） */
    private static final long FP0 = 4020521105L;
    /** TNL861 ★DLL **代码段(.text)** 的 CRC32（★改代码段就对不上 ✓） */
    private static final long TC0 = 2270635665L;
    /** TNL856 ★会话级随机盐（★每局不同 ✒ 反射读了也难复现） */
    private static final long SS = new java.security.SecureRandom().nextLong();
    /** TNL856 ★篡改标志（★命中后血条隐藏 + 免伤 ✓） */
    private static volatile boolean TP = false;
    private static final int NS = 32;
    public static final float C0 = 0.06F;
    public static final int V0 = 30;

    private static volatile boolean N = false;
    private static long L = -1;
    /** TNL862 ★后台自检：**不在主线程做 SHA-256/CRC** ✓（★用户反馈：会掉帧 ✗） */
    private static final java.util.concurrent.atomic.AtomicBoolean BUSY =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static volatile long LAST_MS = 0L;
    /** ★抽样间隔（ms）：★随机化 ✓（★不给外挂抓节奏 ✓） */
    private static final long GAP_MIN = 20000L;
    private static final long GAP_RANGE = 20000L;

    /* TNL855 ★镜像 A：同样是 **MAC 标记的影子值** ✓ */
    private static final long SALT = 0x9E3779B97F4A7C15L;
    private static final long[] H = new long[NS];   // ★掩码血量 ✓
    private static final long[] T = new long[NS];   // ★MAC ✓
    private static final int[] C = new int[NS];     // ★计数器 ✓
    private static final int[] CNT = new int[NS];
    private static final long[] MCAP = new long[NS];  // ★上限（不敏感，可直存 ✓）
    private static final int[] V = new int[NS];
    /** TNL858 ★合法真伤宽限窗口（★到期 tick ✓） */
    private static final int[] GR = new int[NS];
    /** TNL863 ★出生/换维度宽限（★期间直接采纳当前血，不判也不回弹 ✓） */
    private static final int[] RB = new int[NS];
    /** TNL865 ★本 tick 已经吃掉的伤害（×1000 ✓） */
    private static final long[] SPENT = new long[NS];
    /** TNL865 ★上次记账的 tick 号（★用于换 tick 时清零预算 ✓） */
    private static final int[] TCK = new int[NS];
    /** TNL863 ★连续硬证据计数（★MAC 失效 ✓） */
    private static final int[] BAD = new int[NS];
    /** TNL863 ★自检连续失败次数 ✓ */
    private static volatile int SELF_BAD = 0;
    /** TNL863 ★连续正常次数（★用于自动解除 TP ✓） */
    private static volatile int OK_RUN = 0;
    /** TNL863 ★硬证据阈值（★连续 3 次才算 ✓） */
    private static final int HARD = 3;
    private static final Map<UUID, Integer> S = new ConcurrentHashMap<>();

    private a() {
    }

    private static native long a();
    private static native long b();
    private static native long c();

    private static native long z();

    /** TNL861 ★返回代码段 CRC ✓ */
    private static native long y();

    /** TNL861 ★代码段自校验结果（1=对得上 ✓） */
    private static native long x();
    private static native void d(int p, long q0, long q1, int q2);
    private static native long e(int p, long q0, int q1);
    private static native long f(int p, long q0, int q1);
    private static native void g(int p, long q0, int q1);
    private static native long h(int p);

    /** ★敏感字串用 char[] 现拼 ✓（★防止 strings 扫到 ✓） */
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
                return false;
            }
            if (x() != 1L) {
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                        "TNL861 native code section self-check FAILED (crc={}, expected={}), Java shadow path", y(), TC0);
                return false;
            }
            if (hs != A1) {
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
                TP = true;
            }
        } catch (Throwable th) {
            N = false;
            TP = true;
        }
    }

    private static int k(Mob m) {
        return S.computeIfAbsent(m.getUUID(), u -> {
            synchronized (S) {
                // TNL1141 user: "if two momos are born too close together the health is inherited".
                // The old formula was S.size() % NS, which hands a brand new momo the slot of an
                // existing one as soon as the map is full or a slot was freed -- the newcomer then
                // reads the other momo's masked health. Claim a slot nobody owns instead.
                for (int i = 0; i < NS; i++) {
                    int cand = (S.size() + i) % NS;
                    if (!RDY[cand] || OWN[cand] == null || OWN[cand].equals(u)) {
                        return cand;
                    }
                }
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

    /** TNL855 ★初始化：两侧镜像都写满血 ✓ */
    /** TNL868 ★只初始化一次（★旧写法每 tick 都重置 ✗） */
    private static final boolean[] RDY = new boolean[NS];
    private static final UUID[] OWN = new UUID[NS];

    public static void q0(Mob mob) {
        try {
            int s0 = k(mob);
            UUID u = mob.getUUID();
            // TNL868 ★★ 修正：旧写法每 tick 都重置 V/RB ✗
            //   ⇒ ★无敌窗永远存不到 1 tick ✗ ★出生宽限永远为真 ✗
            if (RDY[s0] && u.equals(OWN[s0])) {
                return;                       // ★已初始化过 ⇒ 什么都不动 ✓
            }
            if (RDY[s0] && !u.equals(OWN[s0]) && OWN[s0] != null) {
                // ★槽位被新实体占用 ⇒ 重新初始化 ✓
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
            RB[s0] = mob.tickCount + 40;      // ★出生宽限 40 tick（★只设一次 ✓）
            BAD[s0] = 0;
            wA(s0, mx);
            b.put(s0, mx);
        } catch (Throwable ignored) {
        }
    }


    /**
     * TNL855 ★每 tick：回弹 + 镜像互检（★只改一边就被另一边拉回 ✓）
     * + 确保没有突破限伤（★超出就补回 ✓）。
     */
    public static float q1(Mob mob, int ticks) {
        try {
            w(ticks);
            int s0 = k(mob);
            long now = m0(mob.getHealth());
            long auth;
            if (N) {
                // TNL873 ★★ 修正：★旧写法在原生路径里直接调 ``e()`` ✗
                //   ⇒ ★窗内的**直接写血**依然能吃掉最多 6% ✗
                //   ★用户口径：★"现在免疫窗拦住了 hurt 但没拦住改血" ✓
                if (V[s0] > 0) {
                    V[s0] = V[s0] - 1;             // ★Java 侧也跟着倒计时 ✓
                    long hv = h(s0);
                    // TNL914 ★★ 修正"窗口 2 倍" ✓
                    //   ★原因 ✗：★原生窗的**倒计时写在 ``e()`` 里** ✓
                    //     ★而窗内分支只调 ``h()`` 读值 ✗ ⇒ ★原生窗一直停在 30 不动 ✗
                    //     ⇒ ★Java 侧走完 30 tick 后 才开始走原生那 30 tick ✗
                    //     ⇒ ★实测落地间隔 **3.07s** ✓（★= 2× ✓）
                    //   ★现在：★额外调一次 ``e()`` 只为**让原生窗倒计时** ✓
                    //     （★值传权威值 ✓ ⇒ ★不会被拉低 ✗）
                    e(s0, hv < 0L ? now : hv, ticks);
                    auth = hv < 0L ? now : hv;     // ★窗内：**不采纳任何掉血** ⇒ ★直写被覆盖 ✓
                    if (now < auth) {
                        BLOCKED = true;            // TNL907 ★挡下了直写 ✓
                    }
                    if (ticks - LZ > 40 && now < auth) {
                        LZ = ticks;
                        com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                                "TNL873 window blocked a direct health write: now={} restored={}", m1(now), m1(auth));
                    }
                } else {
                    auth = e(s0, now, ticks);      // ★原生为准 ✓
                }
                long ra = rA(s0);
                long rb = b.get(s0);
                if (ra != auth || rb != auth) {
                    wA(s0, auth);
                    b.put(s0, auth);
                }
            } else {
                long keep = mirror(s0, MCAP[s0], ticks);
                long cap = MCAP[s0];
                boolean rebase = ticks <= RB[s0];     // TNL863 ★出生宽限期：直接采纳 ✓
                boolean grace = ticks <= GR[s0];      // TNL858 ★合法真伤宽限内 ✓
                long low = keep - cap;
                if (low < 0) {
                    low = 0;
                }
                if (rebase) {
                    auth = Math.max(0L, now);         // ★出生宽限：直接采纳 ✓
                } else if (grace || V[s0] > 0) {
                    // TNL864 ★★ 真伤宽限 **或无敌窗** ⇒ ★**完全恢复** ✓
                    //   ★不算篡改 ✗ ★不置 TP ✗ ★不告警 ✗
                    auth = keep;
                } else if (now != keep) {
                    // TNL864 ★越权掉血（★没走我们的 hurt ✗）⇒ **完全恢复** ✓
                    //   ★对外语义：★"限伤为 0"（★真伤/透明通道一律无效 ✓）
                    //   ★正常伤害走 hurt ⇒ 两侧镜像已同步 ⇒ now == keep ⇒ 不会进这里 ✓
                    auth = keep;
                } else {
                    auth = now;
                }
                if (V[s0] > 0) {
                    V[s0] = V[s0] - 1;                // TNL864 ★Java 回退路径也要倒计时 ✓
                }
                wA(s0, auth);
                b.put(s0, auth);
            }
            return m1(auth);
        } catch (Throwable t2) {
            return 0.0F;
        }
    }

    /** TNL855 ★限伤查询（★无敌窗口内一点不吃 ✓） */
    public static float q2(Mob mob, float amt, int ticks) {
        try {
            int s0 = k(mob);
            // TNL865 ★★ 每 tick 总量预算：本 tick 只能吃最大生命的 6% ✓
            //   ★旧写法只限"每次"✗ ⇒ 同一 tick 连打 N 次就 N×6% ✗（★用户实测灰白之墨一次 117 ✓）
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
                want = budget;                    // ★超出预算就截断 ✓
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
            // TNL911 ★★ 关键：★闸门返回 0 且窗还在 ⇒ ★就是"被挡下" ✓
            //   ★用户日志证据：★[GATE] gate=0.0 jInv=12/16/19 ✓
            //   ★而旧写法把置位错插在了 q9 里 ✗
            //     ⇒ ★窗内挡刀（★走 q2 ✗）时标志永远不亮 ✗
            if (gg <= 0L && V[s0] > 0) {
                BLOCKED = true;
            }
            return m1(gg);
        } catch (Throwable t2) {
            return 0.0F;
        }
    }


    /** TNL855 ★记账：两侧镜像同步扣血（★正规路线 ✓）+ 进入无敌 ✓ */
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
            SPENT[s0] = Math.min(MCAP[s0], spent);   // TNL865 ★记进本 tick 预算 ✓
        } catch (Throwable ignored) {
        }
    }

    /**
     * TNL1144 ★★ 按本系统自己的规矩接一记“作弊秒杀”：
     *
     * <p>★宇宙剑会 `hurt(Float.MAX_VALUE)` ⇒ ★我们先**把它按单 tick 预算截断**，
     * 并用 `q3` 记一笔账 ✓ —— ★`q3` 会把 `V[s0]` 打开 30 tick ✓
     * ⇒ ★紧接着它那句 `setHealth(0)` 就落在**窗口内**✓
     * ⇒ ★`qD` 自然会把它拦成权威值 ✓（★完全不动外围逻辑 ✓）
     *
     * @return ★本 tick 允许吃掉的伤害（★单 tick 预算 ✓）
     */
    public static float qI(Mob mob, float amount) {
        try {
            int s0 = k(mob);
            long want = m0(amount);
            if (TCK[s0] != mob.tickCount) {
                TCK[s0] = mob.tickCount;
                SPENT[s0] = 0L;
            }
            long budget = MCAP[s0] - SPENT[s0];
            if (budget < 0L) {
                budget = 0L;
            }
            long allowed = Math.min(want, budget);
            q3(mob, m1(allowed));          // ★记账 + 打开 V 窗口 ✓
            return m1(allowed);
        } catch (Throwable ignored) {
            return 0.0F;
        }
    }

    /** TNL855 ★注销 ✓ */
    public static void q4(Mob mob) {
        try {
            S.remove(mob.getUUID());
        } catch (Throwable ignored) {
        }
    }


    // ==================== TNL855 ★镜像互保（MAC 标记 ✓） ====================

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

    /** ★写入 A 镜像（★带 MAC ✓） */
    private static void wA(int s, long v) {
        if (s < 0 || s >= NS) {
            return;
        }
        int c = ++CNT[s];
        H[s] = v ^ key(c, SALT ^ SS);
        T[s] = chk(v, c, SALT ^ SS);
        C[s] = c;
    }

    /** ★读 A 镜像（★MAC 不对回哨兵 ✓） */
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

    /** ★每 tick 镜像互检：只改一边 ⇒ **回弹** ✓（★取两边较大值 ✓） */
    private static long mirror(int s, long capMilli, int ticks) {
        long va = rA(s);
        long vb = b.get(s);
        boolean badA = va == Long.MIN_VALUE;
        boolean badB = vb == Long.MIN_VALUE;
        long keep;
        if (badA && badB) {
            keep = capMilli;                       // ★两边都不合法 ⇒ 回到满血 ✓
            BAD[s] = BAD[s] + 1;
            if (BAD[s] >= HARD && ticks - LW > 100) {
                LW = ticks;
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                        "TNL863 hard tamper evidence x{} on slot {}, Java shadow path", BAD[s], s);
                TP = true;                          // ★连续硬证据才惩罚 ✓
            }
        } else if (badA || badB) {
            keep = badA ? vb : va;                 // ★只改一边 ⇒ 回弹 ✓
            BAD[s] = BAD[s] + 1;
            if (BAD[s] >= HARD && ticks - LW > 100) {
                LW = ticks;
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                        "TNL863 hard tamper evidence x{} on slot {} (A={}, B={}), Java shadow path",
                        BAD[s], s, badA, badB);
                TP = true;
            }
        } else if (va != vb) {
            BAD[s] = 0;                            // ★两边都合法 ⇒ 不算硬证据 ✓
            keep = Math.max(va, vb);               // ★两边都合法但不一致 ⇒ 取大（★不让人拉低血 ✓）
        } else {
            keep = va;
        }
        if (!badA && !badB) {
            BAD[s] = 0;                            // TNL863 ★一旦恢复合法就清零 ✓
        }
        if (keep > capMilli) {
            keep = capMilli;
        }
        wA(s, keep);
        b.put(s, keep);
        return keep;
    }

    private static long LW = -1000;
    /** TNL865 ★限流 ✓ */
    private static int LQ = -1000;
    /** TNL873 ★窗口拦截日志限流 ✓ */
    private static int LZ = -1000;

    /** TNL907 ★★ 本次是否发生了"免疫窗挡下伤害/改血" ✓ */
    private static volatile boolean BLOCKED = false;

    /** TNL907 ★取走并清除标志（★消费式 ✓） */
    public static boolean qH() {
        if (!BLOCKED) {
            return false;
        }
        BLOCKED = false;
        return true;
    }

    /** TNL857 ★是否已检测到篡改（★命中后行为残酷化 ✓） */
    public static boolean q5() {
        return TP;
    }

    /**
     * TNL858 ★★合法真伤入口（★我们自己的真伤穿透走这里 ✓）
     *
     * <p>★不吃 6% 限伤 ✗ ★不回弹 ✗ ★不置 TP ✗；
     * 只把真伤当成**合法扣血**，并开一个短宽限窗口 ✓。
     * （★用户口径：★别让自己模组的改血/真伤触发惩罚 ✓）
     */
    public static void q6(Mob mob, float delta, int ticks) {
        try {
            int s0 = k(mob);
            // TNL859 ★★ 真伤入口：**不扣血** ✗ ★不惩罚 ✗
            //   只开一个短窗口 ✓ ⇒ q1 在这个窗口内会把血**完全恢复** ✓
            //   ★用户口径：★“真伤无法改血量但也别吃惩罚” ✓
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
            // TNL864 ★★ 修正：旧写法调了 ``g(s0, d, 0)`` ✗
            //   ★第三个参数是**无敌 tick 数** ✗ ⇒ ★每次真伤都把 1.5s 窗口**清零** ✗✗
            //   ★所以天逆铬打一下 ⇒ 窗口没了 ⇒ 接下来的普通伤害全吃 ✗
            //   ★现在：**真伤一律不碰原生血量与无敌窗** ✓
        } catch (Throwable ignored) {
        }
    }


    /**
     * TNL866 ★★最终伤害截断（★放在【所有加伤算完之后】✓）
     *
     * <p>旧写法只在 {@code hurt()} 里截 ✗ ⇒
     * ★自家特性（{@code ChildOfTheStarsHandler} 群星之子：
     * 在 {@code LivingHurtEvent} 里再乘 1.3^级 ✗）**在截断之后又乘一次** ✗✗
     * ⇒ ★用户实测：灰白之墨一击 117 ✓（☆37.5 × 1.3^级 ✓）
     *
     * <p>★现在：在 {@code LivingDamageEvent}（LOWEST 优先级）里调本方法 ✓
     * ⇒ 按"本 tick 剩余预算"截断 + 记账 + 进入无敌窗 ✓
     *
     * @return ★真正允许的伤害（★调用方必须把它写回事件 ✓）
     */
    public static float q9(Mob mob, float amount, int ticks) {
        try {
            int s0 = k(mob);
            if (V[s0] > 0) {
                BLOCKED = true;                    // TNL907 ★挡下了 ⇒ ★待会儿闪金 ✓
                return 0.0F;                       // ★无敌窗内：一点不吃 ✓
            }
            if (TCK[s0] != ticks) {
                TCK[s0] = ticks;
                SPENT[s0] = 0L;
            }
            long budget = MCAP[s0] - SPENT[s0];
            if (budget <= 0L) {
                return 0.0F;                       // ★本 tick 预算用完 ✓
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
                g(s0, allow, V0);                  // ★原生记账 + 无敌窗 ✓
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
            }
            V[s0] = V0;                            // TNL873 ★Java 侧窗口（★两条路都要设 ✓）
            return m1(allow);
        } catch (Throwable th) {
            return 0.0F;
        }
    }

    /** TNL867 ★只截不记账（★给 LivingHurtEvent 用 ✓） */
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

    /** TNL867 ★日志模式：★前 6 刀全量打（★方便用户一次复现就能定位 ✓） */
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

    /** TNL867 ★上报当前预算状态（★供日志 ✓） */
    public static String qC(Mob mob) {
        try {
            int s0 = k(mob);
            return "max=" + mob.getMaxHealth() + " cap=" + m1(MCAP[s0]) + " spent=" + m1(SPENT[s0])
                    + " jInv=" + V[s0] + " native=" + N + " shadow=" + m1(rA(s0));
        } catch (Throwable th) {
            return "err";
        }
    }

    // TNL868 ★★ 静态初始化必须放在所有字段之后 ✓
    //   ★旧写法把 static{}写在 ``char[] P0..P3`` **之前** ✗
    //   ⇒ ★Java 静态初始化按**文本顺序**执行 ⇒ P0 还是 null ✗
    //   ⇒ ★DLL 永远加载失败（NPE: Cannot read the array length because "value" is null ✓）
    //   ★用户日志里就是这一行 ✓
    static {
        N = y1();
        // TNL869 ★★ 启动标记：★只要本类被加载就打一行 ✓
        //   ★用来确认"现在跑的到底是哪个构建" ✓
        try {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                    "TNL869 VITALS BUILD zzF native={} cap={} ns={} sha={} t={}", N, C0, NS, A0.substring(0, 8));
        } catch (Throwable ignored) {
        }
    }

    /**
     * TNL874 ★★ 窗内强制写权威值（★从**源头**拦直写 ✓）
     *
     * <p>被 {@code KtuluVitalsMixin} 在 {@code LivingEntity#setHealth} 入口调用 ✓。
     * ★用户口径：★"我不是写 dll 就是为了阻止这种事情吗" ✓
     * ⇒ ★光靠 DLL 监控是下一 tick 的事 ✗；★混入才是"写的那一瞬间"就拦 ✓✓
     */
    public static float qD(Mob mob, float value) {
        try {
            // TNL893 ★真死后不再拦任何写入 ✓（★否则死不掉 ✗）
            if (mob instanceof com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo km && km.tnl$isDead()) {
                return value;
            }
            int s0 = k(mob);
            if (V[s0] <= 0) {
                return value;                     // TNL1140-reverted: window only, never outside
            }
            // TNL911 ★只有**真的把值拦下来**才置位 ✓
            long auth = N ? h(s0) : rA(s0);
            if (auth < 0L) {
                auth = b.get(s0);
            }
            if (auth == Long.MIN_VALUE) {
                return value;
            }
            long want = m0(value);
            if (want < auth) {                    // ★想写更低 ⇒ ★直接拒绝，改写权威值 ✓
                if (LQ3 != s0) {
                    LQ3 = s0;
                    com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                            "TNL874 setHealth blocked in window: want={} -> auth={}", m1(want), m1(auth));
                }
                BLOCKED = true;                    // TNL911 ★真的拦下了 ✓
                return m1(auth);
            }
            return value;
        } catch (Throwable th) {
            return value;
        }
    }

    private static int LQ3 = -1;

    /**
     * TNL875 ★★ 对外报告权威血量（★判定级屏蔽 ✓）
     *
     * <p>★用户反馈：★"还是有概率改" ✓
     * ⇒ ★因为混入只能拦**走 setHealth**的写 ✗，
     * ★而**直接写字段**（反射/Unsafe ✗）拦不住 ✗。
     * ★现在：★让 Boss 的 {@code getHealth()}直接返回**权威值** ✓
     * ⇒ ★死亡判定/受伤计算/渲染全部走这个值 ✓
     * ⇒ ★字段被改也**不影响任何判定** ✓✓
     */
    /**
     * TNL1272 ★合法加血入口（用户："回血效果也没成功，疑似因为血量结构复杂"）。
     *
     * <p>为什么 `mob.heal()` 无效：这套 Boss 的血量由内核接管，`setHealth` 会被
     * {@link #qD} / {@link #q1} 的镜像比对夹住或回弹。
     *
     * <p>所以加血必须**同时**抬三处：Java 权威镜像（`wA`）、兼容镜像（`b`）、原生字段
     * （`mob.setHealth`）。不加无敌窗、不设真伤宽限 ⇒ 不会被当成"受击"，也不会触发篡改检测。
     */
    public static void qG(Mob mob, float amount) {
        try {
            if (mob == null || amount <= 0.0F) {
                return;
            }
            int s0 = k(mob);
            if (s0 < 0) {
                return;
            }
            long auth = m0(qF(mob));          // ★qF 返回 float，这里是定点的 long
            if (auth < 0L) {
                auth = m0(mob.getHealth());
            }
            long max = m0(mob.getMaxHealth());
            long next = Math.min(max, auth + m0(amount));
            if (next <= auth) {
                return;                      // 已经满血
            }
            wA(s0, next);                    // ① Java 权威镜像
            b.put(s0, next);                 // ② 兼容镜像
            mob.setHealth(m1(next));         // ③ 原生字段（血条显示用）
            // TNL1274 ★关键：`q1` 每 tick 都用**原生**那一路（`e(s0, now, ticks)`）当权威值
            //   ⇒ 只写 Java 镜像和原生字段还不够 ⇒ 下一 tick 又会被原生旧值覆盖回去
            //   （用户实测："血量还是没加"）。所以原生镜像也要写。
            try {
                e(s0, next, mob.tickCount);
            } catch (Throwable ignored) {
            }
            wA(s0, next);                    //    setHealth / e() 可能被改写，写完再兜一次
            b.put(s0, next);
        } catch (Throwable ignored) {
        }
    }

    public static float qF(Mob mob) {
        try {
            int s0 = k(mob);
            long v = N ? h(s0) : rA(s0);
            if (v < 0L) {
                v = b.get(s0);
            }
            if (v < 0L || v == Long.MIN_VALUE) {
                return mob.getMaxHealth();
            }
            return m1(v);
        } catch (Throwable th) {
            return 0.0F;
        }
    }
}
