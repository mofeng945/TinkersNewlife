package com.mofengbaizhi.tinkersnewlife.content.skill;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * TNL1082 Astral Resonance, a Momo boss skill, modelled on the Warden.
 *
 * <p>Rule from the user: when a target stays closer than two blocks for more than thirty seconds,
 * Momo roots itself in place and charges for two seconds, then shrieks and knocks the target beyond
 * ten blocks. Everything here uses vanilla APIs only, so nothing inside KtuluMomo has to change.
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class AstralResonanceHandler {

    private static final double NEAR_RANGE = 2.0D;
    private static final int NEAR_TICKS_REQUIRED = 600;    // 30 s at 20 tps
    private static final int CHARGE_TICKS = 40;            // 2 s
    private static final int COOLDOWN_TICKS = 200;         // 10 s before it can build up again
    private static final double KNOCK_DISTANCE = 11.0D;
    private static final float SHRIEK_DAMAGE = 10.0F;

    private static final Map<UUID, Integer> NEAR = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> CHARGING_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new ConcurrentHashMap<>();

    private AstralResonanceHandler() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            long now = level.getGameTime();
            // TNL1085 there is no (Class, Predicate) overload on ServerLevel, so filter by hand
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e instanceof KtuluMomo momo && momo.isAlive()) {
                    tickOne(level, momo, now);
                }
            }
        }
    }

    private static void tickOne(ServerLevel level, KtuluMomo momo, long now) {
        UUID id = momo.getUUID();

        // already charging: keep it rooted and shriek when the charge is done
        Long until = CHARGING_UNTIL.get(id);
        if (until != null) {
            if (now < until) {
                momo.setDeltaMovement(Vec3.ZERO);
                momo.hurtMarked = true;
                if (momo.getNavigation() != null) {
                    momo.getNavigation().stop();
                }
                level.sendParticles(ParticleTypes.SCULK_SOUL,
                        momo.getX(), momo.getY() + 1.2D, momo.getZ(), 3, 0.3D, 0.4D, 0.3D, 0.01D);
                return;
            }
            CHARGING_UNTIL.remove(id);
            shriek(level, momo, now);
            momo.tnl$setCharging(false);
            return;
        }

        Long cd = COOLDOWN_UNTIL.get(id);
        if (cd != null && now < cd) {
            return;
        }

        LivingEntity target = momo.getTarget();
        if (target == null || !target.isAlive()) {
            NEAR.remove(id);
            return;
        }
        if (momo.distanceTo(target) > NEAR_RANGE) {
            NEAR.remove(id);
            return;
        }
        int t = NEAR.merge(id, 5, Integer::sum);
        if (t >= NEAR_TICKS_REQUIRED) {
            NEAR.remove(id);
            CHARGING_UNTIL.put(id, now + CHARGE_TICKS);
            // Warden style wind up: root in place, charge sound, soul particles
            momo.setDeltaMovement(Vec3.ZERO);
            momo.hurtMarked = true;
            if (momo.getNavigation() != null) {
                momo.getNavigation().stop();
            }
            momo.tnl$setCharging(true);   // TNL1086 (entity data turned out not to reach the client)
            // TNL1088 the log proved the client never saw the synced flag, so mirror the charge into
            // the hidden marker cooldown as well: that channel is known to reach the owning client.
            for (net.minecraft.world.entity.player.Player near : level.getEntitiesOfClass(
                    net.minecraft.world.entity.player.Player.class, momo.getBoundingBox().inflate(12.0D))) {
                near.getCooldowns().addCooldown(
                        com.mofengbaizhi.tinkersnewlife.content.ModItems.AEONIAN_MARKER.get(), CHARGE_TICKS + 5);
            }
            level.playSound(null, momo.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE,
                    SoundSource.HOSTILE, 2.0F, 1.0F);
            level.sendParticles(ParticleTypes.SCULK_SOUL,
                    momo.getX(), momo.getY() + 1.2D, momo.getZ(), 40, 0.5D, 0.8D, 0.5D, 0.02D);
            TinkersNewlife.LOGGER.warn("TNL1082 Momo begins Astral Resonance charge on {}",
                    target.getName().getString());
        }
    }

    /** the shriek itself: Warden sonic boom feel, knock the target past ten blocks. */
    private static void shriek(ServerLevel level, KtuluMomo momo, long now) {
        COOLDOWN_UNTIL.put(momo.getUUID(), now + COOLDOWN_TICKS);
        LivingEntity target = momo.getTarget();
        Vec3 origin = momo.position().add(0.0D, 1.0D, 0.0D);
        level.playSound(null, momo.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.HOSTILE, 3.0F, 1.0F);
        if (target == null || !target.isAlive()) {
            TinkersNewlife.LOGGER.warn("TNL1083 Momo shrieked but had no target");
            return;
        }
        Vec3 dir = target.position().subtract(momo.position());
        if (dir.lengthSqr() < 1.0E-4D) {
            dir = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 flat = new Vec3(dir.x, 0.0D, dir.z).normalize();
        // enough horizontal speed to clear ten blocks even with drag
        target.setDeltaMovement(flat.x * 4.2D, 0.45D, flat.z * 4.2D);
        target.hurtMarked = true;
        target.hurt(momo.damageSources().mobAttack(momo), SHRIEK_DAMAGE);
        // draw the boom lance, like the Warden projectile
        Vec3 end = origin.add(flat.scale(KNOCK_DISTANCE));
        for (int i = 0; i <= 30; i++) {
            double f = i / 30.0D;
            level.sendParticles(ParticleTypes.SONIC_BOOM,
                    origin.x + (end.x - origin.x) * f,
                    origin.y + (end.y - origin.y) * f,
                    origin.z + (end.z - origin.z) * f, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        TinkersNewlife.LOGGER.warn("TNL1084 Momo shrieked, knocked {} away along {}",
                target.getName().getString(), flat);
    }
}
