package com.mofengbaizhi.tinkersnewlife.content.curse.technique;

import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.BaseTechnique;

import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.entity.BloodNovaEntity;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * 术式「赤血操术」：整合「穿血 / 百敛 / 超新星」三种招式于一个术式（占用 1 个术式槽）。
 * <p>
 * 操作：
 * <ul>
 *   <li><b>C（术式键）</b>：释放当前招式——穿血 = 血激光束直线穿透 20 格（不索敌）；
 *       百敛 = 索敌，身体周围 5 道血柱共 5 次伤害；超新星 = 索敌，目标处生成血球延迟爆炸。</li>
 *   <li><b>F（反转键）</b>：在 穿血 → 百敛 → 超新星 间轮换当前招式。</li>
 * </ul>
 * 三招均消耗最大生命值（穿血 5% / 百敛 7% / 超新星 10%，创造模式不消耗，伤害按理论消耗计算），
 * 咒力消耗：穿血「解」一半 / 百敛「解」×5/3 / 超新星「解」×5/2。
 */
public final class BloodManipulationTechnique extends BaseTechnique {

    public static final BloodManipulationTechnique INSTANCE = new BloodManipulationTechnique();

    /** 招式：穿血 */
    public static final int MODE_CHUANXUE = 0;
    /** 招式：百敛 */
    public static final int MODE_BAILIAN = 1;
    /** 招式：超新星 */
    public static final int MODE_SUPERNOVA = 2;

    /** 玩家持久数据键：赤血操术当前招式 */
    public static final String KEY_MODE = "tinkersnewlife.blood_mode";

    /** 光束穿透距离（格） */
    public static final double RANGE = 20.0;
    /** 血柱/伤害次数（百敛） */
    public static final int HIT_COUNT = 5;
    /** 爆炸半径（格，超新星） */
    public static final double EXPLOSION_RADIUS = 5.0;

    /** 各招式消耗最大生命值比例 */
    private static final double[] BLOOD_COST_RATIO = {0.05, 0.07, 0.10};
    /** 各招式咒力消耗倍率（相对「解」基础） */
    private static final double[] CURSE_MULTIPLIER = {0.5, 5.0 / 3.0, 5.0 / 2.0};

    /** 光束粒子间隔（格） */
    private static final double PARTICLE_STEP = 0.5;
    /** 百敛血柱起点环绕半径（格） */
    private static final double PILLAR_RADIUS = 0.9;
    /** 百敛血柱粒子间隔（格） */
    private static final double PILLAR_STEP = 0.4;

    private BloodManipulationTechnique() {
        super(Modifiers.BLOOD_MANIPULATION.getId());
    }

    // ==================== 招式状态 ====================

    /** 当前招式（0 穿血 / 1 百敛 / 2 超新星） */
    public static int getMode(ServerPlayer player) {
        return Math.max(0, Math.min(MODE_SUPERNOVA, player.getPersistentData().getInt(KEY_MODE)));
    }

    private static void setMode(ServerPlayer player, int mode) {
        player.getPersistentData().putInt(KEY_MODE, Math.max(0, Math.min(MODE_SUPERNOVA, mode)));
    }

    private static Component modeName(int mode) {
        return switch (mode) {
            case MODE_BAILIAN -> Component.translatable("message.tinkersnewlife.blood.mode_bailian");
            case MODE_SUPERNOVA -> Component.translatable("message.tinkersnewlife.blood.mode_supernova");
            default -> Component.translatable("message.tinkersnewlife.blood.mode_chuanxue");
        };
    }

    // ==================== 按键钩子 ====================

    /** F（反转键）：轮换招式 穿血 → 百敛 → 超新星 */
    @Override
    public void onReverseKeyPress(ServerPlayer player) {
        if (CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return;
        }
        int next = (getMode(player) + 1) % 3;
        setMode(player, next);
        player.displayClientMessage(Component.translatable(
                "message.tinkersnewlife.blood.switch", modeName(next)), true);
    }

