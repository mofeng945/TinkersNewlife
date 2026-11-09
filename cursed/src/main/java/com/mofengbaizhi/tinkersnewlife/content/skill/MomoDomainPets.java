package com.mofengbaizhi.tinkersnewlife.content.skill;

import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Temporary vanilla squid visuals; lifetime and movement belong to this domain instance. */
final class MomoDomainPets {
    private final List<Squid> pets = new ArrayList<>();
    private Vec3 center;
    void spawn(KtuluMomo owner) {
        clear();
        if (!(owner.level() instanceof ServerLevel level)) return;
        center = owner.position();
        for (int i = 0; i < 3; i++) {
            Squid pet = EntityType.SQUID.create(level);
            if (pet == null) continue;
            double angle = i * Math.PI * 2 / 3;
            pet.moveTo(center.x + Math.cos(angle) * 3, center.y + 1, center.z + Math.sin(angle) * 3, 0, 0);
            pet.addTag(KtuluMomo.TNL_MINION_TAG);
            pet.setNoAi(true);
            pet.setNoGravity(true);
            pet.setAirSupply(300);
            if (level.addFreshEntity(pet)) pets.add(pet);
        }
    }
    void tick(KtuluMomo owner) {
        if (center == null) return;
        for (Squid pet : pets) {
            if (!pet.isAlive()) continue;
            pet.setAirSupply(300);
            LivingEntity target = null;
            for (LivingEntity candidate : owner.level().getEntitiesOfClass(LivingEntity.class,
                    owner.getBoundingBox().inflate(8))) {
                if (candidate == owner || !candidate.isAlive() || KtuluMomo.tnl$isMomoMinion(candidate)
                        || candidate.position().distanceToSqr(center) > 64) continue;
                if (target == null || pet.distanceToSqr(candidate) < pet.distanceToSqr(target)) target = candidate;
            }
            Vec3 next;
            if (target != null) {
                next = pet.position().add(target.getBoundingBox().getCenter().subtract(pet.position()).normalize().scale(0.25));
                if (pet.distanceToSqr(target) < 2.25 && owner.tickCount % 20 == 0) {
                    target.knockback(1.8, center.x - target.getX(), center.z - target.getZ());
                }
            } else {
                next = pet.position().add(center.add(0, 1, 0).subtract(pet.position()).scale(0.05));
            }
            Vec3 offset = next.subtract(center);
            if (offset.horizontalDistanceSqr() > 49) next = center.add(offset.normalize().scale(7));
            pet.setPos(next.x, next.y, next.z);
            pet.setDeltaMovement(Vec3.ZERO);
        }
    }
    void clear() {
        for (Squid pet : pets) if (!pet.isRemoved()) pet.discard();
        pets.clear();
        center = null;
    }
}
