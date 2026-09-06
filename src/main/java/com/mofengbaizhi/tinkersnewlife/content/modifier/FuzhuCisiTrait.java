package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 领域·伏诛赐死（占用领域槽）
 * <p>
 * 指定视线目标开启领域：笼罩范围内除展开者外所有实体定身不能移动
 * （新阴流技巧无效）；被指定的目标成为"被告"，依其类别进入审判：
 * 亡灵 → 直接判有罪（攻击力归零 60s）；节肢动物 → 白昼无罪/夜晚有罪；
 * 玩家 → 依击杀村民与动物总数裁决，有罪则按序没收咒具→术式→咒力 60s。
 * 有罪者由展开者获得"处刑人之剑"执行处决。实际逻辑由 {@code ExecutionDomain} 处理。
 */
public class FuzhuCisiTrait extends Modifier {
}