    /** C：释放当前招式（穿血直线不索敌；百敛/超新星需视线索敌） */
    @Override
    public boolean tryUse(ServerPlayer player) {
        if (CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return false;
        }
        int mode = getMode(player);
        double bloodCost = player.getMaxHealth() * BLOOD_COST_RATIO[mode];
        // ⭐ 先校验全部发动条件（生命代价 / 索敌），通过后才扣咒力：
        // 原先「先扣费→再索敌」，未锁定敌人时发动失败却已扣掉咒力（不退款）。
        if (!player.isCreative() && player.getHealth() <= (float) bloodCost) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_blood"), true);
            return false;
        }
        LivingEntity target = null;
        if (mode != MODE_CHUANXUE) {
            // 百敛 / 超新星需要索敌；穿血是直线血束、不索敌
            target = findTarget(player);
            if (target == null) {
                player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_target"), true);
                return false;
            }
        }
        if (!payCost(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
            return false;
        }

        if (mode == MODE_CHUANXUE) {
            // 穿血：直线血束，不索敌
            if (!player.isCreative()) player.setHealth(player.getHealth() - (float) bloodCost);
            fireBloodBeam(player, bloodCost);
            return true;
        }
        if (!player.isCreative()) player.setHealth(player.getHealth() - (float) bloodCost);
        if (mode == MODE_BAILIAN) {
            castBailian(player, target);
        } else {
            spawnNova(player, target, bloodCost);
        }
        return true;
    }

    /** 咒力消耗：穿血「解」一半 / 百敛「解」×5/3 / 超新星「解」×5/2（最低 1 点） */
    @Override
    protected int getCost(ServerPlayer player) {
        return Math.max(1, (int) Math.ceil(super.getCost(player) * CURSE_MULTIPLIER[getMode(player)]));
    }

    // ==================== 穿血：直线血束 ====================

