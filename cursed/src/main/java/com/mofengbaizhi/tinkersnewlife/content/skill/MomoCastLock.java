package com.mofengbaizhi.tinkersnewlife.content.skill;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TNL1154 user: "too messy, while one skill is being cast no other skill may start".
 *
 * One shared lock per momo. A skill claims it when it starts a cast and releases it when the cast is
 * over; every skill checks it before starting. Also carries the "when may this momo cast again" stamp
 * so a finished cast cannot immediately restart (the chord used to set its own cooldown at the START
 * of a thirty second cast, so it expired mid cast and it fired back to back).
 */
public final class MomoCastLock {

    private static final Map<UUID, Long> BUSY_UNTIL = new ConcurrentHashMap<>();
    /** TNL1166 where the boss stood when the cast began, used to pin it there */
    private static final Map<UUID, net.minecraft.world.phys.Vec3> ANCHOR = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> NEXT_CAST = new ConcurrentHashMap<>();

    private MomoCastLock() {
    }

    /** true while some skill of this momo is casting */
    public static boolean isCasting(UUID id, long now) {
        Long until = BUSY_UNTIL.get(id);
        if (until == null) {
            return false;
        }
        if (now >= until) {
            BUSY_UNTIL.remove(id);
            return false;
        }
        return true;
    }

    /**
     * TNL1157 user: cooldowns and animations are independent per skill; the lock must only stop two
     * skills casting at the same time. It no longer carries a cooldown of its own.
     */
    public static boolean canStart(UUID id, long now) {
        return !isCasting(id, now);
    }

    /** claim the lock for this many ticks */
    public static void begin(UUID id, long now, int ticks) {
        BUSY_UNTIL.put(id, now + ticks);
    }

    /** TNL1166 remember where it should stay during the cast */
    public static void anchor(UUID id, net.minecraft.world.phys.Vec3 pos) {
        if (pos != null) {
            ANCHOR.putIfAbsent(id, pos);
        }
    }

    public static net.minecraft.world.phys.Vec3 anchorOf(UUID id) {
        return ANCHOR.get(id);
    }

    /** release the lock and start the cooldown */
    public static void end(UUID id, long now, int cooldownTicks) {
        BUSY_UNTIL.remove(id);
        ANCHOR.remove(id);
        NEXT_CAST.put(id, now + cooldownTicks);
    }

    /** keep the lock alive while a cast runs, and refresh the cooldown once it is over */
    public static void hold(UUID id, long now, int cooldownTicks) {
        Long until = BUSY_UNTIL.get(id);
        if (until != null && now >= until) {
            end(id, now, cooldownTicks);
        }
    }
}
