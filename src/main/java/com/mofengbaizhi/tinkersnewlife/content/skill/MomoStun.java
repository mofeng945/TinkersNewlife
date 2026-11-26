package com.mofengbaizhi.tinkersnewlife.content.skill;

import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * TNL1229 定身（用户口径：「专门写一个类或方法叫 stun，调用时清理寻路，结束时再还回去」）。
 *
 * <p>TNL1230 用户修正：「算了不清索敌，只是排斥掉寻路」—— 所以这里**不动目标**，
 * 只做三件事：清零速度、停掉寻路、每个 tick 压住移动控制。目标一直留在实体上，
 * 技能该看目标还看得到，近战 Goal 也不会因为丢目标而乱掉。
 *
 * <pre>
 * this.stun.begin();        // 起手：清寻路 ＋ 清零速度
 * this.stun.keep();         // 施法中每 tick：继续压住（近战 Goal 会想重新寻路）
 * this.stun.end();          // 收尾：放开，身体立刻交还 AI
 * </pre>
 */
public final class MomoStun {

    private final KtuluMomo momo;
    private boolean active;

    public MomoStun(KtuluMomo momo) {
        this.momo = momo;
    }

    /** 起手：清寻路 ＋ 清零速度 ＋ 置"被定身"状态（**不碰目标**） */
    public void begin() {
        if (this.active) {
            return;
        }
        this.active = true;
        // TNL1250 用户："在 stun 方法里加上阻止实体攻击"。
        // 状态放到实体上：守卫（KtuluDeathGuard.onMomoAttack）与受击 Goal 都能读到，
        // 于是"被打断/硬直期间它打不出伤害"不用各写一份。
        this.momo.tnl$setStunned(true);
        this.hold();
    }

    /** 施法中每 tick 调：近战 Goal 每 tick 都会想重新寻路，这里把它压回去 */
    public void keep() {
        if (this.active) {
            this.hold();
        }
    }

    /** 收尾：放开定身，身体与攻击权都交还 AI */
    public void end() {
        if (!this.active) {
            return;
        }
        this.active = false;
        this.momo.tnl$setStunned(false);      // TNL1250 攻击权还回去
        this.momo.hurtMarked = true;
    }

    /** 定身期间技能该用的目标：就是实体自己的目标（本类不动它） */
    public LivingEntity target() {
        return this.momo.getTarget();
    }

    public boolean active() {
        return this.active;
    }

    /** 清寻路 ＋ 清速度 ＋ 清移动输入 ＋ 阻止攻击 */
    private void hold() {
        this.momo.setDeltaMovement(Vec3.ZERO);
        this.momo.hurtMarked = true;
        if (this.momo.getNavigation() != null) {
            this.momo.getNavigation().stop();                 // 清寻路
        }
        // TNL1262 用户："吟唱时还是在试图寻路，微弱移动，但是动一下就停止"。
        // 只清 deltaMovement 是不够的：近战 Goal 每 tick 还在给实体喂移动输入
        // （xxa/zza/speed），travel() 会据此把它推出去一小步，下一 tick 又被我们停住。
        // 所以把移动输入也清零，这才是"站住"。
        this.momo.setXxa(0.0F);
        this.momo.setZza(0.0F);
        this.momo.setSpeed(0.0F);
    }
}
