package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 矿工视野（Miner's Vision）—— ★防止透视千里眼的矿石高亮 ✓
 *
 * <pre>
 *   flavor      ：贫瘠的矿脉培养不出老练的矿工
 *   description ：管理员我没有开透！
 *   效果        ：范围内所有 {@code forge:ores} 矿石 **高亮 ✓ 透墙可见** ✓
 *   等级        ：1~5 级，每级范围 +5 格（★满级 25 格 ✓）
 * </pre>
 *
 * <p>★渲染全在**客户端**（★只有自己看得到 ✓），见
 * {@code client/handler/MinersVisionClientHandler} ✓。本类只作注册与等级记录 ✓。
 */
public class MinersVisionTrait extends Modifier {
    // 只作注册标记
}
