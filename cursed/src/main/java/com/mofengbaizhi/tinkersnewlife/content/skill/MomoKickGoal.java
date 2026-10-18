package com.mofengbaizhi.tinkersnewlife.content.skill;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * TNL1287 ★★飞踢（Comet Kick），法术编号 6 ✓
 *
 * <p>用户口径：
 * <ul>
 *   <li>墨默以 **2 倍速度**冲向目标，随后**斜向踢击**；</li>
 *   <li>目标受到 **20% 墨默攻击力的物理伤害**，但会被**严重击飞**；</li>
 *   <li>**撞击到障碍物**时，再按**撞击时的速度**计算**钝伤**；</li>
 *   <li>踢击要有**动作**（{@link MomoPose#KICK}）。</li>
 * </ul>
 *
 * <h3>节奏</h3>
 * <ol>
 *   <li>⭐**冲刺段**：每 tick 用 {@code navigation().moveTo(target, 2.0)} 追（2.0 = 正常速度的 2 倍 ✓），
 *       距离 ≤ {@link #KICK_RANGE} 或冲刺超时就转踢击 ✓；</li>
 *   <li>⭐**踢击**：给目标一个"前方 + 向上"的速度 ⇒ 斜向击飞；伤害 = 攻击力 × 20%
 *       （走 {@code mobAttack} ⇒ 物理伤害，能正常吃护甲/抗性）；</li>
 *   <li>⭐**钝伤窗口**：踢飞后 1.5 秒内跟踪目标，检测到
 *       {@code horizontalCollision}（撞墙/撞方块）且**撞前那一刻水平速度**足够大
 *       ⇒ 追加钝伤 = 攻击力 × 20% × (撞击速度 / 0.4)，上限攻击力 × 1.2 ✓</li>
 * </ol>
 *
 * <p>姿势：{@link MomoPose#KICK}，模型里用 {@code sin(tπ)} 让抬腿→踢出→收回自然起落。
 */
public final class MomoKickGoal extends MomoSpellGoal {

    /** 冲刺最多 1.5 秒（追不上就算了） */
    private static final int DASH_TIMEOUT = 30;
    /** 整个技能 3.5 秒（够冲刺 ＋ 踢击 ＋ 钝伤窗口） */
    private static final int TOTAL_TICKS = 70;
    /** 2 倍速度 ⇒ 冲刺时每 tick 直接给的水平速度（格/tick） */
    private static final float DASH_SPEED = 2.0F;
    /** 冲刺速度（格/tick）：正常走路约 0.2，这里约 2 倍多，看着是"冲" */
    private static final float CHARGE_SPEED = 0.55F;
    /** 进入踢击的距离 */
    private static final float KICK_RANGE = 3.2F;
    /** 踢击伤害系数（用户口径：20% 攻击力） */
    private static final float KICK_FACTOR = 0.20F;
    /** 击飞：水平 / 垂直 */
    private static final float KNOCK_H = 1.30F;
    private static final float KNOCK_UP = 0.95F;
    /** 钝伤窗口 */
    private static final int BLUNT_WINDOW = 30;
    /** 判定"撞上了"的最小撞击前速度（格/tick；正常走路约 0.4） */
    private static final float BLUNT_MIN_SPEED = 0.15F;

    private int dashTicks = 0;
    private boolean kicked = false;
    private LivingEntity victim = null;
    /** 目标上一 tick 的水平速度（撞墙那一瞬间它已经被清零了，只能看"撞前"的值） */
    private float prevSpeed = 0.0F;
    private int bluntTicks = 0;
    private boolean bluntDone = false;
    /** 踢出之后过了几 tick（TNL1292 用来按时收尾） */
    private int kickedTicks = 0;
    /** TNL1295 命中姿势还要播几 tick（从**踢中那一刻**开始算） */
    private int strikeTicks = 0;

    public MomoKickGoal(KtuluMomo momo) {
        super(momo, 6, 200, TOTAL_TICKS);   // 冷却 10 秒（用户没要求，先给个短冷却防连踢）
    }

    @Override
    protected int cooldownLeft() {
        return (int) Math.max(0L, this.momo.tnl$kickCoolUntil() - this.momo.level().getGameTime());
    }

    @Override
    public boolean canUse() {
        if (!MomoSkills.COMET_KICK) {
            return false;
        }
        LivingEntity target = this.momo.getTarget();
        if (target == null || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        if (this.momo.distanceTo(target) > 24.0F) {
            return false;                       // 太远就别踢了（先走过去）
        }
        return this.ready();
    }

    @Override
    protected void onStart() {
        this.dashTicks = 0;
        this.kicked = false;
        this.kickedTicks = 0;
        this.strikeTicks = 0;
        this.bluntTicks = 0;
        this.bluntDone = false;
        this.prevSpeed = 0.0F;
        this.victim = this.momo.getTarget();
        if (this.momo.level() instanceof ServerLevel level) {
            level.playSound(null, this.momo.blockPosition(),
                    SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.6F, 1.5F);
        }
        TinkersNewlife.LOGGER.warn("TNL1287 Comet Kick starts vs {}",
                this.victim == null ? "nothing" : this.victim.getName().getString());
    }

    @Override
    protected boolean onTick() {
        LivingEntity target = this.momo.getTarget();
        if (target != null && target.isAlive()) {
            this.victim = target;
        }
        if (!this.kicked) {
            return this.tickDash();
        }
        this.tickBlunt();
        this.kickedTicks++;
        if (this.strikeTicks > 0) {
            this.strikeTicks--;
        }
        // TNL1292 用户："结束时会停在这个动作上"。
        //   根因 ✗：★`onTick()` 踢完一直返回 false ✗ ⇒ ★只有看门狗（totalTicks+40）才收尾 ✓
        //   ⇒ ★姿势要挂 5 秒多 ✗ 而且进度回落到 0.30 正好是"抬膝"那一段 ✓
        //   ★现在 ✗：★钝伤窗口一结束（或窗口用完 / 踢完 1.5 秒）就 `true` ⇒ ★立刻收尾 ✓
        if (this.kickedTicks > BLUNT_WINDOW || this.bluntTicks <= 0) {
            return true;
        }
        return false;
    }

    /** 冲刺段：2 倍速度追上去，贴身就踢 */
    private boolean tickDash() {
        LivingEntity target = this.victim;
        if (target == null || !target.isAlive()) {
            return true;
        }
        this.dashTicks++;
        if (this.momo.distanceTo(target) <= KICK_RANGE || this.dashTicks > DASH_TIMEOUT) {
            this.doKick(target);
            return false;
        }
        this.momo.getLookControl().setLookAt(target, 40.0F, 40.0F);
        // TNL1288 直线冲刺：直接给速度（约 2 倍移速 ≈ 0.55 格/tick），不走寻路 ⇒ 不会绕障碍
        double dx = target.getX() - this.momo.getX();
        double dz = target.getZ() - this.momo.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4D) {
            dx = 0.0D;
            dz = 1.0D;
            len = 1.0D;
        }
        this.momo.setDeltaMovement(dx / len * CHARGE_SPEED,
                this.momo.getDeltaMovement().y, dz / len * CHARGE_SPEED);
        this.momo.hurtMarked = true;
        return false;
    }

    /** 踢击：斜向击飞 ＋ 20% 攻击力物理伤害 */
    private void doKick(LivingEntity target) {
        this.kicked = true;
        this.strikeTicks = 16;              // 0.8 秒命中姿势
        this.bluntTicks = BLUNT_WINDOW;
        this.prevSpeed = 0.0F;
        try {
            this.momo.getNavigation().stop();
            this.momo.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.position());
        } catch (Throwable ignored) {
        }
        float atk = (float) this.momo.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double dx = target.getX() - this.momo.getX();
        double dz = target.getZ() - this.momo.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4D) {
            dx = 0.0D;
            dz = 1.0D;
            len = 1.0D;
        }
        if (this.momo.distanceTo(target) <= KICK_RANGE + 1.5F) {
            // 斜向击飞：水平朝外 ＋ 明显向上
            Vec3 push = new Vec3(dx / len * KNOCK_H, KNOCK_UP, dz / len * KNOCK_H);
            target.setDeltaMovement(push);
            target.hurtMarked = true;                   // 让客户端接受服务端写的速度
            // 20% 攻击力的**物理**伤害（mobAttack ⇒ 吃护甲/抗性）
            target.hurt(this.momo.damageSources().mobAttack(this.momo), atk * KICK_FACTOR);
        }
        if (this.momo.level() instanceof ServerLevel level) {
            level.playSound(null, target.blockPosition(),
                    SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 2.0F, 0.6F);
            level.sendParticles(ParticleTypes.CRIT,
                    target.getX(), target.getY() + 0.8D, target.getZ(),
                    24, 0.6D, 0.6D, 0.6D, 0.25D);
        }
        TinkersNewlife.LOGGER.warn("TNL1287 Comet Kick hit {} (dmg={})", target.getName().getString(),
                String.format(java.util.Locale.ROOT, "%.1f", atk * KICK_FACTOR));
    }

    /** 钝伤窗口：撞到方块/墙时，按**撞前速度**追加钝伤 */
    private void tickBlunt() {
        if (this.bluntDone || this.bluntTicks <= 0) {
            return;
        }
        this.bluntTicks--;
        LivingEntity target = this.victim;
        if (target == null || !target.isAlive()) {
            return;
        }
        double speed = target.getDeltaMovement().horizontalDistance();
        boolean hitWall = target.horizontalCollision && this.prevSpeed > BLUNT_MIN_SPEED;
        if (hitWall) {
            this.applyBlunt(target, this.prevSpeed);
            this.bluntDone = true;
        }
        this.prevSpeed = (float) speed;
    }

    private void applyBlunt(LivingEntity target, float impactSpeed) {
        float atk = (float) this.momo.getAttributeValue(Attributes.ATTACK_DAMAGE);
        // 攻击力 × 20% × (撞击速度 / 0.4)；上限攻击力 × 1.2
        float blunt = atk * 0.20F * (impactSpeed / 0.4F);
        blunt = Math.max(atk * 0.20F, Math.min(atk * 1.20F, blunt));
        target.hurt(this.momo.damageSources().mobAttack(this.momo), blunt);
        if (this.momo.level() instanceof ServerLevel level) {
            level.playSound(null, target.blockPosition(),
                    SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.4F, 1.1F);
            level.sendParticles(ParticleTypes.CRIT,
                    target.getX(), target.getY() + 1.0D, target.getZ(),
                    16, 0.4D, 0.4D, 0.4D, 0.1D);
        }
        TinkersNewlife.LOGGER.warn("TNL1287 blunt impact on {} speed={} dmg={}",
                target.getName().getString(),
                String.format(java.util.Locale.ROOT, "%.2f", impactSpeed),
                String.format(java.util.Locale.ROOT, "%.1f", blunt));
    }

    @Override
    protected void onStop() {
        this.momo.getNavigation().stop();
        this.momo.tnl$setKickCoolUntil(this.momo.level().getGameTime() + 200L);
    }

    /**
     * TNL1295 用户问「kick 在第几秒踢出」—— 关键点是：**命中不固定**。
     * 冲刺段最长 30 tick（1.5 秒），一旦进到 3.2 格就立刻命中。
     * 所以姿势拆成两段，**命中段从踢中那一刻才开始播**，这样动画和判定一定对齐：
     * <ul>
     *   <li>冲刺中（没踢中）⇒ {@link MomoPose#KICK}（动画 `kick_dash`，循环）</li>
     *   <li>踢中后 16 tick（0.8 秒）⇒ {@link MomoPose#KICK_STRIKE}（动画 `kick_strike`）</li>
     *   <li>之后 ⇒ NONE（钝伤窗口还在后台跑，但不再摆姿势）</li>
     * </ul>
     */
    @Override
    protected MomoPose animPose() {
        if (!this.kicked) {
            return MomoPose.KICK;
        }
        return this.strikeTicks > 0 ? MomoPose.KICK_STRIKE : MomoPose.NONE;
    }

    /**
     * TNL1288 三段动画（照用户的三格参考图）：
     * <ul>
     *   <li>冲刺段：0.05 → 0.35，模型里对应"抬膝"（图123）；</li>
     *   <li>踢出瞬间：0.35 → 0.55，对应"蹬出"（图124）；</li>
     *   <li>钝伤窗口里再慢慢收回 0.55 → 0.30。</li>
     * </ul>
     */
    @Override
    protected float animProgress() {
        if (!this.kicked) {
            return 0.05F + 0.30F * Math.min(1.0F, this.dashTicks / (float) DASH_TIMEOUT);
        }
        float k = 1.0F - Math.max(0.0F, Math.min(1.0F, this.bluntTicks / (float) BLUNT_WINDOW));
        float out = Math.min(1.0F, k * 10.0F);                                  // 蹬出很快
        float back = Math.max(0.0F, Math.min(1.0F, (k - 0.55F) / 0.45F));       // 后半段收回
        return 0.35F + 0.20F * out - 0.25F * back;
    }
}
