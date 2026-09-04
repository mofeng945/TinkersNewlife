package com.mofengbaizhi.tinkersnewlife.content.curse.technique;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.BloodManipulationTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.BaseTechnique;

import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 术式「赤血操术·百敛」：血之百道，尽归一身。
 * <p>
 * 指向目标（视线索敌），从玩家身体周围释放 5 道血柱，轨迹最终指向目标：
 * <ul>
 *   <li>对目标造成 5 次伤害，每次 = 「赤血操术·穿血」单体伤害 ÷ 2（无视无敌帧，可被护甲衰减）</li>
 *   <li>每道伤害都触发材料战斗特性</li>
 *   <li>每次发动消耗 7% 最大生命值（创造模式不消耗）</li>
 *   <li>咒力消耗 = 「解」 × 5/3</li>
 * </ul>
 */
public final class BloodManipulationHyakurenTechnique extends BaseTechnique {

    public static final BloodManipulationHyakurenTechnique INSTANCE = new BloodManipulationHyakurenTechnique();

    /** 血柱/伤害次数 */
    public static final int HIT_COUNT = 5;
    /** 每次伤害 = 穿血单体伤害 ÷ 2 */
    public static final double DAMAGE_DIVISOR = 2.0;
    /** 咒力消耗 = 解 × 5/3 */
    public static final double COST_MULTIPLIER = 5.0 / 3.0;
    /** 每次发动消耗最大生命值的比例（7%） */
    public static final double BLOOD_COST_RATIO = 0.07;

    /** 血柱起点环绕半径（格） */
    private static final double PILLAR_RADIUS = 0.9;
    /** 血柱粒子间隔（格） */
    private static final double PARTICLE_STEP = 0.4;

    private BloodManipulationHyakurenTechnique() {
        super(Modifiers.BLOOD_MANIPULATION_HYAKUREN.getId());
    }

    /** 咒力消耗 = 「解」 × 5/3（最低 1 点） */
    @Override
    protected int getCost(ServerPlayer player) {
        return Math.max(1, (int) Math.ceil(super.getCost(player) * COST_MULTIPLIER));
    }

    /**
     * 百敛流程：熔断 → 咒力 → 索敌 → 血量（7%） → 五道血柱连击。
     * 未命中目标时不消耗血量（咒力已扣）。
     */
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
        LivingEntity target = findTarget(player);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_target"), true);
            return false;
        }
        // 血量检查：当前血量必须严格大于 7% 消耗，避免术式致死
        double bloodCost = player.getMaxHealth() * BLOOD_COST_RATIO;
        if (!player.isCreative() && player.getHealth() <= (float) bloodCost) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_blood"), true);
            return false;
        }
        if (!player.isCreative()) {
            player.setHealth(player.getHealth() - (float) bloodCost);
        }
        onCast(player, target);
        return true;
    }

    @Override
    protected void onCast(ServerPlayer player, LivingEntity target) {
        ServerLevel level = player.serverLevel();
        double perHit = BloodManipulationTechnique.singleTargetDamage(player) / DAMAGE_DIVISOR;

        // 5 道血柱视觉：身体周围环绕 5 个起点，各自射向目标中心
        spawnBloodPillars(level, player, target);

        // 5 次伤害：无视无敌帧，材料战斗特性每道触发
        for (int i = 0; i < HIT_COUNT; i++) {
            double dmg = amplifyTechniqueDamage(player, perHit);
            dmg = com.mofengbaizhi.tinkersnewlife.content.curse.CurseCoreTraitHelper
                    .applyCurseCoreTraits(player, target, dmg);
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().mobAttack(player), (float) dmg);
            com.mofengbaizhi.tinkersnewlife.content.curse.CurseCoreTraitHelper.afterCurseCoreHit(player, target, dmg);
        }

        // 命中处伤害指示
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
            // 起点血雾
            level.sendParticles(new DustParticleOptions(bloodColor, 1.0F),
                    start.x, start.y, start.z, 5, 0.15, 0.15, 0.15, 0);
            // 血柱路径
            for (double d = 0; d <= dist; d += PARTICLE_STEP) {
                Vec3 p = start.add(dir.scale(d));
                level.sendParticles(new DustParticleOptions(bloodColor, 1.0F),
                        p.x, p.y, p.z, 1, 0.06, 0.06, 0.06, 0);
            }
        }
        // 终点音爆波强化命中感
        level.sendParticles(ParticleTypes.SONIC_BOOM,
                targetCenter.x, targetCenter.y, targetCenter.z, 1, 0, 1, 0, 0.2);
    }
}
