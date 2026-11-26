package com.mofengbaizhi.tinkersnewlife.client;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** TNL879 命中特效缓存（★照搬参考实现 ClientStarHitCache ✓） */
public class StarfallHitCache {

    public static class Hit {
        public final Vec3 pos;
        public final Vec3 dir;
        public final long startTick;
        public final int lifeTicks = 10;

        public Hit(Vec3 pos, Vec3 dir, long startTick) {
            this.pos = pos;
            this.dir = dir;
            this.startTick = startTick;
        }

        public float progress(long now) {
            return Math.min(1f, (float) (now - startTick) / lifeTicks);
        }

        public boolean expired(long now) {
            return now - startTick >= lifeTicks;
        }
    }

    private static final List<Hit> HITS = new ArrayList<>();

    public static void add(Vec3 pos, Vec3 dir, long now) {
        HITS.add(new Hit(pos, dir, now));
    }

    public static List<Hit> get() {
        return HITS;
    }

    public static void prune(long now) {
        Iterator<Hit> it = HITS.iterator();
        while (it.hasNext()) {
            if (it.next().expired(now)) {
                it.remove();
            }
        }
    }
}
