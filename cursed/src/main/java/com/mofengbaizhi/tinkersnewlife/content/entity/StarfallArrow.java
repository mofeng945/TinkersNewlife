package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.network.starfall.PacketStarfallHit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraftforge.network.PacketDistributor;

import java.util.LinkedList;
import java.util.UUID;

/**
 * TNL879 \u661f\u9668\u4e4b\u7bad\uff08\u2605\u7167\u642c\u53c2\u8003\u5b9e\u73b0 HomingArrow \u2713\uff09
 *
 * <p>\u2605\u53c2\u8003\u5b9e\u73b0\u91cc\u7bad\u5934\u4f53**\u6ca1\u6709\u8ffd\u8e2a\u903b\u8f91** \u2717 \u2014\u2014
 * \u2605"\u8ffd\u8e2a"\u7684\u89c2\u611f\u6765\u81ea**\u7ba1\u72b6\u6d41\u5149\u5c3e\u8ff9** \u002b **\u547d\u4e2d\u661f\u73af\u002f\u4e94\u661f** \u2713\uff08\u89c1 StarfallRenderHandler \u2713\uff09\u3002
 * \u2605\u672c\u7c7b\u53e6\u5916\u4fdd\u7559\u7528\u6237\u53e3\u5f84\uff1a\u2605\u5f31\u8ffd\u8e2a\uff08\u4ec5\u9884\u6d4b\u4e00\u6b21 \u2713\uff09\u002b \u2605\u771f\u5b9e\u4f24\u5bb3\uff08\u975e Boss 8 \u70b9\uff0fBoss \u4e0a\u9650 0.5%\uff09\u002b \u2605 10% \u5f3a\u5316\u7bad\u3002
 */
public class StarfallArrow extends AbstractArrow {

    private static final String TAG_BATCH = "tnl_starfall_batch";
    private static final int MAX_HISTORY = 16;
    private static final int MAX_LIFE = 200;

    private static final EntityDataAccessor<Float> DATA_SCALE =
            SynchedEntityData.defineId(StarfallArrow.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_ENHANCED =
            SynchedEntityData.defineId(StarfallArrow.class, EntityDataSerializers.BOOLEAN);

    private static final UUID PIN_UUID = UUID.fromString("7f2c1a44-9c31-4b8e-9f11-2b7a5c6d8e01");

    private int tickCount = 0;
    private boolean shouldRenderTail = true;
    private final LinkedList<Vec3> trailHistory = new LinkedList<>();
    private Vec3 tnl$predicted = null;
    private boolean tnl$homing = false;
    private Entity tnl$pinned = null;
    private int tnl$pinTicks = 0;

    public StarfallArrow(EntityType<? extends StarfallArrow> type, Level level) {
        super(type, level);
        this.setBaseDamage(0.0D);
        this.pickup = Pickup.DISALLOWED;
    }

    public StarfallArrow(EntityType<? extends StarfallArrow> type, LivingEntity shooter, Level level) {
        super(type, shooter, level);
        this.setBaseDamage(0.0D);
        this.pickup = Pickup.DISALLOWED;
        this.entityData.set(DATA_ENHANCED, level.random.nextFloat() < 0.10F);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_SCALE, 1.0F);
        this.entityData.define(DATA_ENHANCED, false);
    }

    public void setArrowScale(float sc) {
        this.entityData.set(DATA_SCALE, sc);
    }

    public float getArrowScale() {
        return this.entityData.get(DATA_SCALE);
    }

    public boolean tnl$isEnhanced() {
        return this.entityData.get(DATA_ENHANCED);
    }

    public int getTickCount() {
        return tickCount;
    }

    public boolean shouldRenderTail() {
        return shouldRenderTail;
    }

    public LinkedList<Vec3> getTrailHistory() {
        return trailHistory;
    }

    public void setBatchId(long id) {
        this.getPersistentData().putLong(TAG_BATCH, id);
    }

    public long getBatchId() {
        return this.getPersistentData().getLong(TAG_BATCH);
    }

    /** \u2605\u5f31\u8ffd\u8e2a\uff1a\u2605**\u53ea\u9884\u6d4b\u4e00\u6b21** \u2713 */
    public void tnl$setHomingTarget(LivingEntity target) {
        if (target == null) {
            return;
        }
        double dist = this.position().distanceTo(target.position());
        double travel = Math.max(0.2D, dist / 3.0D);
        this.tnl$predicted = target.position().add(target.getDeltaMovement().scale(travel));
        this.tnl$homing = true;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (target == this.getOwner()) {
            return false;
        }
        return super.canHitEntity(target);
    }

