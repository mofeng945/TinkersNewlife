package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 强化·统御者（占用能力槽）：半径 8 格内的刌民（AbstractIllager 子类）有概率
 * 优先锁定你所攻击的非刌民目标。单级、可洗可剥。
 * 具体逻辑在 {@code content/modifier/events/CommanderHandler}（服务端 LivingHurtEvent 驱动）。
 * 本类仅作为可安装的能力槽强化空壳。
 */
public class CommanderModifier extends Modifier {
}
