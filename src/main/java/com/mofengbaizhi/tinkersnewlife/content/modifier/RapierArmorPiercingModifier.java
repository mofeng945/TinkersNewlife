package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.content.modifier.base.BaseCombatModifier;

/**
 * 词条·<b>穿甲</b>（§943 阶段 5 · 西洋剑自带 · <b>无等级</b> ✓）。
 *
 * <p>与 {@link SoldiersSaberModifier} 同款写法：**本类只当"标记/展示"** ✓，逻辑全在别处 ✓：
 * <ul>
 *   <li><b>无视护甲</b> ⇒ {@code content/handler/RapierCombatHandler} ✓（§948：
 *       {@code LivingHurtEvent} 记护甲前伤害 ✓ {@code LivingDamageEvent} 把护甲吃掉的量补回来 ✓）；</li>
 *   <li><b>右键后跳</b> ⇒ {@code content/item/RapierItem#use} ✓（§949）；</li>
 *   <li><b>手持时副手盾牌无法使用</b> ⇒ {@code RapierCombatHandler#onShieldBlock} ✓（§950）。</li>
 * </ul>
 *
 * <p>⚠ 现在上面三处判定的是"**主手是不是 {@code RapierItem}**" ✓ 而不是"有没有这个词条" ✗ ——
 * 对"西洋剑自带"来说两者等价 ✓；若以后想让**别的工具也吃**这三条 ✓
 * 就把判定换成 {@code tool.getModifiers().getLevel(RAPIER_ARMOR_PIERCING)} ✓
 * （{@code SoldiersSaberHandler} 就是这么写的 ✓ 见那儿的注释 ✓）。
 *
 * <p>⚠ 按仓库硬规矩：**不加 tooltip** ✗（§812 ✓）—— 词条信息由匠魂自己的特性显示承担 ✓
 * （`modifier.tinkersnewlife.rapier_armor_piercing` ＋ `.description` ＋ `.flavor` ✓）。
 */
public class RapierArmorPiercingModifier extends BaseCombatModifier {
    // 只作为注册标记 ✓ 逻辑见类注释列出的三处 ✓
}
