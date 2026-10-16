package com.mofengbaizhi.tinkersnewlife.content.skill;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.common.Mod;

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

    private static final int COOLDOWN_TICKS = 200;         // 10 s before it can build up again
    private static final double KNOCK_DISTANCE = 11.0D;

    private static final Map<UUID, Integer> NEAR = new ConcurrentHashMap<>();

    private AstralResonanceHandler() {
    }


    /** kept only as a no-op marker: the entity drives this now */

    /** TNL1090 user: keep the boss melee at zero while the animation is being tested. */


    /** the shriek itself: Warden sonic boom feel, knock the target past ten blocks. */
    public static void shriek(ServerLevel level, KtuluMomo momo, long now) {   // TNL1174 called by ResonanceGoal
        momo.tnl$setResonanceCoolUntil(now + COOLDOWN_TICKS);   // TNL1172 on the entity
        // TNL1164 user: never clear the AI for a wind up or a recovery -- it
        // sticks (teleporting high left the boss frozen two seconds). Rooting is done
        // by zeroing the movement every tick instead, which is already done above.
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
        target.setDeltaMovement(flat.x * 4.6D, 0.50D, flat.z * 4.6D);   // TNL1103 push only, no damage
        target.hurtMarked = true;
        // TNL1103 user: no sonic damage at all, the shriek only launches the target away.
        // draw the boom lance, like the Warden projectile
        Vec3 end = origin.add(flat.scale(KNOCK_DISTANCE));
        for (int i = 0; i <= 30; i++) {
            double f = i / 12.0D;
            level.sendParticles(ParticleTypes.SONIC_BOOM,
                    origin.x + (end.x - origin.x) * f,
                    origin.y + (end.y - origin.y) * f,
                    origin.z + (end.z - origin.z) * f, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        TinkersNewlife.LOGGER.warn("TNL1084 Momo shrieked, knocked {} away along {}",
                target.getName().getString(), flat);
    }
}