    @Override
    public void tick() {
        super.tick();
        this.tickCount++;
        if (this.tickCount > MAX_LIFE || this.inGround) {
            this.discard();
            return;
        }
        this.shouldRenderTail = !this.inGround && this.isAlive();
        if (this.level().isClientSide && this.shouldRenderTail) {
            this.trailHistory.addLast(this.position());
            if (this.trailHistory.size() > MAX_HISTORY) {
                this.trailHistory.removeFirst();
            }
        }
        // \u2605\u4ec5\u524d 20 tick \u5fae\u8c03\u4e00\u6b21\u9884\u6d4b\u70b9\u65b9\u5411 \u2713
        if (this.tnl$homing && this.tnl$predicted != null && this.tickCount < 20 && !this.level().isClientSide) {
            Vec3 want = this.tnl$predicted.subtract(this.position());
            if (want.lengthSqr() > 0.01D) {
                Vec3 now = this.getDeltaMovement();
                Vec3 dir = now.normalize().scale(0.85D).add(want.normalize().scale(0.15D)).normalize();
                this.setDeltaMovement(dir.scale(now.length()));
            }
        }
        // \u2605\u53ea\u5403 40% \u91cd\u529b\uff08\u2605\u7167\u642c\u53c2\u8003 \u2713\uff09
        if (!this.isNoGravity() && !this.inGround && !this.isInWater()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, 0.03D, 0.0D));
        }
        // \u2605\u94c9\u4f4f\uff08\u2605\u7bad\u8fd8\u5728 \u21d2 \u76ee\u6807\u52a8\u4e0d\u4e86 \u2713\uff09
        if (!this.level().isClientSide && this.tnl$pinned != null) {
            this.tnl$pinTicks--;
            if (this.tnl$pinTicks <= 0 || !this.tnl$pinned.isAlive()) {
                this.tnl$unpin();
            } else if (this.tnl$pinned instanceof net.minecraft.world.entity.Mob mob) {
                mob.setDeltaMovement(0.0D, mob.getDeltaMovement().y, 0.0D);
                mob.getNavigation().stop();
                mob.hurtMarked = true;
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
        this.tnl$unpin();
        super.remove(reason);
    }

    /** TNL881 \u2605Boss \u5224\u5b9a\uff08\u2605\u6539\u6210\u6807\u7b7e\u5224\u5b9a \u2713\uff09
     *
     * <p>\u2605\u7528\u6237\u53cd\u9988\uff1a\u2605"\u4e3a\u4ec0\u4e48\u5047\u4eba\u4e5f\u7b97 boss \u751f\u7269" \u2713
     * \u21d2 \u2605\u56e0\u4e3a\u65e7\u5199\u6cd5\u662f"\u751f\u547d\u4e0a\u9650 \u2265 200 \u5c31\u7b97 Boss" \u2717\uff08\u5047\u4eba/\u8bad\u7ec3\u6728\u4eba\u4e5f\u4f1a\u4e2d\u62db \u2717\uff09\u3002
     *
     * <p>\u2605\u73b0\u5728\uff1a\u2605\u539f\u7248 Boss \u7c7b \u002b \u2605\u6570\u636e\u6807\u7b7e
     * {@code tinkersnewlife:bosses} \u2713\uff08\u2605\u8def\u5f84\uff1a
     * {@code data/tinkersnewlife/tags/entity_types/bosses.json} \u2713
     * \u21d2 \u2605\u4f60\u60f3\u52a0\u54ea\u4e2a Boss \u76f4\u63a5\u5f80\u91cc\u9762\u6dfb \u2713\uff09\u3002
     */
    private static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> TNL$BOSSES =
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                    new net.minecraft.resources.ResourceLocation("tinkersnewlife", "bosses"));

    public static boolean tnl$isBoss(Entity e) {
        if (e instanceof EnderDragon || e instanceof WitherBoss || e instanceof KtuluMomo) {
            return true;
        }
        try {
            return e.getType().is(TNL$BOSSES);
        } catch (Throwable th) {
            return false;
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        if (!(hitResult.getEntity() instanceof LivingEntity target)) {
            super.onHitEntity(hitResult);
            return;
        }
        if (this.level().isClientSide) {
            return;
        }
        // \u2605\u547d\u4e2d\u7279\u6548\u5e7f\u64ad\uff08\u2605\u7167\u642c\u53c2\u8003 StarHitPacket \u2713\uff09
        if (this.level() instanceof ServerLevel sl) {
            Vec3 pos = this.position();
            Vec3 dir = this.getDeltaMovement();
            if (dir.lengthSqr() < 1e-6) {
                dir = this.getViewVector(0.0F);
            }
            PacketStarfallHit pkt = new PacketStarfallHit(pos, dir);
            for (ServerPlayer sp : sl.players()) {
                if (sp.distanceToSqr(pos) < 64.0D * 64.0D) {
                    TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), pkt);
                }
            }
        }
        boolean enh = this.tnl$isEnhanced();
        float dmg = tnl$isBoss(target) ? target.getMaxHealth() * 0.005F : 8.0F;
        if (enh) {
            dmg *= 2.0F;
        }
        LivingEntity attacker = this.getOwner() instanceof LivingEntity le ? le : null;
        try {
            com.mofengbaizhi.tinkersnewlife.util.TruePierce.apply(attacker, target, dmg);
        } catch (Throwable th) {
            target.hurt(this.damageSources().magic(), dmg);
        }
        if (enh) {
            Vec3 v = this.getDeltaMovement();
            Vec3 dir = v.lengthSqr() > 0.001D ? v.normalize() : this.position().subtract(target.position()).normalize();
            target.push(dir.x * 1.6D, 0.45D, dir.z * 1.6D);
            target.hurtMarked = true;
            try {
                var inst = target.getAttribute(Attributes.MOVEMENT_SPEED);
                if (inst != null) {
                    inst.removeModifier(PIN_UUID);
                    inst.addTransientModifier(new AttributeModifier(
                            PIN_UUID, "tinkersnewlife.starfall_pin", -1.0D, AttributeModifier.Operation.MULTIPLY_TOTAL));
                }
            } catch (Throwable ignored) {
            }
            this.tnl$pinned = target;
            this.tnl$pinTicks = 100;
        }
        this.discard();
    }

    @Override
    protected ItemStack getPickupItem() {
        return ItemStack.EMPTY;
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
}
