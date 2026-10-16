package com.mofengbaizhi.tinkersnewlife.content.skill;

import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;

/**
 * TNL1173 技能体系基类，照诡厄使徒的做法。
 *
 * <p>使徒是怎么做的（反编译 {@code Apostle} 2388 行）：
 * <ul>
 *   <li>每个技能都是自己的 {@code Goal}，用
 *       {@code goalSelector.addGoal(3, new XxxSpellGoal(this))} 注册；</li>
 *   <li>每个技能有自己的冷却字段（{@code coolDown}、{@code tornadoCoolDown}、
 *       {@code infernoCoolDown}、{@code monolithCoolDown}、{@code damnedCoolDown}），在
 *       {@code customServerAiStep} 里递减；</li>
 *   <li>施法状态放在实体上：同步的法术编号 ＋ 一个 tick 计数器
 *       （{@code SpellCastingCultist.SPELL} ＋ {@code spellTicks}）；</li>
 *   <li>转阶段本身也是一个 Goal（{@code SecondPhaseIndicator}），它只做一件事：
 *       置一个标志，其它所有 Goal 都判它 —— 这就是"不清 AI 也能站定"的做法。</li>
 * </ul>
 *
 * <p>所以这里的技能＝<b>实体上的冷却 ＋ isBusy 闸门 ＋ 由同步法术通道决定的姿势</b>。
 * 不许用 {@code setNoAi}，不许在服务端 tick 事件里清零速度，不许把冷却挂在玩家身上。
 * 详见 {@code docs/墨默技能开发准则.md}。
 */
public abstract class MomoSpellGoal extends net.minecraft.world.entity.ai.goal.Goal {

    protected final KtuluMomo momo;

    /** TNL1229 统一的定身：清寻路（★什么时候定、什么时候放开由各技能自己决定 ✓）*/
    protected final MomoStun stun;

    /** 1 瞬身、2 幻音共鸣、3 混沌和弦 */
    protected final int spellId;
    /** 本技能自己的冷却（tick），从**施法结束**那一刻起算 */
    private final int cooldownTicks;
    /** 从起手前摇到后摇结束的总 tick 数 */
    private final int totalTicks;

    protected MomoSpellGoal(KtuluMomo momo, int spellId, int cooldownTicks, int totalTicks) {
        this.momo = momo;
        this.stun = new MomoStun(momo);
        this.spellId = spellId;
        this.cooldownTicks = cooldownTicks;
        this.totalTicks = totalTicks;
        // TNL1198 关键：**绝不占用 MOVE/LOOK**。近战 Goal（优先级 1）只要 Boss 有目标就一直在跑，
        // 而它同时占着这两个 flag，技能若也占就**永远抢不到**，表现就是"技能根本不放"。
        // 诡厄的使徒也不占 flag：
        // 它靠施法状态定身。我们同样处理：技能每 tick 清零速度、
        // 停下寻路，攻击由守卫按 isBusy() 取消。
        // （这里故意不调 setFlags）
    }

    /** TNL1216 本技能摆哪种姿势（技能自己声明，客户端只照它画）*/
    public MomoPose poseKind() {
        return MomoPose.of(this.spellId);
    }

