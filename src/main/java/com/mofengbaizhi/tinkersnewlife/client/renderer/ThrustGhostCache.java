package com.mofengbaizhi.tinkersnewlife.client.renderer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * ⭐ §1166 <b>突刺残影的客户端缓存</b>（用户口径 ✓：
 * 「**客户端收到后存缓存（每个残影存在0.35秒）**」✓）。
 *
 * <h2>⭐ 存活时长 0.35 秒怎么算</h2>
 * ⭐ 20 tick/秒 × 0.35 ＝ ⭐ **7 tick** ✓（⭐ `LIFETIME_TICKS` ✓）。
 * ⚠ 服务端**每 2 tick** 广播一次 ✗ ⇒ ⭐ 冲刺 5 tick 只会产生 **3 个左右**残影 ✓
 * （⭐ 刚好够"拖影"又不糊成一团 ✓）。
 *
 * <h2>⚠ 时间基准用"tick ＋ partialTick"✗ 不能用系统时间 ✓</h2>
 * ⭐ 用 ⭐ `Minecraft.getInstance().player.tickCount` 当基准 ✗ ⇒ ⭐ 暂停游戏时残影**也冻住** ✓
 * （⭐ 用 `System.nanoTime()` 的话暂停时残影会自顾自淡完 ✓ —— ⭐ 那是错的 ✓）。
 *
 * <h2>⚠ 线程</h2>
 * ⭐ 收包走 `enqueueWork` ⇒ ⭐ 本来就是**主线程** ✓ ⇒ ⭐ 这里用普通 `ArrayList` ＋ `synchronized` 双保险 ✓
 * （⭐ 渲染也在主线程 ✓ 但要防止将来有人在别的线程读 ✓）。
 */
public final class ThrustGhostCache {

    private ThrustGhostCache() {
    }

    /** ⭐ 一个残影的存活 tick 数（⭐ 0.35 秒 ＝ 7 tick ✓） */
    public static final int LIFETIME_TICKS = 7;

    /** ⭐ 一条残影 ✓（⚠ 全部是**广播那一刻**的快照 ✗ 不跟随实体 ✓） */
    public static final class Ghost {
        public final int entityId;
        public final double x;
        public final double y;
        public final double z;
        public final float yaw;
        public final float pitch;
        /** ⭐ 出生时的客户端 tick ✓（用来算存活进度 ✓） */
        public final int bornTick;

        Ghost(int entityId, double x, double y, double z, float yaw, float pitch, int bornTick) {
            this.entityId = entityId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.bornTick = bornTick;
        }

        /**
         * ⭐ 存活进度：0 ＝ 刚出生（⭐ 最实 ✓）→ 1 ＝ 该消失（⭐ 全透明 ✓）。
         * <p>⚠ 取不到当前 tick 时返回 1 ✓（⭐ 宁可不画 ✗ 也不要画一个不淡的僵尸残影 ✓）。
         */
        public float progress(int nowTick) {
            if (nowTick < 0) {
                return 1.0F;
            }
            int age = nowTick - this.bornTick;
            if (age <= 0) {
                return 0.0F;
            }
            return Math.min(1.0F, (float) age / (float) LIFETIME_TICKS);
        }
    }

    private static final List<Ghost> GHOSTS = new ArrayList<>();

    /**
     * ⭐ 收到包时调 ✓（⭐ 主线程 ✓ 见类注释 ✓）—— ⭐ 记下这一刻的位置/朝向 ✓。
     */
    public static void add(int entityId, double x, double y, double z, float yaw, float pitch) {
        int now = currentTick();
        synchronized (GHOSTS) {
            GHOSTS.add(new Ghost(entityId, x, y, z, yaw, pitch, now));
        }
    }

    /**
     * ⭐ 取**当前该画**的残影（⭐ 顺手把过期的清掉 ✓）✗
     * ⇒ ⭐ 返回的是内部列表的**快照拷贝** ✓（⭐ 免得渲染时被收包线程改 ✓）。
     */
    public static List<Ghost> alive() {
        int now = currentTick();
        List<Ghost> out = new ArrayList<>();
        synchronized (GHOSTS) {
            Iterator<Ghost> it = GHOSTS.iterator();
            while (it.hasNext()) {
                Ghost g = it.next();
                // ⚠ 过期就丢 ✗（⭐ 存活 0.35 秒 ✓ 见类注释 ✓）
                if (g.progress(now) >= 1.0F) {
                    it.remove();
                } else {
                    out.add(g);
                }
            }
        }
        return out;
    }

    /** ⭐ 客户端当前 tick ✓（⭐ 拿不到就 -1 ⇒ ⭐ 残影一律判为过期 ✓ 不会画错 ✓） */
    private static int currentTick() {
        try {
            net.minecraft.client.player.LocalPlayer p = net.minecraft.client.Minecraft.getInstance().player;
            return p == null ? -1 : p.tickCount;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    /** ⭐ 退出世界时清干净 ✓（⚠ 否则下次进世界会看到上次的鬼影 ✗） */
    public static void clear() {
        synchronized (GHOSTS) {
            GHOSTS.clear();
        }
    }
}
