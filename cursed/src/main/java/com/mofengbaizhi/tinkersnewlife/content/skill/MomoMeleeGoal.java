package com.mofengbaizhi.tinkersnewlife.content.skill;

import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

import java.util.EnumSet;

/**
 * TNL1261 墨默专用近战 Goal：**只占 `MOVE`，不占 `LOOK`**。
 *
 * <p>用户 2026-10-16 的推理（正确）：
 * 「如果你 disable 成功了的话，寻路也应该失效啊，但是和弦的时候墨默还在试图向我寻路」
 * —— 原版 {@link MeleeAttackGoal} 占 `MOVE|LOOK`，而技能只是**禁用了 LOOK 旗**：
 * 禁旗只阻止 Goal **起手**，不会停掉**已经在跑**的那个 ⇒ 它继续寻路。
 *
 * <p>现在把近战改成只占 `MOVE`：
 * <ul>
 *   <li>技能施法时禁用 `LOOK` ⇒ 近战 Goal **无法起手**（它需要 LOOK）⇒ 不再寻路；</li>
 *   <li>同时 `LookAtPlayerGoal`/`RandomLookAroundGoal` 也进不来 ⇒ 头不再乱转；</li>
 *   <li>施法结束恢复 LOOK ⇒ 一切照常。</li>
 * </ul>
 */
public final class MomoMeleeGoal extends MeleeAttackGoal {

    public MomoMeleeGoal(KtuluMomo momo) {
        super(momo, 1.0D, true);
        // 只要 MOVE：朝向我们不再由近战负责（技能自己管）
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }
}
