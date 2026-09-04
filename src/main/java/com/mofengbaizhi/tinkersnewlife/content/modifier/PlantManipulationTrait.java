package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 草木操术（术式特性，占用术式槽）
 * <p>
 * 顺转：按下术式键打开选择界面，选「树根」或「咒种」后进入蓄力，
 * 对准敌人再按一次释放：树根在目标脚下及其周围 2 格长出甜浆果丛持续 3 秒
 * （减速 + 咒术伤害、打碎不掉落且不可破坏、随时间自动还原）；
 * 咒种使目标获得「咒种寄生」（攻击 -40%、咒力总量/输出各 -1 级、亲和 -60）。
 * 反转：吸收自身 5×5×5 范围内植物的生命能量转化为咒力
 * （草方块→砂土 +1；草 / 花破坏 +3；树叶破坏 +8，均不掉落）。
 * 实际逻辑由 {@code TechniqueHandler} + {@code PlantManipulationTechnique} 处理。
 */
public class PlantManipulationTrait extends Modifier {
}
