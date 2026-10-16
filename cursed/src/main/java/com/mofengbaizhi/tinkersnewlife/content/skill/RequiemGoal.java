package com.mofengbaizhi.tinkersnewlife.content.skill;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * TNL1253 生息绝响（Breath's Requiem），法术编号 4。
 *
 * <p>用户口径：
 * <ul>
 *   <li>展开 **8 格死生领域**，持续 **10 秒**，**或直到墨默被攻击**；</li>
 *   <li>领域内**墨默的仆从**得 力量II ＋ 速度II ＋ 生命恢复II；**墨默本人**得 生命恢复II；</li>
 *   <li>领域内**非友方**每秒受 **生命上限 3% 的魔法伤害**；</li>
 *   <li>只在**血量 &lt; 80%** 时**主动放一次**，冷却 **120 秒**；</li>
 *   <li>期间墨默 **stun**（站定 ＋ 不能攻击）；</li>
 *   <li>领域是**贴地转动的六芒星阵法**（本版用尘埃粒子画六芒星并旋转 ✓ 贴图版以后再说）；</li>
 *   <li>姿势：**双手合十举到脸前**（修女祈祷状）⇒ {@link MomoPose#REQUIEM}。</li>
 * </ul>
 *
 * <p>仆从判定按用户 2026-10-16 的简化口径：**看标记** —— 后面的召唤技能给实体打
 * {@link KtuluMomo#TNL_MINION_TAG} 即可。
 */
public final class RequiemGoal extends MomoSpellGoal {

    private static final int TOTAL_TICKS = 200;          // 10 秒
    private static final int COOLDOWN_TICKS = 2400;      // 120 秒
    private static final double RADIUS = 8.0D;
    private static final int EFFECT_REFRESH = 40;        // 每次给 2 秒 buff，每 20 tick 刷新一次
    private static final float PCT_DAMAGE = 0.03F;       // 每秒 3% 生命上限

    /** 六芒星：旋转用的角度累积（客户端只是看粒子，这里在服务端算） */
    private double spin;

    public RequiemGoal(KtuluMomo momo) {
        super(momo, 4, COOLDOWN_TICKS, TOTAL_TICKS);
    }

    @Override
    protected int cooldownLeft() {
        return (int) Math.max(0L,
                this.momo.tnl$requiemCoolUntil() - this.momo.level().getGameTime());
    }

    @Override
    public boolean canUse() {
        // TNL1259 用户："没有释放死生领域"。加一条只在**血量已低**时输出的探针，
        // 直接看是哪个条件没满足（血量没掉下来 / 已用过 / 冷却 / busy）。
        boolean tnl$lowHp = this.momo.getHealth() <= this.momo.getMaxHealth() * 0.8F;
        if (this.momo.level().getGameTime() % 20L == 0L) {
            TinkersNewlife.LOGGER.warn(
                    "TNL1259 requiem check hp={}/{} low={} used={} cd={} busy={} flag={}",
                    String.format(java.util.Locale.ROOT, "%.0f", this.momo.getHealth()),
                    String.format(java.util.Locale.ROOT, "%.0f", this.momo.getMaxHealth()),
                    tnl$lowHp, this.momo.tnl$requiemUsed(), this.cooldownLeft(),
                    this.momo.tnl$isBusy(), MomoSkills.BREATH_REQUIEM);
        }
        if (!MomoSkills.BREATH_REQUIEM) {
            return false;
        }
        if (this.momo.tnl$requiemUsed()) {
            return false;                                // 一局只放一次
        }
        if (!tnl$lowHp) {
            return false;                                // 只在血量 < 80% 时主动放
        }
        return this.ready();
    }

    @Override
    protected void onStart() {
        this.spin = 0.0D;
        this.momo.tnl$clearRequiemClose();
        this.momo.tnl$markRequiemUsed();
        this.momo.tnl$setAnim(MomoPose.REQUIEM.ordinal(), 0.0F);
        this.stun.begin();                               // 期间 stun：站定 + 不能攻击
        if (this.momo.level() instanceof ServerLevel level) {
            level.playSound(null, this.momo.blockPosition(),
                    net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT,
                    net.minecraft.sounds.SoundSource.HOSTILE, 2.4F, 0.7F);
            level.sendParticles(ParticleTypes.SCULK_SOUL,
                    this.momo.getX(), this.momo.getY() + 1.2D, this.momo.getZ(),
                    60, 2.0D, 0.6D, 2.0D, 0.02D);
        }
        TinkersNewlife.LOGGER.warn("TNL1253 Breath's Requiem opened ({} blocks, 10 s)",
                String.format(java.util.Locale.ROOT, "%.0f", RADIUS));
    }

    @Override
    protected boolean onTick() {
        ServerLevel level = (ServerLevel) this.momo.level();
        long now = level.getGameTime();
        this.stun.keep();                                // 每 tick 压住（近战 Goal 会想重新寻路）


        // 每秒结算一次
        if (this.momo.tnl$castTicks() % 20 == 0) {
            tickEffects(level);
        }

        // 结束条件：10 秒到 或 被攻击
        if (this.momo.tnl$requiemCloseRequested()) {
            TinkersNewlife.LOGGER.warn("TNL1253 Requiem broken by a hit");
            return true;
        }
        return this.momo.tnl$castTicks() <= 0;
    }

    /** 每秒：仆从/自己加 buff，非友方吃 3% 生命上限魔法伤害 */
    private void tickEffects(ServerLevel level) {
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class,
                this.momo.getBoundingBox().inflate(RADIUS))) {
            if (le == this.momo || this.momo.distanceTo(le) > RADIUS) {
                continue;
            }
            if (KtuluMomo.tnl$isMomoMinion(le)) {
                // 仆从：力量 II + 速度 II + 生命恢复 II
                le.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, EFFECT_REFRESH, 1, false, true, true));
                le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, EFFECT_REFRESH, 1, false, true, true));
                le.addEffect(new MobEffectInstance(MobEffects.REGENERATION, EFFECT_REFRESH, 1, false, true, true));
            } else {
                // 非友方：每秒 3% 生命上限的魔法伤害（走 magic 源，能被抗性正常处理）
                le.hurt(level.damageSources().magic(), Math.max(1.0F, le.getMaxHealth() * PCT_DAMAGE));
            }
        }
        // TNL1256 墨默本人：生命恢复效果对它**不生效**（它的血量由血量内核接管）⇒
        // 改成直接按**百分比**回血：领域期间每秒恢复 1% 生命上限。
        float tnl$heal = this.momo.getMaxHealth() * 0.01F;
        this.momo.heal(tnl$heal);
    }

    // TNL1255 六芒星已改为客户端真渲染（KtuluMomoRenderer.tnl$drawRequiemHexagram），
    // 原来的粒子版删掉了；这里只保留每秒的效果结算与收尾粒子。

    @Override
    protected void onStop() {
        this.stun.end();
        this.momo.tnl$clearRequiemClose();
        this.momo.tnl$clearAnim();
        // buff 用短时长（2 秒）自然过期 ⇒ 这里不用手动移除；领域消失后再给一次收尾粒子
        if (this.momo.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SCULK_SOUL,
                    this.momo.getX(), this.momo.getY() + 1.0D, this.momo.getZ(),
                    30, 1.5D, 0.4D, 1.5D, 0.01D);
        }
        this.momo.tnl$setRequiemCoolUntil(
                this.momo.level().getGameTime() + COOLDOWN_TICKS);
    }

    @Override
    protected com.mofengbaizhi.tinkersnewlife.content.skill.MomoPose animPose() {
        return MomoPose.REQUIEM;
    }

    @Override
    protected float animProgress() {
        return this.progress();
    }
}
