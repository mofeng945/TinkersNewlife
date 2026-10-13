package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * TNL877 \u661f\u9668\u60b2\u53f9\u7684\u7bad\uff08\u2605\u7528\u6237\u53e3\u5f84 \u2713\uff09
 *
 * <ul>
 *   <li>\u2605\u5f31\u8ffd\u8e2a\uff1a\u2605**\u53ea\u9884\u6d4b\u4e00\u6b21**\u4f4d\u7f6e \u2713\uff08\u2605\u51fa\u5f26\u65f6\u7b97\u4e00\u6b21\uff0c\u4e4b\u540e\u4e0d\u518d\u4fee\u6b63 \u2717\uff09\uff1b</li>
 *   <li>\u2605\u547d\u4e2d\uff1a\u2605\u975e Boss \u751f\u7269 **\u56fa\u5b9a 8 \u70b9\u771f\u5b9e\u4f24\u5bb3** \u2713\uff1b\u2605Boss \u751f\u7269 **\u751f\u547d\u4e0a\u9650\u7684 0.5%** \u2713\uff1b</li>
 *   <li>\u2605\u5f3a\u5316\u7bad\uff08\u2605\u6982\u7387 10% \u2713\uff09\uff1a\u2605\u4f24\u5bb3 \u00d72 \u2713 \u002b \u2605\u6cbf\u547d\u4e2d\u77e2\u91cf\u65b9\u5411**\u63a8\u529b** \u2713 \u002b \u2605\u628a\u76ee\u6807**\u9489\u5728\u539f\u4f4d** \u2713\uff1b</li>
 *   <li>\u2605\u6d41\u5149\uff1a\u2605\u5ba2\u6237\u7aef\u5c3e\u8ff9\u7c92\u5b50 \u2713\uff08\u2605\u4eff\u98de\u5251 \u2713\uff09\u3002</li>
 * </ul>
 */
public class StarfallArrow extends AbstractArrow {

    private static final EntityDataAccessor<Boolean> DATA_ENHANCED =
            SynchedEntityData.defineId(StarfallArrow.class, EntityDataSerializers.BOOLEAN);
    private static final UUID PIN_UUID = UUID.fromString("7f2c1a44-9c31-4b8e-9f11-2b7a5c6d8e01");
    private static final String PIN_NAME = "tinkersnewlife.starfall_pin";

    /** \u2605\u4e00\u6b21\u6027\u9884\u6d4b\u70b9\uff08\u2605\u51fa\u5f26\u65f6\u7b97\u597d \u2713\uff09 */
    private Vec3 tnl$predicted = null;
    private boolean tnl$homing = false;
    private int tnl$life = 0;
    private Entity tnl$pinned = null;
    private int tnl$pinTicks = 0;

    public StarfallArrow(EntityType<? extends StarfallArrow> type, Level level) {
        super(type, level);
        this.setBaseDamage(0.0D);
        this.pickup = Pickup.DISALLOWED;
    }

    public StarfallArrow(EntityType<? extends StarfallArrow> type, double x, double y, double z, Level level) {
        super(type, x, y, z, level);
        this.setBaseDamage(0.0D);
        this.pickup = Pickup.DISALLOWED;
    }

    public StarfallArrow(EntityType<? extends StarfallArrow> type, LivingEntity shooter, Level level) {
        super(type, shooter, level);
        this.setBaseDamage(0.0D);
        this.pickup = Pickup.DISALLOWED;
        this.entityData.set(DATA_ENHANCED, level.random.nextFloat() < 0.10F);   // \u2605\u5f3a\u5316\u7bad 10% \u2713
    }

    /** \u2605\u8bbe\u5b9a\u5f31\u8ffd\u8e2a\u76ee\u6807\uff08\u2605**\u53ea\u7b97\u4e00\u6b21** \u2713\uff09 */
    public void tnl$setHomingTarget(LivingEntity target) {
        if (target == null) {
            return;
        }
        Vec3 vel = target.getDeltaMovement();
        double dist = this.position().distanceTo(target.position());
        double travel = Math.max(0.2D, dist / 3.0D);          // \u2605\u4f30\u4e00\u4e0b\u98de\u884c\u65f6\u95f4 \u2713
        this.tnl$predicted = target.position().add(vel.scale(travel));   // \u2605\u9884\u6d4b\u70b9 \u2713
        this.tnl$homing = true;
    }

