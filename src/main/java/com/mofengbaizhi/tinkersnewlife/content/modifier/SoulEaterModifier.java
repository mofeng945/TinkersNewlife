package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 强化·噬魂（占用升级槽，1 级占 1 槽，2/3 级无槽，最高 3 级）。
 *
 * <p><b>§1070 起按诡厄本体「噬魂」附魔的口径</b>（用户口径：「我要的是模仿诡厄本体噬魂附魔的效果」✓）：
 * <b>击杀时灵魂获取 ×(等级＋1)</b> ✓（噬魂 I ⇒ ×2 ✓、II ⇒ ×3 ✓、III ⇒ ×4 ✓），
 * 并且**只对击杀生效** ✓（近战 ✓ 或自己射出的弹射物 ✓；宠物随从击杀也算主人的 ✓）。
 * <p>本体依据（cfr 反编译 {@code goety-2.5.57.3} 实读 ✓，非猜测 ✗）：
 * {@code SEHelper.SoulMultiply} 用 {@code clamp(附魔等级+1, 1, 10)} ✓，
 * {@code SEHelper.rawHandleKill} 用 {@code floor(基数 × 该倍率) × 配置倍率} ✓。
 * <p>实现逻辑在 {@code content/modifier/events/SoulEaterHandler} ✓（服务端每 tick 观察灵魂增量拿到"基数"，
 * 只在该玩家最近有击杀时按 {@code 增量 × 等级} 补发 ✓ ⇒ 合计＝基数 ×(等级＋1) ✓ 与本体等价 ✓）。
 * <p>⚠ 与「灵魂饥饿」（{@code goety:soul_hunger} ✓ 本体那条削弱 ✗）毫无关系 ✓ —— 本模组从不施加它 ✓。
 */
public class SoulEaterModifier extends Modifier {
}
