package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.curse.shikigami.ShikigamiHandler;
import com.mofengbaizhi.tinkersnewlife.content.curse.shikigami.ShikigamiType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 式神共享 AI：所有继承原版生物的子类在 aiStep 里调用 {@link #tick(Mob, ShikigamiMob)}。
 * <p>
 * 行为按类型区分（玉犬扑击 / 鵺盘旋俯冲+骑乘 / 蚀蠹盘绕 / 蛤蟆舌缚 / 川豚水射踏压 /
 * 脱兔诱敌 / 愈羊治疗 / 贯牛冲撞 / 怒角重击 / 魔虚罗适应再生）。
 */
public final class ShikigamiBehavior {

    /** 索敌/追击范围：只攻击主人附近的目标，防止越追越远跑丢（半径 32 格） */
    private static final double LEASH_RANGE = 32.0;
    private static final double LEASH_SQ = LEASH_RANGE * LEASH_RANGE;
    /** 跟随主人超过此距离直接传送回身边（而非消失） */
    private static final double TELEPORT_BACK_SQ = 64.0 * 64.0;

    private ShikigamiBehavior() {}

    /** 每 tick 驱动（服务端） */
    public static void tick(Mob self, ShikigamiMob info) {
        ShikigamiState st = info.getState();
        ServerLevel level = (ServerLevel) self.level();
        ServerPlayer owner = info.getOwner();
        if (owner == null) {
            // 主人不在线（未登录/跨服）：原地待命不消散；累计超时后清理。
            // 复活/上线后 getOwner() 恢复 → 自动重链继续（已调伏跟随/未调伏接着调伏战）。
            self.getNavigation().stop();
            if (++st.ownerGoneTicks > ShikigamiState.OWNER_GONE_MAX) {
                self.discard();
            }
            return;
        }
        st.ownerGoneTicks = 0;
        if (!owner.isAlive()) {
            // 主人死亡：式神原地待命，不消散——否则复活后未调伏式神要重新打调伏战、
            // 已调伏式神也要重新召唤。等主人复活后自动恢复行动。
            self.getNavigation().stop();
            return;
        }
        // 骑乘模式：玩家驾驶（不执行 AI）
        if (!self.getPassengers().isEmpty()) {
            self.getNavigation().stop();
            return;
        }
        // 脱兔被动：四散逃跑 + 啄怪诱敌
        if (info.getShikigamiType() == ShikigamiType.RABBIT && st.tamed) {
            if (--st.despawnTimer <= 0) {
                self.discard();
                return;
            }
            wanderPassively(self, info, owner);
            return;
        }
        if (st.attackCooldown > 0) st.attackCooldown--;
        if (st.rangedCooldown > 0) st.rangedCooldown--;
        if (st.healCooldown > 0) st.healCooldown--;
        if (st.boundTicks > 0) st.boundTicks--;

        LivingEntity target = st.tamed ? pickEnemyTarget(self, info, owner) : pickHostileTarget(self, info, owner);
        if (!st.tamed && !checkUntamedLifecycle(self, info, owner)) {
            return;
        }
        if (target == null) {
            followOwner(self, info, owner);
            if (info.getShikigamiType() == ShikigamiType.DEER) {
                healAllies(self, info, owner);
            }
            return;
        }
        // 已调伏：目标一旦脱离主人附近（被打飞/引走）就放弃追击，回主人身边，
        // 防止一路追出去最后跑丢/消失
        if (st.tamed && target.distanceToSqr(owner) > LEASH_SQ) {
            followOwner(self, info, owner);
            if (info.getShikigamiType() == ShikigamiType.DEER) {
                healAllies(self, info, owner);
            }
            return;
        }
        behave(self, info, owner, target, level);
    }

    // ============================================================
    //  目标选择
    // ============================================================

    private static LivingEntity pickEnemyTarget(Mob self, ShikigamiMob info, ServerPlayer owner) {
        // 0️⃣ 自己被打 → 还手（最高优先）：挨打目标就是敌人，避免"站着挨打"
        //    （如魔虚罗被 Boss 攻击时若主人没在打该目标，原逻辑会漏掉它）
        LivingEntity attacker = self.getLastHurtByMob();
        if (isEnemyOf(self, info, attacker)
                && attacker.distanceToSqr(self) <= LEASH_SQ) return attacker;
        // 主人最近攻击的目标（近战/箭矢命中才会更新；术式直伤不更新，靠下方扫描兜底）
        LivingEntity mob = owner.getLastHurtMob();
        if (isValidTarget(self, info, mob) && mob.distanceToSqr(owner) <= LEASH_SQ) return mob;
        mob = owner.getLastHurtByMob();
        if (isValidTarget(self, info, mob) && mob.distanceToSqr(owner) <= LEASH_SQ) return mob;
        // 主人周边敌人；若式神离主人较远（体型大/被击退/卡地形），扫描同时覆盖式神自身周边
        List<LivingEntity> enemies = self.level().getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(owner.position(), LEASH_RANGE * 2, LEASH_RANGE * 2, LEASH_RANGE * 2),
                e -> e != owner && e != self && e.isAlive() && isEnemyOf(self, info, e));
        if (self.distanceToSqr(owner) > 16.0 * 16.0) {
            enemies.addAll(self.level().getEntitiesOfClass(LivingEntity.class,
                    AABB.ofSize(self.position(), LEASH_RANGE * 2, LEASH_RANGE * 2, LEASH_RANGE * 2),
                    e -> e != owner && e != self && e.isAlive() && isEnemyOf(self, info, e)));
        }
        double best = Double.MAX_VALUE;
        LivingEntity result = null;
        for (LivingEntity e : enemies) {
            // 以式神自身距离为准（去打它，而不是让式神满图跑向"主人身边的敌人"）
            double d = e.distanceToSqr(self);
            if (d < best) {
                best = d;
                result = e;
            }
        }
        return result;
    }

    /**
     * 敌人判定：已调伏式神之间永不互打；其他式神（含未调伏）按敌意/血缘区分。
     * 原版敌对生物子类（幻翼/蠹虫实现了 Enemy）不能直接按 instanceof Enemy 判敌，
     * 否则同阵营式神会互打。
     */
    private static boolean isEnemyOf(Mob self, ShikigamiMob info, LivingEntity e) {
        if (e instanceof ShikigamiMob other) {
            ShikigamiState os = other.getState();
            // 已调伏式神：互不敌对
            if (os.tamed) return false;
            // 未调伏式神：敌意（与主人同源的未调伏式神也视为敌，用于调伏）
            return true;
        }
        // 主人咒灵操术的亡灵/仆从（同队）不算敌人：式神不打友军
        ServerPlayer owner = info.getOwner();
        if (owner != null && com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedSpiritTechnique
                .isSpiritTeam(e, owner)) {
            return false;
        }
        return e instanceof Enemy;
    }

    private static LivingEntity pickHostileTarget(Mob self, ShikigamiMob info, ServerPlayer owner) {
        ShikigamiState st = info.getState();
        LivingEntity locked = st.lockedId != null
                && self.level() instanceof ServerLevel sl && sl.getEntity(st.lockedId) instanceof LivingEntity le ? le : null;
        if (locked == null || !locked.isAlive()) return owner;
        // 锁定目标脱离主人附近则不追（防止追着目标越跑越远，最后离主人太远而消散），留在主人身边
        if (locked.distanceToSqr(owner) > LEASH_SQ) return owner;
        return self.distanceToSqr(owner) < self.distanceToSqr(locked) ? owner : locked;
    }

    private static boolean isValidTarget(Mob self, ShikigamiMob info, LivingEntity e) {
        return e != null && e.isAlive() && e != self
                && !(e instanceof ShikigamiMob s && s.getState().ownerId != null
                && s.getState().ownerId.equals(info.getState().ownerId) && s.getState().tamed);
    }

    private static boolean checkUntamedLifecycle(Mob self, ShikigamiMob info, ServerPlayer owner) {
        ShikigamiState st = info.getState();
        if (self.distanceToSqr(owner) > 24.0 * 24.0) {
            if (++st.awayTicks > 100) {
                self.discard();
                return false;
            }
        } else {
            st.awayTicks = 0;
        }
        if (st.lockedId != null) {
            LivingEntity locked = self.level() instanceof ServerLevel sl && sl.getEntity(st.lockedId) instanceof LivingEntity le ? le : null;
            if (locked == null || !locked.isAlive()) {
                self.discard();
                return false;
            }
        }
        return true;
    }

    // ============================================================
    //  行为
    // ============================================================

    private static void behave(Mob self, ShikigamiMob info, ServerPlayer owner, LivingEntity target, ServerLevel level) {
        switch (info.getShikigamiType()) {
            case DOG -> behaveDog(self, info, target, level);
            case NUE -> behaveNue(self, info, target, level);
            case SERPENT -> behaveSerpent(self, info, owner, target, level);
            case TOAD -> behaveToad(self, info, target, level);
            case ELEPHANT -> behaveElephant(self, info, owner, target, level);
            case DEER -> behaveDeer(self, info, owner, target, level);
            case OX -> behaveOx(self, info, owner, target, level);
            case RABBIT -> behaveMelee(self, info, owner, target, level, 0.8);
            case TIGER -> behaveMelee(self, info, owner, target, level, 2.6);
            case MAHORAGA -> behaveMahoraga(self, info, owner, target, level);
        }
    }

    private static void behaveMelee(Mob self, ShikigamiMob info, ServerPlayer owner, LivingEntity target, ServerLevel level, double reach) {
        self.getNavigation().moveTo(target, info.getState().speed);
        // 近战判定用固定臂长 + 目标半宽（不再乘体型放大：体型放大后判定距离暴涨，
        // 会出现十几格外"隔空命中"的远程观感——魔虚罗/怒角体型大尤其明显）
        double hitRange = reach + target.getBbWidth() * 0.5;
        if (info.getState().attackCooldown <= 0 && self.distanceToSqr(target) <= hitRange * hitRange) {
            info.getState().attackCooldown = 25;
            meleeHit(self, info, target, level);
        }
    }

    /** 魔虚罗：纯近战。目标无法抵达（浮空/隔墙/导航失败）时直接跳跃贴脸追击 */
    private static void behaveMahoraga(Mob self, ShikigamiMob info, ServerPlayer owner, LivingEntity target, ServerLevel level) {
        ShikigamiState st = info.getState();
        if (st.leapTicks > 0) {
            st.leapTicks--;
            Vec3 dir = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(self.position()).normalize();
            double vx = dir.x * 1.1;
            double vy = dir.y * 0.6 + 0.35; // 带一点上抛，越过障碍/贴近高台目标
            double vz = dir.z * 1.1;
            self.setDeltaMovement(vx, Math.min(1.0, vy), vz);
            self.getNavigation().stop();
            if (self.distanceToSqr(target) < 3.0 * 3.0 || st.leapTicks <= 0) {
                st.leapTicks = 0;
            }
            return;
        }
        // 近战判定：臂长较大（铁傀儡体型），不随 scale 放大
        double hitRange = 3.2 + target.getBbWidth() * 0.5;
        double distSq = self.distanceToSqr(target);
        if (st.attackCooldown <= 0 && distSq <= hitRange * hitRange) {
            st.attackCooldown = 20;
            meleeHit(self, info, target, level);
            return;
        }
        // 能走到就直接冲近战
        self.getNavigation().moveTo(target, st.speed);
        // 无法抵达（导航没路 / 目标在头顶高处 / 离得太远且长期没接近）→ 跳脸
        boolean navStuck = self.getNavigation().isDone() && distSq > hitRange * hitRange;
        double heightGap = target.getY() - self.getY();
        boolean tooHigh = heightGap > 1.2 && distSq > 2.5 * 2.5;
        if ((navStuck || tooHigh) && self.onGround() && st.attackCooldown < 60) {
            st.leapTicks = 30;
            st.attackCooldown = 40;
        }
    }

    private static void behaveDog(Mob self, ShikigamiMob info, LivingEntity target, ServerLevel level) {
        ShikigamiState st = info.getState();
        if (st.pounceTicks > 0) {
            st.pounceTicks--;
            Vec3 dir = target.position().add(0, target.getBbHeight() * 0.3, 0).subtract(self.position()).normalize();
            self.setDeltaMovement(dir.x * 0.6, self.getDeltaMovement().y, dir.z * 0.6);
            if (self.distanceToSqr(target) < 3.2) {
                st.pounceTicks = 0;
                meleeHit(self, info, target, level);
            }
            return;
        }
        self.getNavigation().moveTo(target, st.speed);
        double dist = self.distanceTo(target);
        if (st.attackCooldown <= 0) {
            if (dist <= 1.8 + target.getBbWidth() * 0.5) {
                st.attackCooldown = 20;
                meleeHit(self, info, target, level);
            } else if (dist <= 3.5) {
                st.attackCooldown = 25;
                st.pounceTicks = 12;
                Vec3 dir = target.position().add(0, target.getBbHeight() * 0.3, 0).subtract(self.position()).normalize();
                self.setDeltaMovement(dir.x * 0.55, 0.42, dir.z * 0.55);
            }
        }
    }

    private static void behaveNue(Mob self, ShikigamiMob info, LivingEntity target, ServerLevel level) {
        ShikigamiState st = info.getState();
        if (st.diveTicks > 0) {
            st.diveTicks--;
            Vec3 dir = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(self.position()).normalize();
            self.setDeltaMovement(dir.scale(0.9));
            faceDirection(self, dir);
            if (self.distanceToSqr(target) < 2.0) {
                st.diveTicks = 0;
                meleeHit(self, info, target, level);
            }
            return;
        }
        // 绕目标上方水平盘旋
        double angle = self.tickCount * 0.08;
        double radius = 4.0;
        Vec3 circle = target.position().add(Math.cos(angle) * radius, 3.0, Math.sin(angle) * radius);
        Vec3 toCircle = circle.subtract(self.position());
        double d = toCircle.length();
        Vec3 moveDir;
        if (d > 1.2) {
            moveDir = toCircle.normalize();
            self.setDeltaMovement(moveDir.scale(0.55));
        } else {
            moveDir = Vec3.ZERO;
            self.setDeltaMovement(Vec3.ZERO);
        }
        // 面向运动方向（盘旋切向/俯冲方向），避免一直面向圆心导致"固定朝向"
        if (moveDir.lengthSqr() > 0.001) {
            faceDirection(self, moveDir);
        }
        if (st.rangedCooldown <= 0) {
            st.rangedCooldown = 60;
            strikeLightning(self, info, target, level);
        }
        if (st.attackCooldown <= 0 && self.getRandom().nextInt(80) == 0) {
            st.attackCooldown = 90;
            st.diveTicks = 25;
        }
    }

    /** 面向指定方向（水平投影） */
    private static void faceDirection(Mob self, Vec3 dir) {
        float yaw = (float) -Math.toDegrees(Math.atan2(dir.x, dir.z));
        self.setYRot(yaw);
        self.yBodyRot = yaw;
        self.yHeadRot = yaw;
    }

    private static void faceTarget(Mob self, LivingEntity target) {
        Vec3 dir = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(self.position());
        float yaw = (float) -Math.toDegrees(Math.atan2(dir.x, dir.z));
        self.setYRot(yaw);
        self.yBodyRot = yaw;
        self.yHeadRot = yaw;
    }

    private static void behaveSerpent(Mob self, ShikigamiMob info, ServerPlayer owner, LivingEntity target, ServerLevel level) {
        ShikigamiState st = info.getState();
        double dist = self.distanceTo(target);
        if (st.boundTargetId != null && st.boundTargetId.equals(target.getUUID()) && st.boundTicks > 0) {
            st.boundTicks--;
            // 缠在目标身体中下部（不再是固定 30% 高度——巨型目标（如魔虚罗）30% 处接近头顶，
            // 小蠹虫贴上去看起来像"飘在头顶高空"）。大目标贴其脚边/下腹，小目标贴 30% 处。
            double attachY = target.getY() + Math.min(1.2 + self.getBbHeight() * 0.5,
                    target.getBbHeight() * 0.3);
            self.setPos(target.getX(), attachY, target.getZ());
            self.setDeltaMovement(Vec3.ZERO);
            faceTarget(self, target);
            if (st.attackCooldown <= 0) {
                st.attackCooldown = 15;
                meleeHit(self, info, target, level);
            }
            return;
        }
        if (dist <= 2.0 * info.getShikigamiScale()) {
            st.boundTargetId = target.getUUID();
            st.boundTicks = 120;
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 140, 6));
            target.addEffect(new MobEffectInstance(MobEffects.JUMP, 140, 250));
        } else {
            self.getNavigation().moveTo(target, st.speed * 1.3);
        }
    }

    private static void behaveToad(Mob self, ShikigamiMob info, LivingEntity target, ServerLevel level) {
        ShikigamiState st = info.getState();
        double dist = self.distanceTo(target);
        if (dist > 12) {
            self.getNavigation().moveTo(target, st.speed);
            return;
        }
        if (st.rangedCooldown <= 0) {
            st.rangedCooldown = 50;
            Vec3 pull = self.position().add(0, 0.8, 0).subtract(target.position()).normalize();
            target.addDeltaMovement(pull.scale(1.1));
            target.hurtMarked = true;
            target.hurt(self.damageSources().mobAttack(self), (float) (st.damage * 0.8));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            retaliate(self, target);
            self.level().playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.0F, 0.6F);
        } else if (dist > 3) {
            self.getNavigation().moveTo(target, st.speed);
        }
    }

    private static void behaveElephant(Mob self, ShikigamiMob info, ServerPlayer owner, LivingEntity target, ServerLevel level) {
        ShikigamiState st = info.getState();
        if (st.stompTicks > 0) {
            st.stompTicks--;
            if (self.onGround()) {
                st.stompTicks = 0;
                stomp(self, info, level);
            }
            return;
        }
        behaveMelee(self, info, owner, target, level, 2.6);
        if (st.rangedCooldown <= 0) {
            st.rangedCooldown = 60;
            waterSpray(self, info, level);
        }
        if (st.attackCooldown <= 0 && self.distanceTo(target) <= 5.0 && self.getRandom().nextInt(50) == 0) {
            st.attackCooldown = 80;
            st.stompTicks = 22;
            Vec3 dir = target.position().subtract(self.position()).normalize();
            self.setDeltaMovement(dir.x * 0.45, 1.15, dir.z * 0.45);
        }
    }

    /** AoE 目标过滤：排除自身、主人、主人咒灵操术同队、同主人已调伏式神（避免打到自己人） */
    private static boolean isFriendly(Mob self, ShikigamiMob info, LivingEntity e) {
        if (e == self || e == info.getOwner()) return true;
        if (e instanceof ShikigamiMob other) {
            ShikigamiState os = other.getState();
            if (os.tamed && os.ownerId != null && os.ownerId.equals(info.getState().ownerId)) return true;
        }
        // 主人咒灵操术的亡灵/仆从同队：AoE 不误伤
        ServerPlayer owner = info.getOwner();
        return owner != null && com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedSpiritTechnique
                .isSpiritTeam(e, owner);
    }

    private static void stomp(Mob self, ShikigamiMob info, ServerLevel level) {
        double dmg = info.getState().damage * 1.2;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(self.position(), 5, 3, 5))) {
            if (isFriendly(self, info, e)) continue;
            e.hurt(self.damageSources().mobAttack(self), (float) dmg);
            e.push(0, 0.5, 0);
            retaliate(self, e);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, self.getX(), self.getY() + 0.3, self.getZ(), 12, 1.0, 0.3, 1.0, 0);
        level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2F, 0.7F);
    }

    private static void waterSpray(Mob self, ShikigamiMob info, ServerLevel level) {
        Vec3 dir = self.getLookAngle().normalize();
        Vector3f water = new Vector3f(0.35F, 0.65F, 1.0F);
        for (double d = 0.5; d <= 5; d += 0.4) {
            Vec3 p = self.position().add(0, self.getBbHeight() * 0.7, 0).add(dir.scale(d));
            level.sendParticles(new DustParticleOptions(water, 1.2F), p.x, p.y, p.z, 2, 0.15, 0.15, 0.15, 0);
        }
        level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 1.5F, 0.8F);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(self.position(), 10, 6, 10))) {
            if (isFriendly(self, info, e)) continue;
            Vec3 to = e.position().subtract(self.position());
            double d = to.horizontalDistance();
            if (d > 5 || to.normalize().dot(dir) < 0.5) continue;
            e.hurt(self.damageSources().mobAttack(self), (float) (info.getState().damage * 0.8));
            e.push(dir.x * 1.4, 0.5, dir.z * 1.4);
            retaliate(self, e);
        }
    }

    private static void behaveDeer(Mob self, ShikigamiMob info, ServerPlayer owner, LivingEntity target, ServerLevel level) {
        if (self.distanceToSqr(owner) > 9 * 9) {
            self.getNavigation().moveTo(owner, info.getState().speed);
        } else {
            self.getNavigation().stop();
        }
        healAllies(self, info, owner);
        if (self.distanceToSqr(target) <= 4 * 4 && info.getState().attackCooldown <= 0) {
            info.getState().attackCooldown = 30;
            meleeHit(self, info, target, level);
        }
    }

    /** 愈羊治疗：主人不满血先奶主人；主人满血则奶同主人受伤的式神（含自己） */
    private static void healAllies(Mob self, ShikigamiMob info, ServerPlayer owner) {
        ShikigamiState st = info.getState();
        if (st.healCooldown > 0) return;
        double amount = 4.0 * ShikigamiType.statScale(owner) + st.damage * 0.3;

        if (owner.getHealth() < owner.getMaxHealth()) {
            st.healCooldown = 40;
            owner.heal((float) Math.max(1, amount));
            ((ServerLevel) self.level()).sendParticles(ParticleTypes.HEART, owner.getX(), owner.getY() + 1.2, owner.getZ(), 4, 0.4, 0.3, 0.4, 0);
            return;
        }
        // 主人满血：优先奶血量最低的同主人已调伏式神（含自己）
        LivingEntity best = null;
        double bestMissing = -1;
        for (LivingEntity e : self.level().getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(self.position(), 24, 24, 24),
                e -> e instanceof ShikigamiMob sm && sm.getState().ownerId != null
                        && sm.getState().ownerId.equals(owner.getUUID())
                        && sm.getState().tamed && e.isAlive()
                        && e.getHealth() < e.getMaxHealth())) {
            double missing = e.getMaxHealth() - e.getHealth();
            if (missing > bestMissing) {
                bestMissing = missing;
                best = e;
            }
        }
        if (best == null) return; // 无人需要治疗，不消耗冷却（等有人受伤再奶）
        st.healCooldown = 40;
        best.heal((float) Math.max(1, amount));
        ((ServerLevel) self.level()).sendParticles(ParticleTypes.HEART,
                best.getX(), best.getY() + best.getBbHeight() + 0.3, best.getZ(), 4, 0.4, 0.3, 0.4, 0);
    }

    private static void behaveOx(Mob self, ShikigamiMob info, ServerPlayer owner, LivingEntity target, ServerLevel level) {
        ShikigamiState st = info.getState();
        if (st.charging) {
            st.chargeTimer++;
            Vec3 before = self.position();
            // 冲撞方向取水平（目标较高/在空中时不斜向上飞，避免贯牛飘在空中）。
            Vec3 to = target.position().add(0, target.getBbHeight() * 0.3, 0).subtract(self.position());
            Vec3 dir = new Vec3(to.x, 0, to.z);
            if (dir.lengthSqr() < 1e-8) {
                stopCharge(st);
                return;
            }
            dir = dir.normalize();
            double chargeSpeed = Math.min(1.6, 0.55 + st.speed * 1.6);
            // 直接驱动位移（冲撞时 travel 被覆写为 no-op，避免重力/摩擦/原版移动控制干扰）。
            // 但保留竖直速度：若冲下坡/被顶起离地，重力会让它落回地面，不会悬空飘着滑行。
            double vy = self.getDeltaMovement().y;
            if (!self.onGround() && vy > -0.5) {
                vy -= 0.08;
            } else if (self.onGround()) {
                vy = 0;
            }
            self.setDeltaMovement(dir.x * chargeSpeed, vy, dir.z * chargeSpeed);
            self.move(MoverType.SELF, self.getDeltaMovement());
            self.getNavigation().stop();
            double moved = self.position().distanceTo(before);
            st.chargeDist += moved;
            // 目标水平距离够近即算命中（含高度差较大的空中目标时用水平距离判定）
            double hitDx = target.getX() - self.getX();
            double hitDz = target.getZ() - self.getZ();
            if (st.attackCooldown <= 0 && hitDx * hitDx + hitDz * hitDz <= 3.2 * 3.2) {
                double dmg = st.damage * (1.0 + Math.max(0, st.chargeDist) * 0.25);
                target.hurt(self.damageSources().mobAttack(self), (float) dmg);
                target.push(dir.x * 1.2, 0.4, dir.z * 1.2);
                retaliate(self, target);
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 6, 0.3, 0.3, 0.3, 0);
                stopCharge(st);
                return;
            }
            if (moved < 0.03 || st.chargeTimer > 80) {
                stopCharge(st);
            }
            return;
        }
        double dist = self.distanceTo(target);
        if (dist > 20) {
            self.getNavigation().moveTo(target, st.speed);
            return;
        }
        if (st.attackCooldown <= 0) {
            self.getLookControl().setLookAt(target);
            st.charging = true;
            st.chargeDist = 0;
            st.chargeTimer = 0;
            st.attackCooldown = 80;
        }
    }

    private static void stopCharge(ShikigamiState st) {
        st.charging = false;
    }

    // ============================================================
    //  通用
    // ============================================================

    private static void meleeHit(Mob self, ShikigamiMob info, LivingEntity target, ServerLevel level) {
        double dmg = info.getState().damage;
        if (info.getShikigamiType() == ShikigamiType.MAHORAGA && target.getMobType() == MobType.UNDEAD) {
            dmg *= 1.5;
        }
        target.hurt(self.damageSources().mobAttack(self), (float) dmg);
        retaliate(self, target);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                4, 0.2, 0.2, 0.2, 0);
    }

    private static void retaliate(Mob self, LivingEntity target) {
        if (target instanceof Mob mob && mob.getTarget() == null) {
            mob.setTarget(self);
        }
    }

    private static void strikeLightning(Mob self, ShikigamiMob info, LivingEntity target, ServerLevel level) {
        var bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.setVisualOnly(true);
            bolt.moveTo(target.getX(), target.getY(), target.getZ(), 0, 0);
            level.addFreshEntity(bolt);
        }
        double dmg = info.getState().damage;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(target.position(), 5, 5, 5))) {
            if (isFriendly(self, info, e)) continue;
            if (!e.isAlive()) continue;
            e.hurt(self.damageSources().mobAttack(self), (float) (dmg * 0.8));
            retaliate(self, e);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + 1, target.getZ(),
                12, 0.5, 0.5, 0.5, 0);
    }

    private static void followOwner(Mob self, ShikigamiMob info, ServerPlayer owner) {
        double dist = self.distanceToSqr(owner);
        if (dist > TELEPORT_BACK_SQ) {
            // 过远（如主人跑太远/被地形卡住）：传送回主人身边，而不是消失
            double dx = (self.getRandom().nextDouble() - 0.5) * 2.0;
            double dz = (self.getRandom().nextDouble() - 0.5) * 2.0;
            self.teleportTo(owner.getX() + dx, owner.getY(), owner.getZ() + dz);
            self.getNavigation().stop();
            return;
        }
        if (dist > 4 * 4) {
            self.getNavigation().moveTo(owner, info.getState().speed);
        } else {
            self.getNavigation().stop();
        }
    }

    private static void wanderPassively(Mob self, ShikigamiMob info, ServerPlayer owner) {
        LivingEntity danger = self.level().getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(self.position(), 24, 24, 24),
                e -> e != self && e != owner && e.isAlive() && e instanceof Enemy)
                .stream().min((a, b) -> Double.compare(self.distanceToSqr(a), self.distanceToSqr(b))).orElse(null);
        if (danger != null && self.distanceToSqr(danger) < 12 * 12) {
            Vec3 away = self.position().subtract(danger.position()).normalize().scale(16);
            self.getNavigation().moveTo(self.getX() + away.x, self.getY(), self.getZ() + away.z,
                    info.getState().speed * 1.5);
            if (self.getRandom().nextInt(20) == 0 && self.distanceTo(danger) < 4.0) {
                danger.hurt(self.damageSources().mobAttack(self), 0.5F);
                retaliate(self, danger);
            }
            return;
        }
        if (self.getRandom().nextInt(40) == 0) {
            double x = self.getX() + self.getRandom().nextDouble() * 8 - 4;
            double z = self.getZ() + self.getRandom().nextDouble() * 8 - 4;
            self.getNavigation().moveTo(x, self.getY(), z, info.getState().speed * 1.2);
        }
    }

    // ============================================================
    //  客户端粒子（渲染层由原版渲染器负责，这里仅服务端效果粒子）
    // ============================================================

    /** 魔虚罗适应减伤（hurt 时调用） */
    public static float adaptDamage(Mob self, ShikigamiMob info, float amount) {
        ShikigamiState st = info.getState();
        if (info.getShikigamiType() == ShikigamiType.MAHORAGA && !self.level().isClientSide) {
            if (st.adaptation < 12) st.adaptation++;
            amount *= (1.0F - 0.05F * st.adaptation);
        }
        return Math.max(0, amount);
    }

    /** 魔虚罗再生（aiStep 里调用） */
    public static void regenMahoraga(Mob self, ShikigamiMob info) {
        if (!self.level().isClientSide && self.tickCount % 40 == 0 && self.getHealth() < self.getMaxHealth()) {
            self.heal(1.0F);
        }
    }

    /** 死亡调伏（die 时调用） */
    public static void onDeath(Mob self, ShikigamiMob info) {
        if (!self.level().isClientSide && !info.getState().tamed) {
            ServerPlayer owner = info.getOwner();
            if (owner != null) {
                ShikigamiHandler.onShikigamiDefeated(owner, info.getShikigamiType());
            }
        }
    }

    /** 初始化数值（召唤时，服务端）：血量/伤害/移速/体型 */
    public static void initStats(Mob self, ShikigamiMob info, ServerPlayer player, ShikigamiType type,
                                 boolean tamed, @Nullable LivingEntity locked, int variant) {
        ShikigamiState st = info.getState();
        st.type = type;
        st.ownerId = player.getUUID();
        st.lockedId = locked != null ? locked.getUUID() : null;
        st.tamed = tamed;
        st.variant = variant;
        st.damage = type.scaledDamage(player);
        st.speed = type.scaledSpeed(player);
        st.scale = type.scaledSize(player);
        st.despawnTimer = 600;

        self.getAttribute(Attributes.MAX_HEALTH).setBaseValue(type.scaledHp(player));
        self.setHealth((float) type.scaledHp(player));
        self.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(st.speed);
        // 部分原版生物（青蛙/猪/羊/山羊/牛/兔）没有 ATTACK_DAMAGE 属性，伤害走 st.damage
        if (self.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            self.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(st.damage);
        }
        self.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(32.0);
        self.setPersistenceRequired();
        self.setCustomName(net.minecraft.network.chat.Component.translatable(type.getLangKey()));
        self.setCustomNameVisible(false);
        if (type == ShikigamiType.NUE) {
            self.setNoGravity(true);
        }
        self.refreshDimensions();
    }
}