    public boolean tnl$isEnhanced() {
        return this.entityData.get(DATA_ENHANCED);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ENHANCED, false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("TnlEnhanced", this.tnl$isEnhanced());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_ENHANCED, tag.getBoolean("TnlEnhanced"));
    }

    @Override
    public void tick() {
        super.tick();
        this.tnl$life++;
        // \u2605\u5f31\u8ffd\u8e2a\uff1a\u2605\u53ea\u524d 20 tick \u5fae\u8c03\u4e00\u6b21\u9884\u6d4b\u70b9\u65b9\u5411 \u2713\uff08\u2605\u4e4b\u540e\u76f4\u7ebf \u2717\uff09
        if (this.tnl$homing && this.tnl$predicted != null && this.tnl$life < 20 && !this.level().isClientSide) {
            Vec3 want = this.tnl$predicted.subtract(this.position());
            if (want.lengthSqr() > 0.01D) {
                Vec3 now = this.getDeltaMovement();
                Vec3 dir = now.normalize().scale(0.75D).add(want.normalize().scale(0.25D)).normalize();
                this.setDeltaMovement(dir.scale(now.length()));
            }
        }
        // \u2605\u6d41\u5149\uff08\u2605\u53ea\u5728\u5ba2\u6237\u7aef\u753b \u2713\uff09
        if (this.level().isClientSide) {
            Vec3 v = this.getDeltaMovement();
            double sp = v.length();
            int n = tnl$isEnhanced() ? 4 : 2;
            for (int i = 0; i < n; i++) {
                this.level().addParticle(
                        tnl$isEnhanced() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.END_ROD,
                        this.getX() - v.x * 0.3D * i, this.getY() - v.y * 0.3D * i, this.getZ() - v.z * 0.3D * i,
                        v.x * 0.15D, v.y * 0.15D + 0.01D, v.z * 0.15D);
            }
            if (sp > 0.6D) {
                this.level().addParticle(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
            }
        }
        // \u2605\u94c9\u4f4f\uff1a\u2605\u7bad\u8fd8\u5728 \u21d2 \u76ee\u6807\u52a8\u4e0d\u4e86 \u2713
        if (!this.level().isClientSide && this.tnl$pinned != null) {
            this.tnl$pinTicks--;
            if (this.tnl$pinTicks <= 0 || !this.tnl$pinned.isAlive()) {
                tnl$unpin();
            } else if (this.tnl$pinned instanceof net.minecraft.world.entity.Mob mob) {
                mob.setDeltaMovement(0.0D, mob.getDeltaMovement().y, 0.0D);
                mob.getNavigation().stop();          // \u2605\u53ea\u6709 Mob \u6709\u5bfb\u8def \u2713
                mob.hurtMarked = true;
            } else if (this.tnl$pinned instanceof LivingEntity le) {
                le.setDeltaMovement(0.0D, le.getDeltaMovement().y, 0.0D);
                le.hurtMarked = true;
            }
        }
    }

    private void tnl$unpin() {
        if (this.tnl$pinned instanceof LivingEntity le) {
            try {
                var inst = le.getAttribute(Attributes.MOVEMENT_SPEED);
                if (inst != null) {
                    inst.removeModifier(PIN_UUID);
                }
            } catch (Throwable ignored) {
            }
        }
        this.tnl$pinned = null;
        this.tnl$pinTicks = 0;
    }

    @Override
    public void remove(RemovalReason reason) {
        tnl$unpin();
        super.remove(reason);
    }

    /** \u2605\u662f\u5426\u7b97 Boss\uff08\u2605\u7528\u6237\u53e3\u5f84\uff1a\u2605Boss \u6309\u751f\u547d\u4e0a\u9650\u767e\u5206\u6bd4 \u2713\uff09 */
    public static boolean tnl$isBoss(Entity e) {
        if (e instanceof EnderDragon || e instanceof WitherBoss || e instanceof Warden || e instanceof KtuluMomo) {
            return true;
        }
        return e instanceof LivingEntity le && le.getMaxHealth() >= 200.0F;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity hit = result.getEntity();
        if (this.level().isClientSide) {
            return;
        }
        boolean enh = tnl$isEnhanced();
        float dmg;
        if (hit instanceof LivingEntity le) {
            dmg = tnl$isBoss(hit) ? le.getMaxHealth() * 0.005F : 8.0F;   // \u2605Boss 0.5% / \u975e Boss 8 \u2713
            if (enh) {
                dmg = dmg * 2.0F;                                        // \u2605\u5f3a\u5316\u7bad \u00d72 \u2713
            }
        } else {
            dmg = enh ? 16.0F : 8.0F;
        }
        Entity owner = this.getOwner();
        LivingEntity attacker = owner instanceof LivingEntity le2 ? le2 : null;
        // \u2605\u771f\u5b9e\u4f24\u5bb3\uff08\u2605\u8d70\u9879\u76ee\u73b0\u6210\u7684\u4e07\u80fd\u7a7f\u900f \u2713\uff09
        if (hit instanceof LivingEntity target) {
            try {
                com.mofengbaizhi.tinkersnewlife.util.TruePierce.apply(attacker, target, dmg);
            } catch (Throwable th) {
                target.hurt(this.damageSources().arrow(this, attacker), dmg);
            }
            if (enh) {
                // \u2605\u63a8\u529b\uff1a\u2605\u65b9\u5411 = \u547d\u4e2d\u77e2\u91cf \u2713
                Vec3 v = this.getDeltaMovement();
                Vec3 dir = v.lengthSqr() > 0.001D ? v.normalize() : this.position().subtract(target.position()).normalize();
                target.push(dir.x * 1.6D, 0.45D, dir.z * 1.6D);
                target.hurtMarked = true;
                // \u2605\u94c9\u5728\u539f\u4f4d \u2713
                try {
                    var inst = target.getAttribute(Attributes.MOVEMENT_SPEED);
                    if (inst != null) {
                        inst.removeModifier(PIN_UUID);
                        inst.addTransientModifier(new AttributeModifier(
                                PIN_UUID, PIN_NAME, -1.0D, AttributeModifier.Operation.MULTIPLY_TOTAL));
                    }
                } catch (Throwable ignored) {
                }
                this.tnl$pinned = target;
                this.tnl$pinTicks = 100;                                  // \u26055 \u79d2 \u2713
            }
        }
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.2D, 0.2D, 0.2D));
        this.discard();
    }

    @Override
    protected ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }
}
