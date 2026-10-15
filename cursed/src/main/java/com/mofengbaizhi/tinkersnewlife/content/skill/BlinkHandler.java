package com.mofengbaizhi.tinkersnewlife.content.skill;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TNL1120 Blink, a Momo skill.
 *
 * <p>Momo locks its gaze on the target, stands perfectly still for 1.5 s and then, with no warning at
 * all, appears three blocks behind it. No particles, no sound, nothing to react to.
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class BlinkHandler {

    private static final int LOCK_TICKS = 30;        // 1.5 s
    private static final double BEHIND = 3.0D;       // three blocks behind the target
    private static final double RANGE = 16.0D;       // only when the target is this close
    private static final int COOLDOWN_TICKS = 400;   // 20 s

    private static final Map<UUID, Long> LOCK_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new ConcurrentHashMap<>();

    private BlinkHandler() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !MomoSkills.BLINK) {
            return;
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            long now = level.getGameTime();
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e instanceof KtuluMomo momo && momo.isAlive()) {
                    tickOne(level, momo, now);
                }
            }
        }
    }

    private static void tickOne(ServerLevel level, KtuluMomo momo, long now) {
        UUID id = momo.getUUID();

        Long until = LOCK_UNTIL.get(id);
        if (until != null) {
            LivingEntity target = momo.getTarget();
            if (now < until && target != null && target.isAlive()) {
                // stand still and keep the eyes on the target, nothing else
                momo.setNoAi(true);
                momo.setDeltaMovement(Vec3.ZERO);
                momo.hurtMarked = true;
                if (momo.getNavigation() != null) {
                    momo.getNavigation().stop();
                }
                momo.getLookControl().setLookAt(target, 30.0F, 30.0F);
                return;
            }
            LOCK_UNTIL.remove(id);
            momo.setNoAi(false);
            if (target != null && target.isAlive()) {
                teleportBehind(level, momo, target);
            }
            return;
        }

        Long cd = COOLDOWN_UNTIL.get(id);
        if (cd != null && now < cd) {
            return;
        }
        LivingEntity target = momo.getTarget();
        if (target == null || !target.isAlive() || momo.distanceTo(target) > RANGE) {
            return;
        }
        LOCK_UNTIL.put(id, now + LOCK_TICKS);
        COOLDOWN_UNTIL.put(id, now + LOCK_TICKS + COOLDOWN_TICKS);
        momo.setNoAi(true);
        momo.setDeltaMovement(Vec3.ZERO);
        momo.hurtMarked = true;
        if (momo.getNavigation() != null) {
            momo.getNavigation().stop();
        }
        TinkersNewlife.LOGGER.warn("TNL1120 Momo locks onto {} and starts the Blink wind up",
                target.getName().getString());
    }

    /** appear three blocks behind the target, facing it, with no warning whatsoever. */
    private static void teleportBehind(ServerLevel level, KtuluMomo momo, LivingEntity target) {
        Vec3 look = target.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        if (flat.lengthSqr() < 1.0E-4D) {
            flat = new Vec3(0.0D, 0.0D, 1.0D);
        }
        flat = flat.normalize();
        Vec3 dest = target.position().subtract(flat.scale(BEHIND));   // opposite of where it looks
        BlockPos bp = BlockPos.containing(dest);
        if (!level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()) {
            dest = target.position();                                // no room, land on the target
        }
        momo.teleportTo(dest.x, dest.y, dest.z);
        momo.setDeltaMovement(Vec3.ZERO);
        momo.hurtMarked = true;
        momo.getLookControl().setLookAt(target, 30.0F, 30.0F);
        momo.setNoAi(false);
        TinkersNewlife.LOGGER.warn("TNL1120 Momo blinked behind {} to ({}, {}, {})",
                target.getName().getString(),
                String.format("%.1f", dest.x), String.format("%.1f", dest.y), String.format("%.1f", dest.z));
    }
}
