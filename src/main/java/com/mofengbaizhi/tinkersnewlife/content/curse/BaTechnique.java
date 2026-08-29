package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 术式「捌」：对 3 格内的接触目标同时发动 3 道横向斩击 + 3 道纵向斩击
 * <p>
 * 每道斩击伤害 = 「解」伤害的二分之一（共享伤害基底 × 70% ÷ 2），无视无敌帧。
 * 咒力消耗为「解」的 2 倍。
 */
public final class BaTechnique extends BaseTechnique {

    public static final BaTechnique INSTANCE = new BaTechnique();

    /** 接触距离：目标需在 3 格内 */
    private static final double TOUCH_RANGE = 3.0;
    /** 斩击总数：3 横向 + 3 纵向 */
    private static final int SLASH_COUNT = 6;

    private BaTechnique() {
        super(Modifiers.BA.getId());
    }

    /** 「捌」为接触术式：目标必须距玩家 3 格以内 */
    @Override
    protected boolean isTargetInRange(ServerPlayer player, LivingEntity target) {
        return player.distanceToSqr(target) <= TOUCH_RANGE * TOUCH_RANGE;
    }

    /** 咒力消耗为「解」的 2 倍 */
    @Override
    protected int getCost(ServerPlayer player) {
        return super.getCost(player) * 2;
    }

    @Override
    protected void onCast(ServerPlayer player, LivingEntity target) {
        // 每道斩击伤害 = 解的二分之一（共享伤害基底 × 70% ÷ 2）
        double perSlash = computeBaseDamage(player) * (KaiTechnique.DAMAGE_FACTOR / 2.0);
        // 3 横向 + 3 纵向，无视无敌帧：每次命中前清零受伤间隔
        for (int i = 0; i < SLASH_COUNT; i++) {
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().mobAttack(player), (float) perSlash);
        }
        // 斩击粒子：3 道横向（不同高度横扫弧）+ 3 道纵向（不同旋转角横扫弧）
        ServerLevel level = player.serverLevel();
        Vec3 pos = target.position();
        for (int i = 0; i < 3; i++) {
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y + 0.3 + i * 0.3, pos.z, 1, 0.9, 0, 0, 0);
        }
        for (int i = 0; i < 3; i++) {
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y + 0.6, pos.z, 1, 0.9, 60 + i * 60, 0, 0);
        }
        level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y + 0.5, pos.z, 6, 0.4, 0.4, 0.4, 0);
    }
}
