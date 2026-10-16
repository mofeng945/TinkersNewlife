package com.mofengbaizhi.tinkersnewlife.content.skill;

import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * TNL1229 定身（用户口径：「专门写一个类或方法叫 stun，调用时清理寻路和索敌，结束时再还回去」）。
 *
 * <p>以前定身是散在每个技能里的三行（清速度 ＋ 停寻路），各自写法还不一样，
 * 于是出现"技能结束了身体还动不了"这种残留。现在统一成一个小对象：
 *
 * <pre>
 * this.stun.begin();        // 起手：清寻路、清索敌（记住原目标）、清零速度
 * this.stun.keep();         // 施法中每 tick：保持不动
 * this.stun.end();          // 收尾：把目标还回去，身体立刻交还 AI
 * </pre>
 *
 * <p>被定身期间 {@link #target()} 返回**被记住的那个目标**，技能要打目标就用它，
 * 不要再用 {@code momo.getTarget()}（那时已经是 null）。
 */
public final class MomoStun {

    private final KtuluMomo momo;
    private LivingEntity savedTarget;
    private boolean active;

    public MomoStun(KtuluMomo momo) {
        this.momo = momo;
    }

    /** 起手：清寻路 ＋ 清索敌（原目标存起来）＋ 清零速度 */
    public void begin() {
        if (this.active) {
            return;
        }
        this.active = true;
        this.savedTarget = this.momo.getTarget();
        this.momo.setTarget(null);                 // 清索敌：被定身时不再挂目标
        this.freeze();
    }

    /** 施法中每 tick 调：保持不动 */
    public void keep() {
        if (this.active) {
            this.freeze();
        }
    }

    /** 收尾：把索敌还回去，身体交还 AI（**不再**清零速度） */
    public void end() {
        if (!this.active) {
            return;
        }
        this.active = false;
        if (this.savedTarget != null && this.savedTarget.isAlive()) {
            this.momo.setTarget(this.savedTarget);
        }
        this.savedTarget = null;
        this.momo.hurtMarked = true;
    }

    /** 被定身时技能该用的目标（就是被记住的那个） */
    public LivingEntity target() {
        return this.savedTarget;
    }

    public boolean active() {
        return this.active;
    }

    private void freeze() {
        this.momo.setDeltaMovement(Vec3.ZERO);
        this.momo.hurtMarked = true;
        if (this.momo.getNavigation() != null) {
            this.momo.getNavigation().stop();      // 清寻路
        }
    }
}
