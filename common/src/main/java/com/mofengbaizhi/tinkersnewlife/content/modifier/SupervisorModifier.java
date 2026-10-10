package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * <b>监工</b>（§1068，标记类 ✓）—— 用户口径（2026-10-05）：
 * <blockquote>
 * 「<b>写一个新强化：监工，只能被附加在鞭子上。效果是让鞭子的抽击伤害降低为0，但是抽打随从时可以为其附加力量和速度，
 * 最高5级，抽打同心戒指同伴时可有此效果，抽打村民时，将会有5%的概率使其立即补货，只不过补货数量为正常补货时的一半，
 * 每天最多触发3次。</b>」
 * </blockquote>
 *
 * <p>本类只作<b>注册标记</b> ✓（照本仓 {@code CharmTrait} 的路子 ✓）—— 真正的逻辑在
 * {@link com.mofengbaizhi.tinkersnewlife.content.handler.SupervisorHandler} ✓，
 * 由鞭子的命中判定（{@code WhipLashEntity} ✓）在"抽中实体"那一刻调用 ✓。
 *
 * <p><b>「只能被附加在鞭子上」怎么实现</b> ✓：靠配方里的工具限定 ✓ ——
 * {@code data/tinkersnewlife/recipes/modifiers/supervisor.json} 写
 * {@code "tools": {"tag": "tinkersnewlife:modifiable/whip"}} ✓，
 * 而该标签（{@code data/tinkersnewlife/tags/items/modifiable/whip.json} ✓）里<b>只有</b>
 * {@code tinkersnewlife:whip} 一项 ✓ ⇒ 别的工具在工匠站里根本看不到这个强化 ✓
 * （这就是本仓 {@code aristocratic_dining}／{@code blasphemy} 限制"只能装盔甲"的同一套写法 ✓）。
 */
public class SupervisorModifier extends Modifier {
    // 只作为注册标记 ✓（逻辑见 SupervisorHandler ✓）
}
