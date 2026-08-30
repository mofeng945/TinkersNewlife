package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.curse.shikigami.ShikigamiHandler;
import com.mofengbaizhi.tinkersnewlife.content.curse.shikigami.ShikigamiType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * 十种影法术 式神实体（单类多型）
 * <p>
 * 行为按类型区分：玉犬守护扑击 / 鵺盘旋俯冲并可骑乘飞行 / 大蛇盘绕撕咬 /
 * 蛤蟆舌缚 / 满象水射+跃起踏压 / 脱兔兔群诱敌 / 圆鹿治疗 / 贯牛冲撞 /
 * 虎葬重击 / 魔虚罗适应再生。
 * 受到式神攻击的敌对/中立生物会立即反击（强制 setTarget）。
 */
public class ShikigamiEntity extends Mob {

    private static final EntityDataAccessor<Byte> TYPE_ID =
            SynchedEntityData.defineId(ShikigamiEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> SCALE =
            SynchedEntityData.defineId(ShikigamiEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> VARIANT =
            SynchedEntityData.defineId(ShikigamiEntity.class, EntityDataSerializers.BYTE);

    // 服务端字段
    private UUID ownerId;
    private UUID lockedId;
    private boolean tamed;
    private int attackCooldown;
    private int rangedCooldown;
    private int healCooldown;
    private int despawnTimer = 600;
    // 玉犬扑击
    private int pounceTicks;
    // 鵺俯冲
    private int diveTicks;
    // 满象踏压
    private int stompTicks;
    // 大蛇盘绕
    private UUID boundTargetId;
    private int boundTicks;
    // 贯牛冲撞
    private boolean charging;
    private double chargeDist;
    private int chargeTimer;
    // 魔虚罗适应
    private int adaptation;
    // 脱离战斗判定（未调伏）
    private int awayTicks;

    public ShikigamiEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    /** 生成式神（玉犬生成黑白一对，其余一只） */
    public static void spawnPair(ServerPlayer player, ShikigamiType type, boolean tamed, LivingEntity locked) {
        ServerLevel level = player.serverLevel();
        int count = type == ShikigamiType.DOG ? 2 : 1;
        for (int i = 0; i < count; i++) {
            ShikigamiEntity shikigami = new ShikigamiEntity(com.mofengbaizhi.tinkersnewlife.content.ModEntities.SHIKIGAMI.get(), level);
            shikigami.setPos(player.getX() + level.random.nextDouble() - 0.5, player.getY() + 0.2, player.getZ() + level.random.nextDouble() - 0.5);
            shikigami.initStats(player, type, tamed, locked, i);
            level.addFreshEntity(shikigami);
        }
        // 脱兔：额外生成 7 只兔群（大量四散，扰乱/诱敌）
        if (type == ShikigamiType.RABBIT) {
            for (int i = 0; i < 7; i++) {
                ShikigamiEntity bunny = new ShikigamiEntity(com.mofengbaizhi.tinkersnewlife.content.ModEntities.SHIKIGAMI.get(), level);
                bunny.setPos(player.getX() + level.random.nextDouble() * 4 - 2, player.getY() + 0.2, player.getZ() + level.random.nextDouble() * 4 - 2);
                bunny.initStats(player, type, tamed, locked, 0);
                bunny.despawnTimer = 400;
                level.addFreshEntity(bunny);
            }
        }
    }

    /** 初始化属性（亲和/输出缩放）与模式 */
    private void initStats(ServerPlayer player, ShikigamiType type, boolean tamed, LivingEntity locked, int variant) {
        this.ownerId = player.getUUID();
        this.lockedId = locked != null ? locked.getUUID() : null;
        this.tamed = tamed;
        entityData.set(TYPE_ID, (byte) type.ordinal());
        entityData.set(VARIANT, (byte) variant);
        entityData.set(SCALE, (float) type.scaledSize(player));
        double hp = type.scaledHp(player);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        setHealth((float) hp);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(type.scaledSpeed(player));
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(type.scaledDamage(player));
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(32.0);
        setPersistenceRequired();
        if (type == ShikigamiType.NUE) {
            setNoGravity(true);
        }
        this.setCustomName(Component.translatable(type.getLangKey()));
        this.setCustomNameVisible(false);
    }

    public ShikigamiType getShikigamiType() {
        int ord = entityData.get(TYPE_ID) & 0xFF;
        return ord >= 0 && ord < ShikigamiType.values().length ? ShikigamiType.values()[ord] : ShikigamiType.DOG;
    }

    public float getShikigamiScale() {
        return entityData.get(SCALE);
    }

    public int getVariant() {
        return entityData.get(VARIANT) & 0xFF;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(TYPE_ID, (byte) 0);
        entityData.define(SCALE, 1.0f);
        entityData.define(VARIANT, (byte) 0);
    }

    @Nullable
    public ServerPlayer getOwner() {
        if (ownerId == null) return null;
        Entity e = level() instanceof ServerLevel sl ? sl.getEntity(ownerId) : null;
        return e instanceof ServerPlayer sp ? sp : null;
    }

    @Override
    public void tick() {
        super.tick();
        // 鵺：无重力必须双端生效（noGravity 不是同步字段，客户端需每 tick 补）
        if (getShikigamiType() == ShikigamiType.NUE) {
            setNoGravity(true);
        }
        if (level().isClientSide) {
            tickClientParticles();
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        ServerPlayer owner = getOwner();
        if (owner == null || !owner.isAlive()) {
            discard();
            return;
        }
        // 骑乘模式：玩家驾驶（不执行 AI）
        if (!getPassengers().isEmpty()) {
            getNavigation().stop();
            return;
        }
        // 脱兔被动：四散逃跑 + 啄怪诱敌
        if (getShikigamiType() == ShikigamiType.RABBIT && tamed) {
            if (--despawnTimer <= 0) {
                discard();
                return;
            }
            wanderPassively(owner);
            return;
        }
        if (attackCooldown > 0) attackCooldown--;
        if (rangedCooldown > 0) rangedCooldown--;
        if (healCooldown > 0) healCooldown--;
        if (boundTicks > 0) boundTicks--;

        LivingEntity target = tamed ? pickEnemyTarget(owner) : pickHostileTarget(owner);
        if (!tamed && !checkUntamedLifecycle(owner)) {
            return;
        }
        if (target == null) {
            followOwner(owner);
            if (getShikigamiType() == ShikigamiType.DEER) {
                healOwner(owner);
            }
            return;
        }
        behave(owner, target, level);
    }

    // ============================================================
    //  目标选择
    // ============================================================

    /** 已调伏：主人正在攻击/被攻击的目标，否则最近敌对生物（含未调伏的敌意式神） */
    private LivingEntity pickEnemyTarget(ServerPlayer owner) {
        LivingEntity mob = owner.getLastHurtMob();
        if (isValidTarget(mob)) return mob;
        mob = owner.getLastHurtByMob();
        if (isValidTarget(mob)) return mob;
        List<LivingEntity> enemies = level().getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(owner.position(), 32, 32, 32),
                e -> e != owner && e != this && e.isAlive()
                        && (e instanceof Enemy || (e instanceof ShikigamiEntity s && !s.tamed)));
        double best = Double.MAX_VALUE;
        LivingEntity result = null;
        for (LivingEntity e : enemies) {
            double d = e.distanceToSqr(owner);
            if (d < best) {
                best = d;
                result = e;
            }
        }
        return result;
    }

    /** 未调伏：攻击主人与锁定目标中较近者 */
    private LivingEntity pickHostileTarget(ServerPlayer owner) {
        LivingEntity locked = lockedId != null && level() instanceof ServerLevel sl && sl.getEntity(lockedId) instanceof LivingEntity le ? le : null;
        if (locked == null || !locked.isAlive()) return owner;
        return distanceToSqr(owner) < distanceToSqr(locked) ? owner : locked;
    }

    private boolean isValidTarget(LivingEntity e) {
        return e != null && e.isAlive() && e != this
                && !(e instanceof ShikigamiEntity s && s.ownerId != null && s.ownerId.equals(ownerId) && s.tamed);
    }

    /** 未调伏生命周期：主人死亡 → 消失；锁定目标存在且已死亡 → 消失；从未锁定目标时只攻击主人 */
    private boolean checkUntamedLifecycle(ServerPlayer owner) {
        if (distanceToSqr(owner) > 24.0 * 24.0) {
            if (++awayTicks > 100) {
                discard();
                return false;
            }
        } else {
            awayTicks = 0;
        }
        if (lockedId != null) {
            LivingEntity locked = level() instanceof ServerLevel sl && sl.getEntity(lockedId) instanceof LivingEntity le ? le : null;
            if (locked == null || !locked.isAlive()) {
                discard();
                return false;
            }
        }
        return true;
    }

    // ============================================================
    //  行为
    // ============================================================

    private void behave(ServerPlayer owner, LivingEntity target, ServerLevel level) {
        switch (getShikigamiType()) {
            case DOG -> behaveDog(owner, target, level);
            case NUE -> behaveNue(owner, target, level);
            case SERPENT -> behaveSerpent(owner, target, level);
            case TOAD -> behaveToad(owner, target);
            case ELEPHANT -> behaveElephant(owner, target, level);
            case DEER -> behaveDeer(owner, target);
            case OX -> behaveOx(owner, target, level);
            case RABBIT -> behaveMelee(owner, target, level, 0.8);
            default -> behaveMelee(owner, target, level, getShikigamiType() == ShikigamiType.TIGER ? 2.6 : 2.2);
        }
    }

    /** 通用近战 */
    private void behaveMelee(ServerPlayer owner, LivingEntity target, ServerLevel level, double reach) {
        getNavigation().moveTo(target, getAttributeValue(Attributes.MOVEMENT_SPEED));
        double reachSq = (reach * getShikigamiScale()) * (reach * getShikigamiScale());
        if (attackCooldown <= 0 && distanceToSqr(target) <= reachSq + target.getBbWidth() * target.getBbWidth()) {
            attackCooldown = 25;
            meleeHit(target, level);
        }
    }

    /** 玉犬：守护协同 —— 快速突进到敌人身前，跳起扑击（原版狼式） */
    private void behaveDog(ServerPlayer owner, LivingEntity target, ServerLevel level) {
        if (pounceTicks > 0) {
            pounceTicks--;
            Vec3 dir = target.position().add(0, target.getBbHeight() * 0.3, 0).subtract(position()).normalize();
            setDeltaMovement(dir.x * 0.6, getDeltaMovement().y, dir.z * 0.6);
            if (distanceToSqr(target) < 3.2) {
                pounceTicks = 0;
                meleeHit(target, level);
            }
            return;
        }
        getNavigation().moveTo(target, getAttributeValue(Attributes.MOVEMENT_SPEED));
        double dist = distanceTo(target);
        if (attackCooldown <= 0) {
            if (dist <= 1.8 + target.getBbWidth() * 0.5) {
                attackCooldown = 20;
                meleeHit(target, level);
            } else if (dist <= 3.5) {
                attackCooldown = 25;
                pounceTicks = 12;
                Vec3 dir = target.position().add(0, target.getBbHeight() * 0.3, 0).subtract(position()).normalize();
                setDeltaMovement(dir.x * 0.55, 0.42, dir.z * 0.55);
            }
        }
    }

    /** 鵺：盘旋于敌人上空 + 周期性雷击 + 俯冲撕咬（可骑乘飞行） */
    private void behaveNue(ServerPlayer owner, LivingEntity target, ServerLevel level) {
        if (diveTicks > 0) {
            diveTicks--;
            Vec3 dir = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(position()).normalize();
            setDeltaMovement(dir.scale(0.9));
            if (distanceToSqr(target) < 2.0) {
                diveTicks = 0;
                meleeHit(target, level);
            }
            return;
        }
        // 绕目标上方水平盘旋
        double angle = tickCount * 0.08;
        double radius = 4.0;
        Vec3 circle = target.position().add(Math.cos(angle) * radius, 3.0, Math.sin(angle) * radius);
        Vec3 toCircle = circle.subtract(position());
        double d = toCircle.length();
        if (d > 1.2) {
            setDeltaMovement(toCircle.normalize().scale(0.55));
        } else {
            setDeltaMovement(Vec3.ZERO);
        }
        // 面朝目标
        faceTarget(target);
        if (rangedCooldown <= 0) {
            rangedCooldown = 60;
            strikeLightning(target, level);
        }
        // 概率俯冲
        if (attackCooldown <= 0 && random.nextInt(80) == 0) {
            attackCooldown = 90;
            diveTicks = 25;
        }
    }

    private void faceTarget(LivingEntity target) {
        Vec3 dir = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(position());
        float yaw = (float) -Math.toDegrees(Math.atan2(dir.x, dir.z));
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** 大蛇：主动追击，近身盘绕束缚敌人，持续撕咬直到一方死亡 */
    private void behaveSerpent(ServerPlayer owner, LivingEntity target, ServerLevel level) {
        double dist = distanceTo(target);
        if (boundTargetId != null && boundTargetId.equals(target.getUUID()) && boundTicks > 0) {
            // 已盘绕：贴在目标身上持续撕咬
            boundTicks--;
            setPos(target.getX(), target.getY() + target.getBbHeight() * 0.3, target.getZ());
            faceTarget(target);
            if (attackCooldown <= 0) {
                attackCooldown = 15;
                meleeHit(target, level);
            }
            return;
        }
        if (dist <= 2.0 * getShikigamiScale()) {
            // 开始盘绕：强减速 + 压制跳跃
            boundTargetId = target.getUUID();
            boundTicks = 120;
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 140, 6));
            target.addEffect(new MobEffectInstance(MobEffects.JUMP, 140, 250));
        } else {
            getNavigation().moveTo(target, getAttributeValue(Attributes.MOVEMENT_SPEED) * 1.3);
        }
    }

    /** 蛤蟆：舌头把目标拉到身前并造成一次伤害 */
    private void behaveToad(ServerPlayer owner, LivingEntity target) {
        double dist = distanceTo(target);
        if (dist > 12) {
            getNavigation().moveTo(target, getAttributeValue(Attributes.MOVEMENT_SPEED));
            return;
        }
        if (rangedCooldown <= 0) {
            rangedCooldown = 50;
            Vec3 pull = position().add(0, 0.8, 0).subtract(target.position()).normalize();
            target.addDeltaMovement(pull.scale(1.1));
            target.hurtMarked = true;
            target.hurt(damageSources().mobAttack(this), (float) (getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.8));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            retaliate(target);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.0F, 0.6F);
        } else if (dist > 3) {
            getNavigation().moveTo(target, getAttributeValue(Attributes.MOVEMENT_SPEED));
        }
    }

