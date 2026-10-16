package com.mofengbaizhi.tinkersnewlife.content.skill;

import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * TNL1251 受击硬直（用户口径：「受击这个可以做一个 goal 类，优先级比技能和其他行为都高」＋
 * 「受击事件记得写新类，硬直的 stun 只是他用的方法，并不是所有 stun 都要播放受击动画」）。
 *
 * <p>所以职责划清：
 * <ul>
 *   <li>**本类**只负责"被打中之后"的事：站住、播受击动画、1 秒硬直。</li>
 *   <li>{@link MomoStun} 只是它使用的**手段**（清寻路 ＋ 清零速度 ＋ 阻止攻击），
 *       技能施法时也会用同一个手段，但**不会**播受击动画。</li>
 *   <li>触发来自伤害路径：{@code KtuluDeathGuard.onHurt} 算出夹后伤害后调用
 *       {@link KtuluMomo#tnl$onHit}，本 Goal 通过 {@code canUse()} 接手。</li>
 * </ul>
 *
 * <p>优先级注册在 **0**（最高）并占 {@code MOVE|LOOK}：这样它会挤掉近战/游走这类占旗行为，
 * 真正实现"硬直"。技能 Goal 不占旗，由实体上的打断标志负责收尾。
 */
public final class MomoHurtGoal extends Goal {

    private final KtuluMomo momo;
    /** 本次硬直总长（tick），用来算动画进度 */
    private int total;
    private int left;

    public MomoHurtGoal(KtuluMomo momo) {
        this.momo = momo;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.momo.tnl$hurtStaggerTicks() > 0;
    }

    @Override
    public boolean canContinueToUse() {
        return this.left > 0 && this.momo.isAlive();
    }

    @Override
    public void start() {
        this.total = Math.max(1, this.momo.takeHurtStagger());
        this.left = this.total;
        this.momo.tnl$setStunned(true);        // 用 stun 的手段：清寻路 + 不许攻击
    }

    @Override
    public void tick() {
        this.left--;
        this.momo.tnl$stunFor(2);              // 每 tick 续一点点，保证停住
        // 受击动画只有这里会播：进度 0~1
        float p = 1.0F - (float) this.left / (float) this.total;
        this.momo.tnl$setAnim(MomoPose.HURT.ordinal(), p);
    }

    @Override
    public void stop() {
        this.momo.tnl$setStunned(false);
        this.momo.tnl$clearAnim();
        this.left = 0;
    }
}
