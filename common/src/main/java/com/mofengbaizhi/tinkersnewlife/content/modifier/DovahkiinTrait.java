package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.content.modifier.base.BaseCombatModifier;

/**
 * 都瓦克因（Dovahkiin）—— <b>无等级</b>标记特性。
 *
 * <pre>
 *   flavor      ：宿敌便是那巨龙！
 *   description ：你的龙息攻击将对其他属性的龙类造成真实伤害
 * </pre>
 *
 * <p>★实际逻辑在 {@code DragonsteelHandler}（★龙息伤害那条路径 ✓）：
 * <ul>
 *   <li>持有者的任何**龙息类攻击** 对【异属性】龙类 ⇒ ★**全额真实伤害**（★逆向改血 ✓）；</li>
 *   <li>对【同属性】龙类 ⇒ ★**半数**的真实伤害 ✓（★同族抗性 ✓）；</li>
 *   <li>非龙类 ⇒ ★走原有的普通伤害（★不变 ✓）。</li>
 * </ul>
 *
 * <p>★套在三系**龙钢武器**上 ✓：见
 * {@code data/tinkersnewlife/tinkering/materials/traits/dragonsteel_*.json} 的 {@code default} 数组 ✓。
 */
public class DovahkiinTrait extends BaseCombatModifier {
    // 只作为注册标记
}
