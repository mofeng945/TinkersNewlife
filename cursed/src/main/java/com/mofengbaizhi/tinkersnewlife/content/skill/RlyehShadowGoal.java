package com.mofengbaizhi.tinkersnewlife.content.skill;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import com.mofengbaizhi.tinkersnewlife.content.entity.MomoShadow;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * TNL1282 ★★拉莱耶之影（R'lyeh's Shadow），法术编号 5 ✓
 *
 * <p>用户口径：
 * <ul>
 *   <li>召唤 **3 个分身仆从**：**两个**会主动追逐目标攻击，**最后一个**倾向**游走 ＋ 远程攻击**；</li>
 *   <li>测试期：**墨默血量 50% 时自动触发**，**无前摇、无法打断**；</li>
 *   <li>外形 = 本体略带透明偏黑；血量 = **本体的一半**；**无限伤无保护**；</li>
 *   <li>**不击杀分身无法对墨默造成伤害**；分身标记为墨默**仆从**；</li>
 *   <li>近战／远程 AI **用户后续自己写**，本轮只做**游走** ✓。</li>
 * </ul>
 *
 * <h3>为什么"无前摇"能成立</h3>
 * 分身是在 {@link #onStart()} 里**立刻**生成的；本 Goal 只活几 tick 就结束
 * （{@link #onTick()} 第一 tick 就返回 true）⇒ 没有蓄力段。
 * <p>"无法打断" = 覆写 {@link MomoSpellGoal#interruptible()} 返回 {@code false}，
 * 受击不会把这个技能收掉（⚠ 别的技能仍然可打断 ✓）。
 *
 * <p>一局只放一次：标志在实体上（{@code TNLRlyehUsed}，进 NBT）。
 */
public final class RlyehShadowGoal extends MomoSpellGoal {

    /** 3 个分身 */
    private static final int SHADOW_COUNT = 3;
    /** 生成半径（围着本体 3.5 格） */
    private static final double SPAWN_RADIUS = 3.5D;
    /** 这个 Goal 只活 6 tick（★无前摇 ✓） */
    private static final int TOTAL_TICKS = 6;

    public RlyehShadowGoal(KtuluMomo momo) {
        super(momo, 5, 0, TOTAL_TICKS);
    }

    /** 不需要冷却：一局一次由 {@code tnl$rlyehUsed()} 保证 */
    @Override
    protected int cooldownLeft() {
        return 0;
    }

    @Override
    public boolean canUse() {
        if (!MomoSkills.RLYEH_SHADOW) {
            return false;
        }
        if (this.momo.tnl$rlyehUsed()) {
            return false;                                   // 一局一次
        }
        if (this.momo.getHealth() > this.momo.getMaxHealth() * 0.5F) {
            return false;                                   // ★测试期口径：50% 血自动触发
        }
        return this.ready();
    }

    /** ★无前摇不可打断 ✓ */
    @Override
    protected boolean interruptible() {
        return false;
    }

    @Override
    protected void onStart() {
        this.momo.tnl$markRlyehUsed();
        int spawned = spawnShadows();
        if (this.momo.level() instanceof ServerLevel level) {
            level.playSound(null, this.momo.blockPosition(),
                    SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 2.0F, 0.55F);
            level.sendParticles(ParticleTypes.SQUID_INK,
                    this.momo.getX(), this.momo.getY() + 1.0D, this.momo.getZ(),
                    80, 2.5D, 1.0D, 2.5D, 0.05D);
        }
        TinkersNewlife.LOGGER.warn("TNL1282 R'lyeh's Shadow: spawned {} shadows at hp={}/{}",
                spawned,
                String.format(java.util.Locale.ROOT, "%.0f", this.momo.getHealth()),
                String.format(java.util.Locale.ROOT, "%.0f", this.momo.getMaxHealth()));
    }

    /** 第一 tick 就收尾 ⇒ 没有前摇 ✓ */
    @Override
    protected boolean onTick() {
        return true;
    }

    @Override
    protected void onStop() {
    }

    @Override
    protected MomoPose animPose() {
        return MomoPose.NONE;      // 无前摇 ⇒ 不摆施法姿势
    }

    @Override
    protected float animProgress() {
        return 0.0F;
    }

    /**
     * 生成 3 个分身：前两个 {@link MomoShadow.Role#MELEE}，最后一个
     * {@link MomoShadow.Role#WANDER} 并挂上游走 AI。
     *
     * @return 实际生成数量（>0 才说明技能生效）
     */
    private int spawnShadows() {
        if (!(this.momo.level() instanceof ServerLevel level)) {
            return 0;
        }
        int spawned = 0;
        for (int i = 0; i < SHADOW_COUNT; i++) {
            try {
                MomoShadow shadow = ModEntities.MOMO_SHADOW.get().create(level);
                if (shadow == null) {
                    continue;
                }
                double angle = Math.toRadians(i * 120.0D);
                double x = this.momo.getX() + Math.cos(angle) * SPAWN_RADIUS;
                double z = this.momo.getZ() + Math.sin(angle) * SPAWN_RADIUS;
                shadow.moveTo(x, this.momo.getY(), z, this.momo.getYRot(), 0.0F);
                // ★最后一个是"游走＋远程"的那只
                MomoShadow.Role role = (i == SHADOW_COUNT - 1)
                        ? MomoShadow.Role.WANDER : MomoShadow.Role.MELEE;
                shadow.tnl$bindTo(this.momo, role);       // 归属 / 仆从标记 / 半血
                if (role == MomoShadow.Role.WANDER) {
                    shadow.tnl$addWanderAi();             // 本轮只做游走
                }
                // 近战那两只先跟随本体的目标（AI 用户后续补）
                shadow.setTarget(this.momo.getTarget());
                level.addFreshEntity(shadow);
                spawned++;
            } catch (Throwable t) {
                TinkersNewlife.LOGGER.warn("TNL1282 shadow spawn failed: {}", t.toString());
            }
        }
        return spawned;
    }
}
