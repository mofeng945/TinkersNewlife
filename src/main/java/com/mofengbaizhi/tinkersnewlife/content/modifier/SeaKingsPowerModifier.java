package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.content.modifier.base.BaseCombatModifier;

/**
 * 词条·<b>海王之力</b>（§960 规格 · 无等级 ✓ · 水产养殖2 联动材料「海王金属」自带 ✓）。
 *
 * <p>用户口径（逐条抄 ✓）：
 * <ul>
 *   <li><b>工具上</b>：玩家身处<b>水中或雨中</b>时 —— 无视挖掘速度惩罚 ✓ 攻速 +50% ✓ 伤害 +60% ✓；</li>
 *   <li><b>盔甲上</b>：水下呼吸 ✓ 水中视野更清晰 ✓ 水中或雨中：速度 +20% ✓ 伤害减免 +30% ✓；</li>
 *   <li><b>钓鱼竿上</b>：自动获得 <b>海之眷顾 II</b> ＋ <b>饵钓 I</b> ✓；</li>
 *   <li><b>远程武器上</b>（标枪/弓/弩…）：射出的<b>弹射物在水中无视阻力衰减</b> ✓。</li>
 * </ul>
 *
 * <p>⚠ 当前状态：<b>只落了注册骨架</b> ✗（材料数据层已就位 ✓ 见 §963）——
 * 四组效果尚未实现 ✓，下一步照 {@code docs/开发备忘录.md} §960/§962 的顺序做：
 * ①工具组最易（判据用原版 {@code entity.isInWaterOrRain()} ✓）；
 * ②盔甲组（水下呼吸/视野/速度/减伤 ✓）；③钓鱼竿组要在<b>钓鱼结算</b>处模拟那两个附魔 ✓
 * （匠魂工具不能正常附魔 ✗）；④远程组要给弹射物打标记 ＋ mixin 它的水中衰减 ✓（最重 ✗）。
 *
 * <p>本类照 {@link SoldiersSaberModifier} / {@link RapierArmorPiercingModifier} 的先例：
 * <b>只当注册标记</b> ✓ 逻辑放各自的 handler ✓。
 */
public class SeaKingsPowerModifier extends BaseCombatModifier {
    // 骨架：逻辑待实现（见类注释的四步）
}