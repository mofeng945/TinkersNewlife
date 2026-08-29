package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * 术式「解」：对看向的实体释放一次斩击
 * <p>
 * 伤害 = 共享伤害基底 × 70%，类似伏魔御厨子的小斩击
 * （无视无敌帧、正常护甲结算、横扫粒子特效）。
 * 每次释放消耗 (1-咒力亲和/100)×(10+咒力输出×5) 点咒力（不足时灵魂能量兜底）。
 */
public final class KaiTechnique extends BaseTechnique {

    public static final KaiTechnique INSTANCE = new KaiTechnique();

    /** 「解」的伤害系数：共享伤害基底 × 70%（「捌」的每道斩击 = 该系数的一半） */
    public static final double DAMAGE_FACTOR = 0.70;

    private KaiTechnique() {
        super(Modifiers.KAI.getId());
    }

    @Override
    protected void onCast(ServerPlayer player, LivingEntity target) {
        double damage = computeBaseDamage(player) * DAMAGE_FACTOR;
        // 类似伏魔御厨子小斩击：无视无敌帧，正常伤害结算（护甲/盾牌等仍可衰减）
        target.invulnerableTime = 0;
        target.hurt(player.damageSources().mobAttack(player), (float) damage);
        spawnSlashParticles(player.serverLevel(), player.getEyePosition(), target.position());
    }
}
