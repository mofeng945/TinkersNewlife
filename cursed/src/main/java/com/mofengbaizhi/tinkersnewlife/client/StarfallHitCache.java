package com.mofengbaizhi.tinkersnewlife.client;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** TNL879 \u547d\u4e2d\u7279\u6548\u7f13\u5b58\uff08\u2605\u7167\u642c\u53c2\u8003\u5b9e\u73b0 ClientStarHitCache \u2713\uff09 */
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
