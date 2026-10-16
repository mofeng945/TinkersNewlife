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

    /** 起手：清寻路 ＋ 清零速度（**不碰目标**） */
    public void begin() {
        if (this.active) {
            return;
        }
        this.active = true;
        this.hold();
    }

    /** 施法中每 tick 调：近战 Goal 每 tick 都会想重新寻路，这里把它压回去 */
    public void keep() {
        if (this.active) {
            this.hold();
        }
    }

    /** 收尾：放开定身，身体交还 AI */
    public void end() {
        if (!this.active) {
            return;
        }
        this.active = false;
        this.momo.hurtMarked = true;
    }

    /** 定身期间技能该用的目标：就是实体自己的目标（本类不动它） */
    public LivingEntity target() {
        return this.momo.getTarget();
    }

    public boolean active() {
        return this.active;
    }

    /** 清寻路 ＋ 清速度 ＋ 压住移动控制 */
    private void hold() {
        this.momo.setDeltaMovement(Vec3.ZERO);
        this.momo.hurtMarked = true;
        if (this.momo.getNavigation() != null) {
            this.momo.getNavigation().stop();                 // 清寻路
        }
        // TNL1231 不要用 setWantedPosition(自己, 0)：那是"一次设定、长期生效"的移动指令，
        // 技能结束后还留在移动控制里 ⇒ 正是"瞬身完还不动"的原因之一。停寻路就够了。
    }
}