    /** 满象：水射 + 跃起踏压 + 近战 */
    private void behaveElephant(ServerPlayer owner, LivingEntity target, ServerLevel level) {
        if (stompTicks > 0) {
            stompTicks--;
            if (onGround()) {
                stompTicks = 0;
                stomp(level);
            }
            return;
        }
        behaveMelee(owner, target, level, 2.6);
        if (rangedCooldown <= 0) {
            rangedCooldown = 60;
            waterSpray(level);
        }
        // 高高跃起踏压
        if (attackCooldown <= 0 && distanceTo(target) <= 5.0 && random.nextInt(50) == 0) {
            attackCooldown = 80;
            stompTicks = 22;
            Vec3 dir = target.position().subtract(position()).normalize();
            setDeltaMovement(dir.x * 0.45, 1.15, dir.z * 0.45);
        }
    }

    /** 满象踏压：落地时压伤周围目标 */
    private void stomp(ServerLevel level) {
        double dmg = getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.2;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(position(), 5, 3, 5))) {
            if (e == this || e == getOwner()) continue;
            e.hurt(damageSources().mobAttack(this), (float) dmg);
            e.push(0, 0.5, 0);
            retaliate(e);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 12, 1.0, 0.3, 1.0, 0);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2F, 0.7F);
    }

    /** 满象水射：前方锥形水流伤害 + 击退 */
    private void waterSpray(ServerLevel level) {
        Vec3 dir = getLookAngle().normalize();
        Vector3f water = new Vector3f(0.35F, 0.65F, 1.0F);
        for (double d = 0.5; d <= 5; d += 0.4) {
            Vec3 p = position().add(0, getBbHeight() * 0.7, 0).add(dir.scale(d));
            level.sendParticles(new DustParticleOptions(water, 1.2F), p.x, p.y, p.z, 2, 0.15, 0.15, 0.15, 0);
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 1.5F, 0.8F);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(position(), 10, 6, 10))) {
            if (e == this || e == getOwner()) continue;
            Vec3 to = e.position().subtract(position());
            double d = to.horizontalDistance();
            if (d > 5 || to.normalize().dot(dir) < 0.5) continue;
            e.hurt(damageSources().mobAttack(this), (float) (getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.8));
            e.push(dir.x * 1.4, 0.5, dir.z * 1.4);
            retaliate(e);
        }
    }

    /** 圆鹿：治疗主人（反转术式） */
    private void behaveDeer(ServerPlayer owner, LivingEntity target) {
        if (distanceToSqr(owner) > 9 * 9) {
            getNavigation().moveTo(owner, getAttributeValue(Attributes.MOVEMENT_SPEED));
        } else {
            getNavigation().stop();
        }
        healOwner(owner);
        if (distanceToSqr(target) <= 4 * 4 && attackCooldown <= 0) {
            attackCooldown = 30;
            meleeHit(target, (ServerLevel) level());
        }
    }

    private void healOwner(ServerPlayer owner) {
        if (healCooldown > 0) return;
        healCooldown = 40;
        double amount = 4.0 * (1.0 + ShikigamiType.statScale(owner) - 1.0) + getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.3;
        if (owner.getHealth() < owner.getMaxHealth()) {
            owner.heal((float) Math.max(1, amount));
            ((ServerLevel) level()).sendParticles(ParticleTypes.HEART, owner.getX(), owner.getY() + 1.2, owner.getZ(), 4, 0.4, 0.3, 0.4, 0);
        }
        if (getHealth() < getMaxHealth()) {
            heal(1.0F);
        }
    }

    /** 贯牛：直线冲撞（冲速受咒力输出/亲和缩放），冲得越远撞得越狠 */
    private void behaveOx(ServerPlayer owner, LivingEntity target, ServerLevel level) {
        if (charging) {
            chargeTimer++;
            Vec3 before = position();
            Vec3 dir = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(position()).normalize();
            double chargeSpeed = Math.min(1.6, 0.55 + getAttributeValue(Attributes.MOVEMENT_SPEED) * 1.6);
            setDeltaMovement(dir.scale(chargeSpeed));
            double moved = position().distanceTo(before);
            chargeDist += moved;
            if (distanceToSqr(target) <= 3.2 * 3.2) {
                double dmg = getAttributeValue(Attributes.ATTACK_DAMAGE) * (1.0 + Math.max(0, chargeDist) * 0.25);
                target.hurt(damageSources().mobAttack(this), (float) dmg);
                target.push(dir.x * 1.2, 0.4, dir.z * 1.2);
                retaliate(target);
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 6, 0.3, 0.3, 0.3, 0);
                stopCharge();
                return;
            }
            if (moved < 0.03 || chargeTimer > 80) {
                stopCharge();
            }
            return;
        }
        double dist = distanceTo(target);
        if (dist > 20) {
            getNavigation().moveTo(target, getAttributeValue(Attributes.MOVEMENT_SPEED));
            return;
        }
        if (attackCooldown <= 0) {
            getLookControl().setLookAt(target);
            charging = true;
            chargeDist = 0;
            chargeTimer = 0;
            attackCooldown = 80;
        }
    }

    private void stopCharge() {
        charging = false;
        setDeltaMovement(Vec3.ZERO);
    }

    // ============================================================
    //  通用
    // ============================================================

    /** 近战命中：伤害 + 强制反击（敌对/中立生物都会打回来） */
    private void meleeHit(LivingEntity target, ServerLevel level) {
        double dmg = getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (getShikigamiType() == ShikigamiType.MAHORAGA && target.getMobType() == MobType.UNDEAD) {
            dmg *= 1.5;
        }
        target.hurt(damageSources().mobAttack(this), (float) dmg);
        retaliate(target);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                4, 0.2, 0.2, 0.2, 0);
    }

    /** 强制反击：被攻击的生物立即把式神当作目标（含史莱姆等无复仇目标的生物） */
    private void retaliate(LivingEntity target) {
        if (target instanceof Mob mob && mob.getTarget() == null) {
            mob.setTarget(this);
        }
    }

    /** 鵺雷击：视觉闪电（不破坏/不引火）+ 自身结算伤害与小范围波及 */
    private void strikeLightning(LivingEntity target, ServerLevel level) {
        var bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.setVisualOnly(true);
            bolt.moveTo(target.getX(), target.getY(), target.getZ(), 0, 0);
            level.addFreshEntity(bolt);
        }
        double dmg = getAttributeValue(Attributes.ATTACK_DAMAGE);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(target.position(), 5, 5, 5))) {
            if (e == this || e == getOwner()) continue;
            if (e instanceof ShikigamiEntity s && s.ownerId != null && s.ownerId.equals(ownerId)) continue;
            if (!e.isAlive()) continue;
            e.hurt(damageSources().mobAttack(this), (float) (dmg * 0.8));
            retaliate(e);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + 1, target.getZ(),
                12, 0.5, 0.5, 0.5, 0);
    }

    /** 跟随主人（无目标时） */
    private void followOwner(ServerPlayer owner) {
        double dist = distanceToSqr(owner);
        if (dist > 64 * 64) {
            discard();
            return;
        }
        if (dist > 4 * 4) {
            getNavigation().moveTo(owner, getAttributeValue(Attributes.MOVEMENT_SPEED));
        } else {
            getNavigation().stop();
        }
    }

    /** 脱兔被动：四散逃跑；啄附近的怪物触发仇恨（怪物优先追兔子） */
    private void wanderPassively(ServerPlayer owner) {
        LivingEntity danger = level().getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(position(), 24, 24, 24),
                e -> e != this && e != owner && e.isAlive() && e instanceof Enemy)
                .stream().min((a, b) -> Double.compare(distanceToSqr(a), distanceToSqr(b))).orElse(null);
        if (danger != null && distanceToSqr(danger) < 12 * 12) {
            // 恐慌逃窜：远离怪物
            Vec3 away = position().subtract(danger.position()).normalize().scale(16);
            getNavigation().moveTo(getX() + away.x, getY(), getZ() + away.z,
                    getAttributeValue(Attributes.MOVEMENT_SPEED) * 1.5);
            // 啄怪触发仇恨（让怪物追兔子而不是主人）
            if (random.nextInt(20) == 0 && distanceTo(danger) < 4.0) {
                danger.hurt(damageSources().mobAttack(this), 0.5F);
                retaliate(danger);
            }
            return;
        }
        if (random.nextInt(40) == 0) {
            double x = getX() + random.nextDouble() * 8 - 4;
            double z = getZ() + random.nextDouble() * 8 - 4;
            getNavigation().moveTo(x, getY(), z, getAttributeValue(Attributes.MOVEMENT_SPEED) * 1.2);
        }
    }

    // ============================================================
    //  骑乘（鵺飞行）
    // ============================================================

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide && getShikigamiType() == ShikigamiType.NUE
                && tamed && player.getUUID().equals(ownerId) && player.getMainHandItem().isEmpty()) {
            if (player.isPassenger()) {
                player.stopRiding();
            } else {
                player.startRiding(this);
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public LivingEntity getControllingPassenger() {
        Entity e = getFirstPassenger();
        return e instanceof LivingEntity le ? le : null;
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.9 + getShikigamiScale() * 0.4;
    }

    /** 骑乘飞行（服务端与客户端一致的移动钩子）：W 朝视线飞、S 后退、A/D 侧移、潜行下降 */
    @Override
    protected void tickRidden(Player player, Vec3 movement) {
        super.tickRidden(player, movement);
        if (getShikigamiType() == ShikigamiType.NUE) {
            setNoGravity(true);
            setYRot(player.getYRot());
            yBodyRot = player.getYRot();
            yHeadRot = player.getYRot();
            Vec3 look = player.getLookAngle();
            double speed = 0.55 + getAttributeValue(Attributes.MOVEMENT_SPEED) * 1.4;
            Vec3 motion = Vec3.ZERO;
            if (player.zza != 0) {
                motion = motion.add(look.scale(player.zza * speed));
            }
            if (player.xxa != 0) {
                Vec3 side = new Vec3(-look.z, 0, look.x).normalize();
                motion = motion.add(side.scale(player.xxa * speed * 0.6));
            }
            if (player.isShiftKeyDown()) {
                motion = motion.add(0, -0.4, 0);
            }
            setDeltaMovement(motion);
            move(MoverType.SELF, motion);
        }
    }

    // ============================================================
    //  客户端粒子 / 适应 / 死亡 / 杂项
    // ============================================================

    private void tickClientParticles() {
        ShikigamiType type = getShikigamiType();
        if (tickCount % 3 == 0) {
            level().addParticle(new DustParticleOptions(new Vector3f(0.9F, 0.1F, 0.1F), 0.5F),
                    getX() + random.nextGaussian() * 0.3, getY() + random.nextGaussian() * 0.3 + getBbHeight() * 0.5,
                    getZ() + random.nextGaussian() * 0.3, 0, 0, 0);
        }
        switch (type) {
            case NUE -> level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    getX() + random.nextGaussian() * 0.4, getY() + random.nextGaussian() * 0.4 + 1,
                    getZ() + random.nextGaussian() * 0.4, 0, 0, 0);
            case ELEPHANT -> level().addParticle(ParticleTypes.SPLASH,
                    getX() + random.nextGaussian() * 0.5, getY() + random.nextGaussian() * 0.3 + 1,
                    getZ() + random.nextGaussian() * 0.5, 0, 0.05, 0);
            case MAHORAGA -> level().addParticle(ParticleTypes.FLAME,
                    getX() + random.nextGaussian() * 0.5, getY() + random.nextGaussian() * 0.5 + getBbHeight() * 0.5,
                    getZ() + random.nextGaussian() * 0.5, 0, 0.02, 0);
            default -> {}
        }
    }

    /** 魔虚罗适应：受到攻击后减伤叠加 + 缓慢再生 */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (getShikigamiType() == ShikigamiType.MAHORAGA && !level().isClientSide && source.getEntity() != getOwner()) {
            if (adaptation < 12) adaptation++;
            amount *= (1.0F - 0.05F * adaptation);
        }
        return super.hurt(source, Math.max(0, amount));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && getShikigamiType() == ShikigamiType.MAHORAGA
                && tickCount % 40 == 0 && getHealth() < getMaxHealth()) {
            heal(1.0F);
        }
    }

    /** 未调伏式神被击败 → 调伏成功 */
    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && !tamed) {
            ServerPlayer owner = getOwner();
            if (owner != null) {
                ShikigamiHandler.onShikigamiDefeated(owner, getShikigamiType());
            }
        }
        super.die(source);
    }

    @Override
    protected void registerGoals() {}

    @Override
    public boolean canBeLeashed(net.minecraft.world.entity.player.Player player) { return false; }
}
