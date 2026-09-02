package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;

/**
 * 术式「反转术式」：
 * <p>
 * - 按术式键（C）：对自身释放反转术式——消耗 咒力输出×10 点咒力，
 *   恢复 (1+(亲和/10+输出)/10)×输出×2 点生命
 * - 按术式反转键（F）：反转术式外放——对施术目标恢复生命；
 *   若目标是亡灵生物，则受到应恢复生命数值的 2 倍伤害
 */
public final class ReverseCursedTechnique extends BaseTechnique {

    public static final ReverseCursedTechnique INSTANCE = new ReverseCursedTechnique();

    private ReverseCursedTechnique() {
        super(Modifiers.REVERSE_CURSED.getId());
    }

    /** 咒力消耗 = 咒力输出 × 10 点 */
    @Override
    protected int getCost(ServerPlayer player) {
        return CursePowerHelper.getCurseOutputLevel(player) * 10;
    }

    /** 恢复生命量 = (1+(亲和/10+输出)/10)×输出×2 */
    private double getHealAmount(ServerPlayer player) {
        int affinity = CursePowerHelper.getCurseAffinity(player);
        int output = CursePowerHelper.getCurseOutputLevel(player);
        return (1.0 + (affinity / 10.0 + output) / 10.0) * output * 2.0;
    }

    /** 按下术式键（C）：对自身释放反转术式 */
    @Override
    public void onKeyPress(ServerPlayer player) {
        if (CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return;
        }
        if (!payCost(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
            return;
        }
        double heal = getHealAmount(player);
        player.heal((float) heal);
    }

    /** 按下术式反转键（F）：反转术式外放——治疗目标；亡灵则受 2 倍恢复量伤害 */
    @Override
    public void onReverseKeyPress(ServerPlayer player) {
        if (CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return;
        }
        LivingEntity target = findTarget(player);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_target"), true);
            return;
        }
        if (!isTargetInRange(player, target)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.too_far"), true);
            return;
        }
        if (!payCost(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
            return;
        }
        double heal = getHealAmount(player);
        if (target.getMobType() == MobType.UNDEAD) {
            // 亡灵：受到应恢复生命数值的 2 倍伤害
            float dmg = (float) (heal * 2.0);
            dmg = (float) amplifyTechniqueDamage(player, dmg);
            dmg = (float) com.mofengbaizhi.tinkersnewlife.util.CurseCoreTraitHelper
                    .applyCurseCoreTraits(player, target, dmg);
            target.invulnerableTime = 0;
            target.hurt(target.damageSources().mobAttack(player), dmg);
            com.mofengbaizhi.tinkersnewlife.util.CurseCoreTraitHelper.afterCurseCoreHit(player, target, dmg);
        } else {
            target.heal((float) heal);
        }
        // 白色光芒粒子：施术者 → 目标连线 + 目标处光爆
        spawnWhiteLight(player, target);
    }

    /** 外放距离限制：3 格内 */
    @Override
    protected boolean isTargetInRange(ServerPlayer player, LivingEntity target) {
        return player.distanceToSqr(target) < 3.0 * 3.0;
    }

    /** 白色光芒粒子特效 */
    private void spawnWhiteLight(ServerPlayer player, LivingEntity target) {
        var server = player.serverLevel();
        var white = new org.joml.Vector3f(1.0F, 1.0F, 0.95F);
        // 目标处白色光爆
        server.sendParticles(new net.minecraft.core.particles.DustParticleOptions(white, 1.6F),
                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                24, 0.4, 0.4, 0.4, 0.02);
        // 施术者 → 目标连线光芒
        net.minecraft.world.phys.Vec3 from = player.getEyePosition(1.0F);
        net.minecraft.world.phys.Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
        net.minecraft.world.phys.Vec3 delta = to.subtract(from);
        double dist = delta.length();
        if (dist > 0.1) {
            net.minecraft.world.phys.Vec3 step = delta.normalize().scale(0.4);
            for (double d = 0.3; d < dist; d += 0.4) {
                net.minecraft.world.phys.Vec3 p = from.add(step.scale(d / 0.4));
                server.sendParticles(new net.minecraft.core.particles.DustParticleOptions(white, 1.2F),
                        p.x, p.y, p.z, 2, 0.08, 0.08, 0.08, 0.01);
            }
        }
    }
}
