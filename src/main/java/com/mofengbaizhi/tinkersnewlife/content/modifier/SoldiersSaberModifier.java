package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.content.modifier.base.BaseCombatModifier;

/**
 * 词条·<b>兵士佩刀</b>（§808 · 唐横刀自带 · <b>无等级</b> ✓）——
 * 注册成 {@code StaticModifier}（不升级 ⇒ 提示里不显示等级 ✓ 正合"无等级"口径 ✓）。
 *
 * <p>实际逻辑全在 {@code content/modifier/events/SoldiersSaberHandler} ✓：
 * <ul>
 *   <li>手持时<b>实体交互距离 +1 格</b>（在 {@code TangHengDaoItem#getAttributeModifiers} 里给属性 ✓）；</li>
 *   <li><b>4 格内越近"附加的段数"越多</b>：4格 0 段 / 3格 1 段 / 2格 2 段 / 1格 3 段 / 贴身 4 段 ✓
 *       每段 = <b>工具面板 × 50%</b> ✓ 且<b>各自独立结算一次伤害</b> ✓（不是把总加成塞进同一次 ✗）；</li>
 *   <li>每段挥出一道<b>灰色刀光</b> ✓（{@code SoldierSlashEntity} 弧形面片 ＋ 弧光贴图 ⇒ 仿拔刀剑 ✓
 *       <b>不是</b> {@code ParticleTypes.SWEEP_ATTACK} 横扫粒子 ✗ —— 见 §810）。</li>
 * </ul>
 * 与 {@link DarkMetalBreakerModifier} 同款写法：本类只当"标记/展示"，逻辑在 handler ✓。
 */
public class SoldiersSaberModifier extends BaseCombatModifier {
}