    /** 施法进度 0..1（由同步的剩余 tick 换算）*/
    protected float progress() {
        int left = this.momo.tnl$castTicks();
        if (this.totalTicks <= 0) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, 1.0F - (float) left / (float) this.totalTicks));
    }

    /** 现在能不能起手 */
    protected boolean ready() {
        // TNL1196 GoalSelector 在**客户端也会跑**（Mob.aiStep），而每个技能都会把 level
        // 强转成 ServerLevel 并在上面发粒子。没有这道闸门就会在客户端每 tick 抛
        // ClassCastException。原版自己的施法 Goal 也是这么判的。
        if (this.momo.level().isClientSide) {
            return false;
        }
        return !this.momo.tnl$isBusy()
                && this.cooldownLeft() <= 0
                && this.momo.isAlive()
                && this.momo.tnl$mayCastAgainSoon(this.spellId);   // TNL1178 rotation
    }

    /** ticks of cooldown still to go; each skill keeps its own field on the entity */
    protected abstract int cooldownLeft();

    /** 起手时调一次 */
    protected abstract void onStart();

    /** 施法中每 tick 调；返回 true 表示结束 */
    protected abstract boolean onTick();

    /** 无论怎么结束都调一次 */
    protected abstract void onStop();

    /** 触发条件由子类自己定 */
    @Override
    public abstract boolean canUse();

    @Override
    public void start() {
        this.runningTicks = 0;
        this.tnl$done = false;
        this.momo.tnl$setSpell(this.spellId, this.totalTicks);
        // TNL1244 技能自己上报动画（用户："把动画动作的调用打包在技能类里面"）。
        // 每个技能可以重写 animProgress() 决定自己的节奏。
        this.momo.tnl$setAnim(this.animPose().ordinal(), this.animProgress());
        this.onStart();
    }

    /**
     * TNL1244 本技能此刻该摆的姿势。默认就是自己的 {@link #poseKind()}；
     * 子类想"中途收姿势"（例如和弦在硬控结束后就要放下双手）就重写它。
     */
    protected com.mofengbaizhi.tinkersnewlife.content.skill.MomoPose animPose() {
        return this.poseKind();
    }

    /**
     * TNL1244 本技能此刻的动画进度 0~1。默认用"剩余 tick"换算，
     * 子类想自己控制（比如和弦只在蓄力段推）就重写它。
     */
    protected float animProgress() {
        return this.progress();
    }

    @Override
    public boolean canContinueToUse() {
        // TNL1236 **关键修复**：结束必须走这里，不能在 tick() 里自己调 stop()。
        // 日志实测：`TNL1203 spell 1 overran (259 ticks) … (277 ticks)` 每 tick 刷一次，
        // 说明 tick() 里直接 stop() 之后 GoalSelector 仍然把这个 Goal 留在"运行中"，
        // 于是它每 tick 都停一次 ⇒ spell 标志一直不清 ⇒ isBusy() 永真 ⇒
        // 所有技能都再也触发不了，onStop() 也每 tick 跑一次（和弦"连发"就是这么来的）。
        return !this.tnl$done && this.momo.isAlive();
    }

    @Override
    public void tick() {
        // TNL1203 用户："你还在用清零速度定身，而且没取消"。
        // 不管相位逻辑怎么写，一次施法**最多**比自身长度多活两秒：
        // 这个看门狗会强制结束它，顺便结束它做的任何定身。
        this.runningTicks++;
        if (this.runningTicks > this.totalTicks + 40) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                    "TNL1203 spell {} overran ({} ticks), ending", this.spellId, this.runningTicks);
            this.tnl$done = true;                 // TNL1236 交给 canContinueToUse() 收尾
            return;
        }
        if (this.onTick()) {
            this.tnl$done = true;                 // TNL1236 同上：不在这里调 stop()
        }
        // TNL1244 每 tick 把动画进度上报给客户端（技能驱动，渲染器只搬运）
        this.momo.tnl$setAnim(this.animPose().ordinal(), this.animProgress());
    }

    /** TNL1236 本技能是否已经该结束（由 GoalSelector 读它并调 stop()） */
    private boolean tnl$done;

    /** TNL1203 本次施法已经跑了多久，给看门狗用 */
    private int runningTicks;

    @Override
    public void stop() {
        boolean tnl$ours = this.momo.tnl$spell() == this.spellId;   // TNL1233 先判断再清
        if (tnl$ours) {
            this.momo.tnl$setSpell(0, 0);
        }
        if (tnl$ours) {
            // TNL1233 只有**真的放过本技能**才刷新轮转时间戳。以前无条件刷，
            // 一旦 stop() 被反复调用（被别的 Goal 挤掉/看门狗），时间戳就一直在刷新
            // ⇒ mayCastAgainSoon 永远为 false ⇒ "放一次之后再不放"。
            this.momo.tnl$markSpellCast(this.spellId);
        }   // TNL1178 remember it for the rotation
        // TNL1244 收尾清掉动画通道，免得姿势留在最后一帧
        this.momo.tnl$clearAnim();
        this.onStop();
    }

    /** 施法结束时写入的冷却 */
    protected int cooldownTicks() {
        return this.cooldownTicks;
    }
}
