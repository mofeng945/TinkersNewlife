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
        // TNL1022 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u6700\u5927\u6982\u7387 5%" \u2713\uff08\u2605\u9009\u4e86"\u2605\u5f3a\u5316\u7bad\u603b\u6982\u7387\u5c01\u9876 5%" \u2713\uff09
        //   \u2605\u57fa\u7840 2% \u2713 \u002b \u2605\u6bcf 1 \u70b9\u5e78\u8fd0 +1% \u2713 \u21d2 \u2605**\u4e0a\u9650 5%** \u2713\uff08\u2605\u5e78\u8fd0 +3 \u5c31\u5230\u9876 \u2713\uff09
        //   \u2605\u8d1f\u5e78\u8fd0\u4e5f\u538b\u4e0d\u5230\u8d1f\u6570 \u2713\uff08\u2605\u4e0b\u9650 0 \u2713\uff09
        float tnl$enhChance = 0.02F + (float) shooter.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.LUCK) * 0.01F;
        tnl$enhChance = Math.max(0.0F, Math.min(0.05F, tnl$enhChance));
        this.entityData.set(DATA_ENHANCED, level.random.nextFloat() < tnl$enhChance);
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
    private static final java.util.Map<Class<?>, Boolean> TNL$BOSS_CACHE_BY_CLASS = new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.Map<net.minecraft.world.entity.EntityType<?>, Boolean> TNL$BOSS_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean tnl$isBoss(Entity e) {
        try {
            // TNL982 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u672b\u5f71\u9f99\u6ca1\u6709\u88ab\u5224\u65ad\u4e3a boss \u751f\u7269\uff0c\u51cb\u7075\u5374\u88ab\u5224\u65ad\u4e3a boss \u751f\u7269" \u2713
            //   \u2605\u65e5\u5fd7\u8bc1\u636e\uff1a\u2605\u9f99\u7684**\u90e8\u4ef6** type \u4e5f\u53eb ``entity.minecraft.ender_dragon`` \u2713
            //     \u21d2 \u2605\u7b2c\u4e00\u6b21\u5224\u5b9a\u770b\u7684\u662f\u90e8\u4ef6\uff08\u2605\u6ca1 BossEvent \u2717\uff09\u21d2 \u2605\u628a false
            //     **\u5199\u8fdb\u4e86\u6309 EntityType \u7684\u7f13\u5b58** \u2717 \u21d2 \u2605\u4e4b\u540e\u672c\u4f53\u4e5f\u8fd4\u56de false \u2713\u2713
            //   \u2605\u73b0\u5728\uff1a\u2605\u5148\u628a\u90e8\u4ef6\u6362\u7b97\u6210\u672c\u4f53 \u2713 \u002b \u2605**\u6539\u6210\u6309 `getClass()` \u7f13\u5b58** \u2713
            //     \uff08\u2605\u90e8\u4ef6\u7c7b\u4e0e\u672c\u4f53\u7c7b\u4e0d\u540c \u2713 \u21d2 \u2605\u4e0d\u4f1a\u4e92\u76f8\u6c61\u67d3 \u2713\uff09
            e = com.mofengbaizhi.tinkersnewlife.util.TruePierce.resolveVictim(e);
            Class<?> key = e.getClass();
            Boolean hit = TNL$BOSS_CACHE_BY_CLASS.get(key);
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
            // TNL984 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u672b\u5f71\u9f99\u6ca1\u6709\u88ab\u5224\u65ad\u4e3a boss" \u2713
            //   \u2605\u67e5 jar \u5f97\u5230\uff08\u2605javap \u2713\uff09\uff1a
            //     \u00b7 \u2605\u51cb\u7075 \u2713\uff1a\u2605\u81ea\u5df1\u58f0\u660e\u4e86 ``private final ServerBossEvent`` \u21d2 \u2605\u626b\u5f97\u5230 \u2713
            //     \u00b7 \u2605\u672b\u5f71\u9f99 \u2717\uff1a\u2605**\u9f99\u5b9e\u4f53\u4e0a\u6ca1\u6709\u4efb\u4f55 BossEvent \u5b57\u6bb5** \u2713
            //       \uff08\u2605\u5b83\u7684\u8840\u6761\u5728 ``EndDragonFight`` \u91cc \u2713\uff09\u21d2 \u2605\u6240\u4ee5\u6c38\u8fdc\u626b\u4e0d\u5230 \u2713
            //   \u2605\u73b0\u5728\uff1a\u2605\u660e\u786e\u628a**\u672b\u5f71\u9f99\uff08\u53ca\u5176\u90e8\u4ef6\uff09\u5f53 boss** \u2713
            // TNL985 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u7fa4\u53cb\u8bf4 forge \u6709\u4e13\u95e8\u7684 boss \u6807\u7b7e" \u2713
            //   \u2605\u5df2\u786e\u8bc1\uff08\u2605javap \u002b jar \u91cc\u7684\u8d44\u6e90 \u2713\uff09\uff1a
            //     \u00b7 \u2605``net.minecraftforge.common.Tags$EntityTypes.BOSSES`` \u5b58\u5728 \u2713
            //     \u00b7 \u2605``data/forge/tags/entity_types/bosses.json`` \u5b58\u5728 \u2713
            //   \u2605\u8fd9\u662f"\u6570\u636e\u9a71\u52a8"\u7684\u5224\u636e \u2713 \u21d2 \u2605\u4efb\u4f55\u6574\u5408\u5305/\u6a21\u7ec4\u90fd\u53ef\u4ee5\u5f80\u91cc\u52a0 \u2713
            if (!boss) {
                try {
                    boss = e.getType().is(net.minecraftforge.common.Tags.EntityTypes.BOSSES);
                } catch (Throwable ignored) {
                }
            }
            // TNL987 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u628a\u4e4b\u524d\u7684\u8840\u6761\u53cd\u5c04\u4e8b\u4ef6\u515c\u5e95" \u2713
            //   \u2605\u987a\u5e8f \u2713\uff1a\u2605\u5b57\u6bb5\u626b\u63cf\uff08\u2605\u51cb\u7075 \u2713\uff09\u2192 \u2605forge:bosses \u6807\u7b7e\uff08\u2605\u6570\u636e\u9a71\u52a8 \u2713\uff09
            //     \u2192 \u2605\u8840\u6761\u6ce8\u518c\u8868\uff08\u2605\u670d\u52a1\u7aef\u521b\u5efa \u002b \u2605\u5ba2\u6237\u7aef\u6e32\u67d3 \u2713\uff09\u3002
            //   \u2605\u8fd9\u6761\u5c31\u662f"\u6a21\u7ec4\u6ca1\u5199\u6807\u7b7e\u4f46\u6709\u8840\u6761"\u7684\u515c\u5e95 \u2713\u3002
            if (!boss) {
                try {
                    boss = com.mofengbaizhi.tinkersnewlife.util.BossBarRegistry.isBoss(e.getUUID());
                } catch (Throwable ignored) {
                }
            }
            if (!boss && e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon) {
                boss = true;
            }
            if (boss) {
                TNL$BOSS_CACHE_BY_CLASS.put(key, Boolean.TRUE);   // \u2605\u53ea\u7f13\u5b58"\u662f boss" \u2713
            }
            return boss;
        } catch (Throwable th) {
            return false;
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        // TNL977 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u547d\u4e2d\u591a\u90e8\u4f4d\u5b9e\u4f53\u7684\u975e\u4e3b\u78b0\u649e\u7bb1\u65f6\uff0c\u6ca1\u6709\u9020\u6210\u4f24\u5bb3" \u2713
        //   \u2605\u771f\u56e0 \u2717\uff1a\u2605\u591a\u90e8\u4f4d\u5b9e\u4f53\uff08\u2605\u5de8\u9f99\u7c7b \u2713\uff09\u547d\u4e2d\u7684\u662f
        //     \u2605``PartEntity``\uff08\u90e8\u4ef6 \u2713\uff09\uff0c\u2605\u5b83**\u4e0d\u662f ``LivingEntity``** \u2717
        //     \u21d2 \u2605\u6211\u7b2c\u4e00\u53e5\u5c31 return \u21d2 \u2605\u6ca1\u6253\u5230\u672c\u4f53 \u2713\u2713
        //   \u2605\u73b0\u5728\uff1a\u2605\u5148\u7528 ``PartEntity#getParent()`` \u6362\u7b97\u56de**\u672c\u4f53** \u2713
        //     \uff08\u2605\u540c\u4e00\u5957\u771f\u4f24/\u7a7f\u900f/\u706b\u77e2/\u9ed1\u7bad\u903b\u8f91\u7167\u5e38\u751f\u6548 \u2713\uff09
        net.minecraft.world.entity.Entity tnl$hit = hitResult.getEntity();
        // TNL980 \u2605\u2605 \u63a2\u9488\uff1a\u2605\u770b"\u547d\u4e2d\u4e86\u4ec0\u4e48/\u7b97\u4e0d\u7b97 boss" \u2713
        {
            org.slf4j.Logger lg = com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER;
            Object parentInfo = "";
            try {
                if (tnl$hit instanceof net.minecraftforge.entity.PartEntity<?> p) {
                    parentInfo = " part-of " + p.getParent().getClass().getSimpleName();
                }
            } catch (Throwable ignored) {
            }
            // TNL983 \u2605\u2605 \u63a2\u9488\u6539\u6210\u6253"\u6362\u7b97\u540e"\u7684\u7ed3\u679c \u2713
            net.minecraft.world.entity.Entity tnl$res =
                    com.mofengbaizhi.tinkersnewlife.util.TruePierce.resolveVictim(tnl$hit);
            lg.warn("TNL983 hit={} ({}){}  -> resolved={}  isLiving={}  boss={}",
                    tnl$hit.getClass().getSimpleName(), tnl$hit.getType(), parentInfo,
                    tnl$res.getClass().getSimpleName(),
                    tnl$res instanceof net.minecraft.world.entity.LivingEntity,
                    tnl$isBoss(tnl$res));
        }
        if (tnl$hit instanceof net.minecraftforge.entity.PartEntity<?> tnl$part) {
            tnl$hit = tnl$part.getParent();
        }
        // TNL989 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u51b0\u4e0e\u706b\u6253\u90e8\u4ef6\u7684\u95ee\u9898\u8fd8\u6ca1\u4fee\u597d" \u2713
        //   \u2605\u65e5\u5fd7 \u002b \u6e90\u7801\u4ea4\u53c9\u9a8c\u8bc1 \u2713\uff1a
        //     \u00b7 \u2605``TNL983 ... hit=EntityDragonPart -> resolved=EntityLightningDragon isLiving=true`` \u2713
        //       \u21d2 \u2605\u6362\u7b97**\u672c\u6765\u5c31\u6210\u529f** \u2713
        //     \u00b7 \u2605\u4f46 ``TNL988`` **\u4e00\u884c\u90fd\u6ca1\u6709** \u2717 \u21d2 \u2605\u6839\u672c\u6ca1\u8d70\u5230\u4f24\u5bb3\u90a3\u4e00\u6b65 \u2713
        //     \u00b7 \u2605\u539f\u56e0 \u2717\uff1a\u2605\u8fd9\u91cc\u6bd4\u7684\u662f**\u539f\u59cb\u547d\u4e2d\u5b9e\u4f53**\uff08\u2605\u90e8\u4ef6 \u2717\uff09
        //       \u2605\u90e8\u4ef6\u4e0d\u662f ``LivingEntity`` \u21d2 \u2605**\u5728\u8fd9\u91cc\u5c31 return \u4e86** \u2713\u2713
        //       \uff08\u2605\u6211\u4e0a\u4e00\u8f6e\u7684\u6362\u7b97\u53ea\u5582\u7ed9\u4e86\u63a2\u9488 \u2717 \u6ca1\u7528\u5728\u8fd9\u9053\u95e8\u4e0a \u2713\uff09
        //   \u2605\u73b0\u5728\uff1a\u2605\u5148\u6362\u7b97\u6210\u672c\u4f53 \u2713 \u002b \u2605\u6362\u4e0d\u5230\u5c31\u9000\u56de\u539f\u7248 \u2713
        net.minecraft.world.entity.Entity tnl$resolved =
                com.mofengbaizhi.tinkersnewlife.util.TruePierce.resolveVictim(tnl$hit);
        if (!(tnl$resolved instanceof LivingEntity target)) {
            super.onHitEntity(hitResult);
            return;
        }
        if (this.level().isClientSide) {
            return;
        }
        // TNL938 \u2605\u2605 \u7528\u6237\u53e3\u5f84\uff1a\u2605"\u6211\u662f\u8bf4\u8ba9\u5b83\u53ef\u4ee5\u7ee7\u7eed\u9501\uff0c\u4f46\u662f\u522b\u9501\u7740\u4e0d\u9020\u6210\u4f24\u5bb3" \u2713
        //   \u2605\u21d2 \u2605\u64a4\u6389"\u540c\u4e00\u76ee\u6807\u4e0d\u91cd\u590d\u4f24\u5bb3"\u8fd9\u6761\u9650\u5236 \u2713
        //   \u2605\u6bcf\u6b21\u63a5\u89e6\u90fd\u7ed3\u7b97\u4f24\u5bb3 \u2713\uff0c\u2605\u7a7f\u900f\u6b21\u6570\u7167\u65e7\u6bcf\u6b21 -1 \u2713
        //   \uff08\u2605\u6240\u4ee5\u2605\u9501\u7740\u4e5f\u80fd\u4e00\u76f4\u6253 \u2713\uff0c\u2605\u6253\u6ee1\u7a7f\u900f\u6b21\u6570\u5c31\u6d88\u5931 \u2713\uff09
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
        // TNL1020 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u58f0\u97f3\u7a0d\u5fae\u5927\u4e00\u70b9\uff0c\u7136\u540e\u8ba9\u666e\u901a\u7bad\u77e2\u5c04\u4e2d\u4e5f\u6709\u97f3\u4e50\u97f3\u6548\uff0c
        //   \u53ea\u662f\u6ca1\u5f3a\u5316\u7bad\u77e2\u597d\u542c" \u2713
        //   \u2605\u5f3a\u5316\u7bad \u2713\uff1a\u2605``AMETHYST_BLOCK_CHIME``\uff08\u2605\u6e05\u8106\u60a6\u8033\u7684"\u53ee" \u2713\uff09
        //     \u2605\u97f3\u91cf 1.6 \u2713\uff08\u2605\u6bd4\u539f\u6765 1.0 \u66f4\u54cd \u2713\uff09\u002b \u2605\u97f3\u8c03 0.5~2.0 \u968f\u673a \u2713
        //   \u2605\u666e\u901a\u7bad \u2713\uff1a\u2605``AMETHYST_BLOCK_HIT``\uff08\u2605\u95f7\u4e00\u70b9\u7684"\u6251" \u2713\uff09
        //     \u2605\u97f3\u91cf 1.3 \u2713\u002b \u2605\u97f3\u8c03\u53ea\u5728 0.7~1.1 \u5c0f\u8303\u56f4\u6296 \u2713\uff08\u2605\u542c\u7740\u5c31\u6ca1\u90a3\u4e48\u597d\u542c \u2713\uff09
        if (!this.level().isClientSide) {
            if (enh) {
                this.level().playSound(null, this.blockPosition(),
                        net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                        net.minecraft.sounds.SoundSource.PLAYERS,
                        1.6F, 0.5F + this.level().random.nextFloat() * 1.5F);
            } else {
                this.level().playSound(null, this.blockPosition(),
                        net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_HIT,
                        net.minecraft.sounds.SoundSource.PLAYERS,
                        1.3F, 0.7F + this.level().random.nextFloat() * 0.4F);
            }
        }
        LivingEntity attacker = this.getOwner() instanceof LivingEntity le ? le : null;
        try {
            com.mofengbaizhi.tinkersnewlife.util.TruePierce.apply(attacker, target, dmg);   // ★见下方分支 ✓
        } catch (Throwable th) {
            // TNL986 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u628a\u51b0\u4e0e\u706b\u9f99\u65e0\u6cd5\u771f\u4f24\u7684 bug \u4fee\u597d" \u2713
            //   \u2605jar \u8bc1\u636e\uff08\u2605javap \u2713\uff09\uff1a
            //     \u2605``EntityMutlipartPart``\uff08\u2605\u51b0\u706b\u90e8\u4ef6\u57fa\u7c7b \u2713\uff09\u81ea\u5df1\u5c31\u6709
            //     \u2605``public boolean hurt(DamageSource, float)``\uff08\u2605m_6469_ \u2713\uff09\uff0c\u2605\u5b83**\u4f1a\u628a\u4f24\u5bb3\u8f6c\u4EA4\u7ed9\u672c\u4f53** \u2713
            //     \u002b \u2605\u4e58\u4e0a\u90e8\u4f4d\u7cfb\u6570\uff08\u2605\u7fc5\u8180/\u5c3e\u5df4 \u2713\uff09\u3002\u2605\u539f\u7248 ``EnderDragonPart`` \u540c\u7406 \u2713
            //   \u2605\u6240\u4ee5\uff1a\u2605\u547d\u4e2d"\u90e8\u4ef6"\u65f6\u5e94**\u6253\u90e8\u4ef6** \u2713\uff08\u2605\u5b83\u4f1a\u8f6c\u53d1 \u2713\uff09
            //     \u2605\u800c\u4e0d\u662f\u76f4\u63a5\u6253\u672c\u4f53 \u2717\uff08\u2605\u90a3\u6837\u51b0\u706b\u8fd9\u8fb9\u4e0d\u8d70\u5b83\u7684\u8f6c\u53d1\u94fe \u2717\uff09
            // TNL988 \u2605\u2605 \u63a2\u9488\uff1a\u2605\u76f4\u63a5\u770b"\u5230\u5e95\u8d70\u5230\u4f24\u5bb3\u90a3\u4e00\u6b65\u6ca1\u6709 \u002b \u6253\u4e86\u591a\u5c11" \u2713
            org.slf4j.Logger lg2 = com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER;
            net.minecraft.world.entity.Entity tnl$victim = hitResult.getEntity();
            lg2.warn("TNL988 damage site: hit={} victim={} target={} dmg={} aliveBefore={}",
                    hitResult.getEntity().getClass().getSimpleName(),
                    tnl$victim.getClass().getSimpleName(),
                    target.getClass().getSimpleName(), dmg, target.getHealth());
            if (tnl$victim instanceof net.minecraft.world.entity.LivingEntity) {
                tnl$victim = target;                       // \u2605\u672c\u4f53\u547d\u4e2d \u21d2 \u2605\u8d70\u539f\u6765\u7684\u771f\u4f24\u8def\u5f84 \u2713
            }
            if (tnl$victim != target) {
                tnl$victim.hurt(this.damageSources().magic(), dmg);   // \u2605\u90e8\u4ef6\u81ea\u5df1\u8f6c\u53d1\u7ed9\u672c\u4f53 \u2713
            } else {
                com.mofengbaizhi.tinkersnewlife.util.TruePierce.apply(attacker, target, dmg);
            }
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
