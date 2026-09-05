package com.mofengbaizhi.tinkersnewlife.content.curse.technique;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.entity.PuppetUtil;
import com.mofengbaizhi.tinkersnewlife.content.entity.SkyUsoraBoltEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 术式「天空操术」。
 * <p>
 * 顺转（释放键）：视线内有敌对目标 → 将目标沿视线方向击飞推开（纯位移，不造成伤害）；
 * 无目标 → 给自己一个沿视线方向的推力（冲刺/跃空）。
 * 反转（反转键 F）：对视线锁定目标发射「宇守罗弹」——追踪命中时如击碎薄冰般
 * 将"空间"连同对手一起击飞（巨量击退 + 咒术伤害，命中时套核心材料特性）。
 * <p>
 * 推力/伤害与消耗都随咒力亲和、输出提高：强度 = (1+亲和/100)×(1.6+输出×0.15)，
 * 消耗 = ceil((1+亲和/100)×(顺转 6+输出×3 / 反转 12+输出×5))。
 */
public final class SkyManipulationTechnique extends BaseTechnique {

    public static final SkyManipulationTechnique INSTANCE = new SkyManipulationTechnique();

    private SkyManipulationTechnique() {
        super(Modifiers.SKY_MANIPULATION.getId());
    }

    // ================= 力量与消耗（随亲和/输出提高） =================

    /** 推力强度：作为速度倍率使用 */
    private static double pushPower(ServerPlayer player) {
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        return (1.0 + affinity / 100.0) * (1.6 + output * 0.15);
    }

    /** 击飞强度（宇守罗弹命中，水平速度倍率） */
    private static float knockPower(ServerPlayer player) {
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        return (float) ((1.0 + affinity / 100.0) * (2.0 + output * 0.2));
    }

    private static int skyCost(ServerPlayer player, boolean reverse) {
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        double base = reverse ? (12 + output * 5.0) : (6 + output * 3.0);
        return Math.max(1, (int) Math.ceil((1.0 + affinity / 100.0) * base));
    }

    private static boolean paySkyCost(ServerPlayer player, boolean reverse) {
        if (CursePowerHelper.isCurseInfinite(player)) return true;
        return CursePowerHelper.payCurseWithSoulFallback(player, skyCost(player, reverse)) >= 0;
    }

    // ================= 顺转：推开目标 / 无目标自推 =================

    @Override
    public void onKeyPress(ServerPlayer player) {
        if (CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return;
        }
        if (!paySkyCost(player, false)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
            return;
        }
        double power = pushPower(player);
        Vec3 look = player.getLookAngle();
        LivingEntity target = findEnemyTarget(player);
        if (target != null) {
            // 推开目标：沿视线方向（含一点上抛）
            Vec3 vel = target.getDeltaMovement()
                    .add(look.scale(power))
                    .add(0, 0.3 + power * 0.1, 0);
            target.setDeltaMovement(vel);
            target.hurtMarked = true;
            if (player.serverLevel() != null) {
                ServerLevel level = player.serverLevel();
                level.sendParticles(ParticleTypes.CLOUD,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                        14, 0.5, 0.3, 0.5, 0.02);
                // 视线方向的气流轨迹
                Vec3 from = player.getEyePosition(1.0F);
                Vec3 to = target.position();
                Vec3 d = to.subtract(from);
                double dist = d.length();
                if (dist > 1e-4) {
                    Vec3 step = d.normalize().scale(dist / 3.0);
                    for (int i = 1; i <= 2; i++) {
                        Vec3 p = from.add(step.scale(i));
                        level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0.2, 0.2, 0.2, 0);
                    }
                }
            }
        } else {
            // 无目标：给自己一个沿视线方向的推力（冲刺/跃空）
            Vec3 vel = player.getDeltaMovement()
                    .add(look.scale(power))
                    .add(0, 0.25 + power * 0.06, 0);
            player.setDeltaMovement(vel);
            player.hurtMarked = true;
            // 落空缓冲：上抛部分不产生摔落伤害
            player.fallDistance = Math.max(0, player.fallDistance - (float) power);
            if (player.serverLevel() != null) {
                player.serverLevel().sendParticles(ParticleTypes.CLOUD,
                        player.getX(), player.getY() + 0.2, player.getZ(),
                        18, 0.6, 0.3, 0.6, 0.03);
                player.serverLevel().sendParticles(ParticleTypes.SWEEP_ATTACK,
                        player.getX(), player.getY() + 0.5, player.getZ(),
                        2, 0.6, 0.3, 0.6, 0);
            }
        }
        // 不弹"术式发动"提示
    }

    // ================= 反转：宇守罗弹 =================

    @Override
    public void onReverseKeyPress(ServerPlayer player) {
        if (CursePowerHelper.isSealed(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.sealed.active",
                    CursePowerHelper.getSealedRemainingSeconds(player)), true);
            return;
        }
        if (!paySkyCost(player, true)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
            return;
        }
        LivingEntity target = findEnemyTarget(player);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_target"), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        // 咒术伤害基底 ×1.1，套魔杖增幅（材料特性在命中时套）
        double raw = amplifyTechniqueDamage(player, computeBaseDamage(player) * 1.1);
        SkyUsoraBoltEntity bolt = new SkyUsoraBoltEntity(ModEntities.SKY_USORA_BOLT.get(), level);
        bolt.moveTo(player.getX(), player.getEyeY() - 0.2, player.getZ(),
                player.getYRot(), player.getXRot());
        bolt.launch(player, target, (float) raw, knockPower(player));
        level.addFreshEntity(bolt);
        // 不弹"术式发动"提示
    }

    /** 视线内第一个敌对目标（同队除外） */
    private LivingEntity findEnemyTarget(ServerPlayer player) {
        var eye = player.getEyePosition(1.0F);
        var look = player.getLookAngle();
        var end = eye.add(look.scale(REACH));
        var box = player.getBoundingBox().expandTowards(look.scale(REACH)).inflate(1.0);
        var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                player, eye, end, box,
                e -> !e.isSpectator() && e.isPickable() && e instanceof LivingEntity le
                        && le.isAlive() && !PuppetUtil.isAllyOf(le, player),
                REACH * REACH);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }
}
