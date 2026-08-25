package com.mofengbaizhi.tinkersnewlife.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class KnockbackHelper {

    private KnockbackHelper() {}

    public static void applyStrongKnockback(LivingEntity target, Player attacker, double strength, double upward) {
        if (target == null || target.level().isClientSide) return;
        Vec3 direction = target.position().subtract(attacker.position()).normalize();
        Vec3 horizontal = new Vec3(direction.x, 0, direction.z).normalize();
        Vec3 velocity = horizontal.scale(strength).add(0, upward, 0);
        target.setDeltaMovement(velocity);
        target.hurtMarked = true;
    }
}