    /** 释放血激光束：红色光束粒子 + 穿透路径上每个目标一次伤害（材料特性照常触发） */
    private void fireBloodBeam(ServerPlayer player, double bloodCost) {
        ServerLevel level = player.serverLevel();
        Vec3 start = player.getEyePosition(1.0F);
        Vec3 dir = player.getLookAngle().normalize();
        Vec3 end = start.add(dir.scale(RANGE));

        for (double d = 0; d <= RANGE; d += PARTICLE_STEP) {
            Vec3 p = start.add(dir.scale(d));
            level.sendParticles(new DustParticleOptions(new Vector3f(0.85F, 0.05F, 0.05F), 1.0F),
                    p.x, p.y, p.z, 1, 0.08, 0.08, 0.08, 0);
        }
        level.sendParticles(ParticleTypes.SONIC_BOOM, start.x, start.y, start.z, 1, dir.x, dir.y, dir.z, 0.25);

        AABB scanBox = new AABB(start, end).inflate(1.5);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, scanBox,
                e -> e != player && e.isAlive() && !e.isSpectator());
        for (LivingEntity target : entities) {
            Vec3 center = target.position().add(0, target.getBbHeight() * 0.5, 0);
            Vec3 rel = center.subtract(start);
            double t = rel.dot(dir);
            if (t < 0 || t > RANGE) continue;
            double distToRay = center.distanceTo(start.add(dir.scale(t)));
            double hitRadius = Math.max(0.5, target.getBbWidth() * 0.5) + 0.3;
            if (distToRay > hitRadius) continue;

            double damage = computeDamage(player, bloodCost);
            damage = amplifyTechniqueDamage(player, damage);
            damage = com.mofengbaizhi.tinkersnewlife.content.curse.CurseCoreTraitHelper
                    .applyCurseCoreTraits(player, target, damage);
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().mobAttack(player), (float) damage);
            com.mofengbaizhi.tinkersnewlife.content.curse.CurseCoreTraitHelper.afterCurseCoreHit(player, target, damage);

            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, center.x, center.y, center.z, 4, 0.2, 0.2, 0.2, 0);
        }
    }

    // ==================== 百敛：五道血柱 ====================

    /** 百敛：身体周围 5 道血柱，共 5 次伤害，每次 = 穿血单体伤害 ÷ 2 */
    private void castBailian(ServerPlayer player, LivingEntity target) {
        ServerLevel level = player.serverLevel();
        double perHit = singleTargetDamage(player) / 2.0;
        spawnBloodPillars(level, player, target);

        for (int i = 0; i < HIT_COUNT; i++) {
            double dmg = amplifyTechniqueDamage(player, perHit);
            dmg = com.mofengbaizhi.tinkersnewlife.content.curse.CurseCoreTraitHelper
                    .applyCurseCoreTraits(player, target, dmg);
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().mobAttack(player), (float) dmg);
            com.mofengbaizhi.tinkersnewlife.content.curse.CurseCoreTraitHelper.afterCurseCoreHit(player, target, dmg);
        }
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                HIT_COUNT, 0.3, 0.3, 0.3, 0);
    }

    /** 从身体周围 5 个起点各放一道指向目标的血柱（红色 dust 沿路径） */
    private void spawnBloodPillars(ServerLevel level, ServerPlayer player, LivingEntity target) {
        Vec3 targetCenter = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 playerPos = player.position();
        Vector3f bloodColor = new Vector3f(0.85F, 0.05F, 0.05F);
        for (int i = 0; i < HIT_COUNT; i++) {
            double angle = 2 * Math.PI * i / HIT_COUNT;
            Vec3 start = playerPos.add(
                    Math.cos(angle) * PILLAR_RADIUS,
                    0.6 + level.random.nextDouble() * 0.6,
                    Math.sin(angle) * PILLAR_RADIUS);
            Vec3 dir = targetCenter.subtract(start).normalize();
            double dist = start.distanceTo(targetCenter);
            level.sendParticles(new DustParticleOptions(bloodColor, 1.0F),
                    start.x, start.y, start.z, 5, 0.15, 0.15, 0.15, 0);
            for (double d = 0; d <= dist; d += PILLAR_STEP) {
                Vec3 p = start.add(dir.scale(d));
                level.sendParticles(new DustParticleOptions(bloodColor, 1.0F),
                        p.x, p.y, p.z, 1, 0.06, 0.06, 0.06, 0);
            }
        }
        level.sendParticles(ParticleTypes.SONIC_BOOM,
                targetCenter.x, targetCenter.y, targetCenter.z, 1, 0, 1, 0, 0.2);
    }

    // ==================== 超新星：血球爆炸 ====================

    /** 超新星：在目标中心生成血球，延迟爆炸 */
    private void spawnNova(ServerPlayer player, LivingEntity target, double bloodCost) {
        Vec3 pos = target.position().add(0, target.getBbHeight() * 0.5, 0);
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        double centerDamage = (1.0 + affinity / 100.0) * output * bloodCost * 10.0;
        centerDamage = amplifyTechniqueDamage(player, centerDamage);
        player.serverLevel().addFreshEntity(new BloodNovaEntity(
                player.serverLevel(), pos, player.getUUID(),
                (float) centerDamage, (float) EXPLOSION_RADIUS));
    }

    // ==================== 共享工具 ====================

    /** 穿血单体伤害（供百敛复用）：(1 + 咒力亲和/100) × (咒力输出 + 消耗血量×5)，消耗血量 = 最大生命 × 5% */
    public static double singleTargetDamage(ServerPlayer player) {
        return computeDamage(player, player.getMaxHealth() * BLOOD_COST_RATIO[MODE_CHUANXUE]);
    }

    /** 伤害 = (1 + 咒力亲和/100) × (咒力输出等级 + 消耗血量×5) */
    private static double computeDamage(ServerPlayer player, double bloodCost) {
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        return (1.0 + affinity / 100.0) * (output + bloodCost * 5.0);
    }
}
