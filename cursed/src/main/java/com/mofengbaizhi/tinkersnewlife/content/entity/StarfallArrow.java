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
    /** TNL933 \u2605\u529b\u91cf\u7b49\u7ea7\uff08\u2605\u6bcf\u7ea7 +10% \u4f24\u5bb3 \u2713\uff09 */
    /** TNL933 \u2605\u5df2\u547d\u4e2d\u8fc7\u7684\u76ee\u6807\uff08\u2605\u7a7f\u900f\u7528 \u2713\uff09 */
    private final java.util.List<net.minecraft.world.entity.Entity> tnl$hits = new java.util.ArrayList<>();

    private static final EntityDataAccessor<Integer> DATA_POWER =
            SynchedEntityData.defineId(StarfallArrow.class, EntityDataSerializers.INT);
    /** TNL933 \u2605\u7a7f\u900f\u7b49\u7ea7\uff08\u2605\u8fd8\u80fd\u518d\u51fb\u4e2d\u51e0\u4e2a\u76ee\u6807 \u2713\uff09 */
    private static final EntityDataAccessor<Integer> DATA_PIERCE =
            SynchedEntityData.defineId(StarfallArrow.class, EntityDataSerializers.INT);
    /** TNL933 \u2605\u706b\u77e2 \u2713 */
    private static final EntityDataAccessor<Boolean> DATA_FLAME =
            SynchedEntityData.defineId(StarfallArrow.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Boolean> DATA_ENHANCED =
            SynchedEntityData.defineId(StarfallArrow.class, EntityDataSerializers.BOOLEAN);

    private static final UUID PIN_UUID = UUID.fromString("7f2c1a44-9c31-4b8e-9f11-2b7a5c6d8e01");

    private int tickCount = 0;
    private boolean shouldRenderTail = true;
    private final LinkedList<Vec3> trailHistory = new LinkedList<>();
    // TNL888 \u2605\u2605 \u65b0\u8ffd\u8e2a\uff08\u2605\u7528\u6237\u53e3\u5f84 \u2713\uff09\uff1a
    //   \u2605\u98de\u884c\u4e2d\u626b\u63cf**\u5468\u56f4 3 \u683c** \u2713 \u21d2 \u2605\u6709\u76ee\u6807\u5c31\u63a5\u4e0a
    //   \u2605\u98de\u5251\u540c\u6b3e\u5236\u5bfc\uff08{@link SwordGuidance} \u2713\uff09\u5f00\u59cb\u8ffd\u8e2a \u2713
    private LivingEntity tnl$target = null;
    private SwordGuidance tnl$guide = null;
    /** \u2605\u626b\u63cf\u534a\u5f84\uff083 \u683c \u2713\uff09 */
    private static final double TNL$SCAN = 3.0D;
    /** TNL889 撞地重新起飞次数上限 */
    private int tnl$bounce = 0;
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
        this.entityData.define(DATA_POWER, 0);
        this.entityData.define(DATA_PIERCE, 0);
        this.entityData.define(DATA_FLAME, false);
    }

    public void setArrowScale(float sc) {
        this.entityData.set(DATA_SCALE, sc);
    }

    public float getArrowScale() {
        return this.entityData.get(DATA_SCALE);
    }

    /** TNL933 \u2605\u5916\u90e8\u8bbe\u7f6e\u529b\u91cf/\u7a7f\u900f/\u706b\u77e2 \u2713 */
    public void tnl$setPower(int v) {
        this.entityData.set(DATA_POWER, v);
    }

    public void tnl$setPierce(int v) {
        this.entityData.set(DATA_PIERCE, v);
    }

    public void tnl$setFlame(boolean v) {
        this.entityData.set(DATA_FLAME, v);
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

    /** TNL888 \u2605\u9501\u5b9a\u76ee\u6807\uff08\u2605\u4f9b\u4ee5\u540e Boss AI \u9884\u9501\u7528 \u2713\uff09\uff1a\u2605\u91cd\u7f6e\u5236\u5bfc\u72b6\u6001 \u2713 */
    public void tnl$setHomingTarget(LivingEntity target) {
        // TNL937 \u2605\u2605 \u7528\u6237\u53cd\u9988\uff1a\u2605"\u4e3a\u4ec0\u4e48\u7bad\u77e2\u9501\u4ed6\u4f1a\u4e00\u76f4\u7a7f\u8fc7\u4e0d\u9020\u6210\u4f24\u5bb3" \u2713
        //   \u2605\u771f\u56e0 \u2717\uff1a\u2605\u6211\u5728\u547d\u4e2d\u540e\u867d\u7136\u89e3\u4e86\u9501 \u2713\uff0c
        //   \u2605\u4f46**\u5236\u5bfc\u6bcf tick \u4f1a\u91cd\u65b0\u626b\u63cf\u5e76\u91cd\u65b0\u9501\u5b9a** \u2717 \u21d2
        //   \u2605\u53c8\u9501\u56de\u540c\u4e00\u53ea \u2717 \u21d2 \u2605\u4e8e\u662f\u4e00\u76f4\u7a7f\u8fc7\u53bb\uff08\u2605\u800c\u5df2\u547d\u4e2d\u8fc7\u7684\u76ee\u6807\u4e0d\u91cd\u590d\u4f24\u5bb3 \u2713\uff09\u3002
        //   \u2605\u73b0\u5728\uff1a\u2605\u5728**\u9501\u7684\u5165\u53e3\u5c31\u62d2\u7edd**\u5df2\u547d\u4e2d\u8fc7\u7684\u76ee\u6807 \u2713
        //   \u21d2 \u2605\u5b83\u53ea\u80fd\u53bb\u9501\u522b\u7684\u76ee\u6807 \u002b \u2605\u6216\u8005\u76f4\u7ebf\u98de \u2713\u2713
        if (target != null && this.tnl$hits.contains(target)) {
            return;
        }
        if (target == null) {
            return;
        }
        this.tnl$target = target;
        if (this.tnl$guide != null) {
            this.tnl$guide.reset();
        }
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
        // TNL888 \u2605\u2605 \u8ffd\u8e2a\uff1a\u2605\u5148\u626b 3 \u683c \u2713 \u21d2 \u2605\u627e\u5230\u5c31\u8d70\u98de\u5251\u5236\u5bfc \u2713
        if (!this.level().isClientSide) {
            if (this.tnl$target == null || !this.tnl$target.isAlive()) {
                this.tnl$target = tnl$scanTarget();
                if (this.tnl$target != null && this.tnl$guide != null) {
                    this.tnl$guide.reset();
                }
            }
            if (this.tnl$target != null && this.tnl$target.isAlive()) {
                if (this.tnl$guide == null) {
                    this.tnl$guide = new SwordGuidance();   // \u2605\u98de\u5251\u540c\u6b3e\u5236\u5bfc \u2713
                }
                this.setNoGravity(true);          // TNL889 追踪期间不吃重力
                if (this.inGround && this.tnl$bounce < 20) {
                    this.tnl$bounce++;            // TNL889 撞地不消失，继续追
                    this.inGround = false;
                }
                Vec3 targetPos = this.tnl$target.position()
                        .add(0.0D, this.tnl$target.getBbHeight() * 0.5D, 0.0D);
                Vec3 velocity = this.tnl$guide.guide(this.position(), this.getDeltaMovement(), targetPos);
                this.setDeltaMovement(velocity);            // \u2605\u7167\u642c\u98de\u5251\u7684\u7528\u6cd5 \u2713
            } else if (!this.isNoGravity() && !this.inGround && !this.isInWater()) {
                // \u2605\u6ca1\u76ee\u6807\u65f6\u624d\u5403 40% \u91cd\u529b\uff08\u2605\u7167\u642c\u53c2\u8003 \u2713\uff09
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, 0.03D, 0.0D));
            }
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

    /** TNL882 \u2605\u2605Boss \u5224\u5b9a\uff1a\u2605\u770b"\u6709\u6ca1\u6709 Boss \u8840\u6761\u4e8b\u4ef6" \u2713
     *
     * <p>\u2605\u7528\u6237\u53e3\u5f84\uff1a\u2605"boss\u68c0\u6d4b\u6309\u6709\u6ca1\u6709 boss \u4e8b\u4ef6\u5224\u65ad" \u2713
     * \u21d2 \u2605\u53cd\u5c04\u626b\u63cf\u8be5\u5b9e\u4f53\u7c7b\uff08\u542b\u7236\u7c7b\uff09\u7684\u5b57\u6bb5 \u2713\uff1a
     * \u53ea\u8981\u6709\u4efb\u4f55 {@link net.minecraft.world.BossEvent} \u7c7b\u578b\u7684\u5b57\u6bb5
     * \uff08\u2605\u539f\u7248\u672b\u5f71\u9f99/\u51cb\u7075 \u2713 \u2605\u672c\u6a21\u7ec4 KtuluMomo \u2713 \u2605\u5176\u4ed6\u6a21\u7ec4\u6709\u8840\u6761\u7684 Boss \u2713\uff09
     * \u21d2 \u2605\u5c31\u7b97 Boss \u2713\uff1b\u2605\u5047\u4eba/\u8bad\u7ec3\u6728\u4eba\u6ca1\u6709\u8fd9\u79cd\u5b57\u6bb5 \u2717 \u21d2 \u2605\u4e0d\u7b97 Boss \u2713\u3002
     * \u2605\u7ed3\u679c\u6309**\u5b9e\u4f53\u7c7b\u578b\u7f13\u5b58** \u2713\uff08\u2605\u53cd\u5c04\u53ea\u505a\u4e00\u6b21 \u2713\uff09\u3002
     */
    private static final java.util.Map<net.minecraft.world.entity.EntityType<?>, Boolean> TNL$BOSS_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean tnl$isBoss(Entity e) {
        try {
            net.minecraft.world.entity.EntityType<?> type = e.getType();
            Boolean hit = TNL$BOSS_CACHE.get(type);
            if (hit != null) {
                return hit;
            }
            boolean boss = false;
            Class<?> c = e.getClass();
            while (c != null && c != Object.class) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (net.minecraft.world.BossEvent.class.isAssignableFrom(f.getType())) {
                        boss = true;
                        break;
                    }
                }
                if (boss) {
                    break;
                }
                c = c.getSuperclass();
            }
            TNL$BOSS_CACHE.put(type, boss);
            return boss;
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
        // TNL933 \u2605\u7a7f\u900f\uff1a\u2605\u540c\u4e00\u4e2a\u76ee\u6807\u53ea\u6253\u4e00\u6b21 \u2713
        if (this.tnl$hits.contains(target)) {
            // TNL936 \u2605\u2605 \u7528\u6237\u53cd\u9988\uff1a\u2605"\u7bad\u77e2\u7ee7\u7eed\u8ffd\u8e2a\u539f\u672c\u76ee\u6807\uff0c\u4f46\u662f\u4e00\u76f4\u5728\u4ed6\u8eab\u4e0a\u7ed5\u5708\uff0c\u6ca1\u6709\u4f24\u5bb3" \u2713
            //   \u2605\u771f\u56e0 \u2717\uff1a\u2605\u5236\u5bfc\u9501**\u6ca1\u89e3** \u2717 \u21d2
            //   \u2605\u5b83\u4e00\u76f4\u76ef\u7740\u5df2\u7ecf\u6253\u8fc7\u7684\u76ee\u6807\u8f6c\u5708 \u2717\uff08\u2605\u800c\u540c\u4e00\u76ee\u6807\u53c8\u4e0d\u91cd\u590d\u4f24\u5bb3 \u2713\uff09
            //   \u2605\u73b0\u5728\uff1a\u2605\u7acb\u523b\u89e3\u9501 \u2713 \u21d2 \u2605\u76f4\u7ebf\u98de\u51fa\u53bb\u6253\u4e0b\u4e00\u4e2a \u2713
            this.tnl$setHomingTarget(null);
            return;
        }
        this.tnl$hits.add(target);
        // TNL935 \u2605\u2605 \u63a2\u9488\uff1a\u2605\u770b"\u7b2c\u4e8c\u6b21\u547d\u4e2d\u6709\u6ca1\u6709\u8fdb\u6765" \u2713
        com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                "TNL935 hit #{} target={} pierceLeft={} inGround={} removed={}",
                this.tnl$hits.size(), target.getName().getString(),
                this.entityData.get(DATA_PIERCE), this.inGround, this.isRemoved());
        // TNL933 \u2605\u706b\u77e2\uff1a\u2605\u547d\u4e2d\u70b9\u706b 5 \u79d2 \u2713
        if (this.entityData.get(DATA_FLAME)) {
            target.setSecondsOnFire(5);
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
        // TNL932 \u2605\u2605 \u7528\u6237\u53e3\u5f84\uff08\u4fee\u6539\u4f24\u5bb3\uff09\uff1a
        //   \u2605Boss \u751f\u7269 \u21d2 \u2605\u6bcf\u7bad**\u56fa\u5b9a 3% \u4e0a\u9650\u771f\u4f24** \u2713
        //   \u2605\u975e Boss \u21d2 \u2605\u6bcf\u7bad**\u56fa\u5b9a 10% \u751f\u547d\u4e0a\u9650\u771f\u4f24** \u2713
        //     \u2605\u4f46**\u4e0d\u5f97\u4f4e\u4e8e 16** \u2713\uff08\u2605\u4f4e\u4e8e\u5c31\u76f4\u63a5\u7b97 16 \u2713\uff09
        float dmg = tnl$isBoss(target)
                ? target.getMaxHealth() * 0.03F
                : Math.max(16.0F, target.getMaxHealth() * 0.10F);
        dmg *= 1.0F + 0.10F * this.entityData.get(DATA_POWER);   // TNL933 \u2605\u529b\u91cf\uff1a\u2605\u6bcf\u7ea7 +10% \u2713   // TNL882 \u2605\u975e Boss 16 \u2713 \u2605Boss 3% \u2713
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
            // TNL928 \u2605\u2605 \u4fee\u6b63\uff1a\u2605\u4e0a\u4e00\u7248\u6211\u628a\u5b83\u9519\u63d2\u5728 **catch** \u91cc \u2717
            //   \u2605\u800c\u771f\u4f24\u8d70\u7684\u662f ``TruePierce.apply`` \u90a3\u6761**\u6b63\u5e38\u8def\u5f84** \u2713
            //   \u21d2 \u2605catch \u4ece\u4e0d\u6267\u884c \u21d2 \u2605\u9ed1\u7bad\u4ece\u6765\u6ca1\u624e\u4e0a \u2713\uff08\u2605\u7528\u6237\u5b9e\u6d4b\uff1a\u2605"\u9ed1\u7bad\u5b9e\u4f53\u6ca1\u51fa\u73b0\uff0c\u4e5f\u6ca1\u6210\u529f\u51cf\u901f\u4f7f\u5f92" \u2713\uff09
            //   \u2605\u73b0\u5728\uff1a\u2605\u653e\u8fdb ``if (enh)`` \u91cc \u2713 \u21d2 \u2605\u53ea\u6709\u5f3a\u5316\u7bad\u624d\u624e \u2713
            com.mofengbaizhi.tinkersnewlife.content.entity.StuckArrowManager.apply(target);
            Vec3 v = this.getDeltaMovement();
            Vec3 dir = v.lengthSqr() > 0.001D ? v.normalize() : this.position().subtract(target.position()).normalize();
            target.push(dir.x * 1.6D, 0.45D, dir.z * 1.6D);
            target.hurtMarked = true;
            try {
                var inst = target.getAttribute(Attributes.MOVEMENT_SPEED);
                if (inst != null) {
                    inst.removeModifier(PIN_UUID);
                    inst.addTransientModifier(new AttributeModifier(
                            PIN_UUID, "tinkersnewlife.starfall_pin", 0.0D, AttributeModifier.Operation.MULTIPLY_TOTAL));
                }
            } catch (Throwable ignored) {
            }
            this.tnl$pinned = target;
            this.tnl$pinTicks = 100;
        }
        // TNL933 \u2605\u7a7f\u900f\uff1a\u2605\u8fd8\u6709\u4f59\u989d\u5c31\u4e0d\u6d88\u5931 \u2713
        int pierce = this.entityData.get(DATA_PIERCE);
        if (pierce > 0) {
            this.entityData.set(DATA_PIERCE, pierce - 1);
            // TNL935 \u2605\u2605 \u4fee\u6b63\uff1a\u2605\u786e\u4fdd\u5b83**\u771f\u7684\u7ee7\u7eed\u98de** \u2713
            this.inGround = false;
            this.hasImpulse = true;
            this.pickup = AbstractArrow.Pickup.DISALLOWED;
            this.tnl$setHomingTarget(null);                    // TNL936 \u2605\u7a7f\u900f\u540e\u4e5f\u89e3\u9501 \u2713
            net.minecraft.world.phys.Vec3 tnl$mv = this.getDeltaMovement();
            double tnl$sp = tnl$mv.length();
            if (tnl$sp < 1.2D) {                               // \u2605\u4fdd\u5e95\u901f\u5ea6 \u2713
                net.minecraft.world.phys.Vec3 tnl$dir = tnl$sp < 1.0E-4D
                        ? this.getViewVector(1.0F) : tnl$mv.scale(1.0D / tnl$sp);
                this.setDeltaMovement(tnl$dir.scale(1.2D));
            }
        } else {
            this.discard();
        }
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

    /** TNL888 \u2605\u626b\u63cf**\u5468\u56f4 3 \u683c**\u5185\u7684\u53ef\u8ffd\u76ee\u6807\uff08\u2605\u6700\u8fd1\u4f18\u5148 \u2713\uff09 */
    private LivingEntity tnl$scanTarget() {
        try {
            java.util.List<LivingEntity> found = this.level().getEntitiesOfClass(
                    LivingEntity.class, this.getBoundingBox().inflate(TNL$SCAN));
            LivingEntity best = null;
            double bestDist = Double.MAX_VALUE;
            for (LivingEntity le : found) {
                if (le == this.getOwner() || !le.isAlive() || le.isSpectator()) {
                    continue;
                }
                if (!this.canHitEntity(le)) {
                    continue;
                }
                double d = le.distanceToSqr(this);
                if (d < bestDist) {
                    bestDist = d;
                    best = le;
                }
            }
            return best;
        } catch (Throwable th) {
            return null;
        }
    }
}
