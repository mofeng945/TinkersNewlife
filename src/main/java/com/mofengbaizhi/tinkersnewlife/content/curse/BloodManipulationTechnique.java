package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
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
 * 术式「赤血操术·穿血」：以血为刃，贯穿万敌。
 * <p>
 * 每次发动消耗 5% 最大生命值（创造模式不消耗，伤害仍按理论消耗计算），
 * 向玩家当前朝向释放一道血激光束，穿透面前 20 格（方块不阻挡）：
 * <ul>
 *   <li>伤害 = (1 + 咒力亲和/100) × (咒力输出等级 + 消耗血量×5)</li>
 *   <li>每个被穿透的目标只受到一次伤害（可被护甲衰减，无视无敌帧）</li>
 *   <li>光束粒子显示约 0.5 秒</li>
 *   <li>咒力消耗 = (1 - 咒力亲和/100) × (10 + 咒力输出×5) ÷ 2</li>
 * </ul>
 */
public final class BloodManipulationTechnique extends BaseTechnique {

    public static final BloodManipulationTechnique INSTANCE = new BloodManipulationTechnique();

    /** 光束穿透距离（格） */
    public static final double RANGE = 20.0;
    /** 每次发动消耗最大生命值的比例 */
    public static final double BLOOD_COST_RATIO = 0.05;

    /** 光束粒子间隔（格） */
    private static final double PARTICLE_STEP = 0.5;

    private BloodManipulationTechnique() {
        super(Modifiers.BLOOD_MANIPULATION.getId());
    }

    /** 赤血操术为直线穿透，无需索敌目标：熔断 → 咒力 → 血量 → 释放光束 */
    @Override
    public boolean tryUse(ServerPlayer player) {
        if (CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return false;
        }
        if (!payCost(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
            return false;
        }
        // 血量检查：当前血量必须严格大于 5% 消耗，避免术式致死
        double bloodCost = player.getMaxHealth() * BLOOD_COST_RATIO;
        if (!player.isCreative() && player.getHealth() <= (float) bloodCost) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_blood"), true);
            return false;
        }
        if (!player.isCreative()) {
            player.setHealth(player.getHealth() - (float) bloodCost);
        }
        fireBloodBeam(player, bloodCost);
        return true;
    }

    /** 咒力消耗 = 「解」的一半（最低 1 点） */
    @Override
    protected int getCost(ServerPlayer player) {
        return Math.max(1, super.getCost(player) / 2);
    }

    /** 释放血激光束：红色光束粒子 + 穿透路径上每个目标一次伤害（材料特性照常触发） */
    private void fireBloodBeam(ServerPlayer player, double bloodCost) {
        ServerLevel level = player.serverLevel();
        Vec3 start = player.getEyePosition(1.0F);
        Vec3 dir = player.getLookAngle().normalize();
        Vec3 end = start.add(dir.scale(RANGE));

        // 光束视觉：沿路径红色 dust 粒子（粒子寿命约 0.5~1s，呈现为短暂血光束）
        for (double d = 0; d <= RANGE; d += PARTICLE_STEP) {
            Vec3 p = start.add(dir.scale(d));
            level.sendParticles(new DustParticleOptions(new Vector3f(0.85F, 0.05F, 0.05F), 1.0F),
                    p.x, p.y, p.z, 1, 0.08, 0.08, 0.08, 0);
        }
        // 起点音爆波强化光束感
        level.sendParticles(ParticleTypes.SONIC_BOOM, start.x, start.y, start.z, 1, dir.x, dir.y, dir.z, 0.25);

        // 穿透判定：扫描射线盒内实体，按"中心点到射线距离 ≤ 命中半径"筛选，每个目标一次
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

            // 伤害 = (1 + 咒力亲和/100) × (咒力输出 + 消耗血量×5)，魔杖增幅 + 材料特性
            double damage = computeDamage(player, bloodCost);
            damage = amplifyTechniqueDamage(player, damage);
            damage = com.mofengbaizhi.tinkersnewlife.util.CurseCoreTraitHelper
                    .applyCurseCoreTraits(player, target, damage);
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().mobAttack(player), (float) damage);
            com.mofengbaizhi.tinkersnewlife.util.CurseCoreTraitHelper.afterCurseCoreHit(player, target, damage);

            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, center.x, center.y, center.z, 4, 0.2, 0.2, 0.2, 0);
        }
    }

    /** 穿血单体伤害（供「赤血操术·百敛」复用）：(1 + 咒力亲和/100) × (咒力输出 + 消耗血量×5)，消耗血量 = 最大生命 × 5% */
    public static double singleTargetDamage(ServerPlayer player) {
        return computeDamage(player, player.getMaxHealth() * BLOOD_COST_RATIO);
    }

    /** 伤害 = (1 + 咒力亲和/100) × (咒力输出等级 + 消耗血量×5) */
    private static double computeDamage(ServerPlayer player, double bloodCost) {
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        return (1.0 + affinity / 100.0) * (output + bloodCost * 5.0);
    }
}
