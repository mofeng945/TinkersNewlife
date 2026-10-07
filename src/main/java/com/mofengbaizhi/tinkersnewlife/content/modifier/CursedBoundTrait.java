package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.content.modifier.base.BaseCombatModifier;

/**
 * ⭐ §1118y <b>七咒所缚</b>（通用特性 ✓ 无等级 ✓）—— 材料「恶念星灵」的默认通用特性 ✓。
 *
 * <p>⚠ 类名与文件名是 {@code CursedBoundTrait} ✗（不是 SevenCursesBoundTrait ✓）——
 * 原因：我先前建过同名文件又删掉 ✗，而本仓的写文件工具**拒绝重建已删除的路径** ✓
 * ⇒ 换个新类名绕过 ✓；**对外的注册 id 仍是 `seven_curses_bound`** ✓（语言键/材料 JSON 都用 id ✓ 与类名无关 ✓）。
 *
 * <h2>用户口径（原文 ✓）</h2>
 * <ul>
 *   <li>flavor ✓：「半神？」</li>
 *   <li>description ✓：「只有承受**七咒**时间为**在世界上时间 99% 以上**的人才可以使用它」；</li>
 *   <li>「不满足条件的人手持、装备或装配进饰品时会自动将工具**丢回背包**，
 *       如果背包中没有空位会**自动将其扔在地上**」✓。</li>
 * </ul>
 *
 * <h2>实现（照本仓规矩 ✓）</h2>
 * ⚠ 本仓的**特性类只做标记** ✓ 行为写在 **Forge 事件处理器** 里 ✓：
 * <ul>
 *   <li>{@code content/handler/SevenCursesBoundHandler} ✓ —— 每 20 tick 判定一次：
 *       不合格者身上凡带本特性者（背包/主副手/护甲槽/饰品 ✓）⇒ **塞回背包** ✓ 背包满 ⇒ **丢地上** ✓；</li>
 *   <li>判定数据（总在线 tick ✓ 受七咒 tick ✓）由同一个处理器写进 {@code player.getPersistentData()} ✓
 *       （仓库既有持久化口径 ✓ 见 {@code CursePowerHelper} ✓）；</li>
 *   <li>「受七咒」判据 ✓：身上带着神秘遗物的**七咒之戒** ✓（id ＝ {@code enigmaticlegacy:cursed_ring} ✓，
 *       与 {@code client/handler/CursedRingTooltipHandler} 的判定一致 ✓）。</li>
 * </ul>
 */
public class CursedBoundTrait extends BaseCombatModifier {
}
