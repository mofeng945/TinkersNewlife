package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.hook.ranged.ProjectileLaunchModifierHook;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;

/** Snapshot the actual launcher/thrown tool's modifier at launch, including secondary multishot arrows. */
public enum SoulBodyProjectileHandler implements ProjectileLaunchModifierHook {
    INSTANCE;

    /** Implemented by the Projectile mixin. Ordinary projectiles always have a zero budget. */
    public interface Flight {
        void tnl$startSoulFlight(float distance);
        float tnl$soulDistance();
        void tnl$traceLength(double length);
        boolean tnl$phaseThroughThisStep();
    }

    @Override public void onProjectileLaunch(IToolStackView tool, ModifierEntry entry, LivingEntity shooter,
            Projectile projectile, @Nullable AbstractArrow arrow, ModDataNBT data, boolean primary) {
        if (!projectile.level().isClientSide && com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader.isIceAndFire()
                && projectile instanceof Flight flight && entry.getLevel() > 0) {
            flight.tnl$startSoulFlight(5f * entry.getLevel());
        }
    }

    public static boolean suppressGravity(Entity entity) {
        return entity instanceof Flight flight && flight.tnl$phaseThroughThisStep();
    }

    /** Vanilla still checks entities on the ENTIRE segment, but starts checking blocks only beyond the budget. */
    public static Vec3 blockTraceStart(Vec3 from, Vec3 to, Entity entity) {
        if (!(entity instanceof Flight flight) || !from.equals(entity.position())) return from;
        double left = flight.tnl$soulDistance();
        if (!(left > 0)) return from;
        Vec3 segment = to.subtract(from);
        double length = segment.length();
        flight.tnl$traceLength(length);
        return length <= left ? to : from.add(segment.scale(left / length));
    }
}
