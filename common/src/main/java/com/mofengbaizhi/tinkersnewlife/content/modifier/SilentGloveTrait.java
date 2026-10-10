package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 噤默手套的特性标记类
 * 仅用于标识手套具有此特性，让 ModCreativeTabs 能够识别并生成对应的工具变体
 * 攻击逻辑由 SilentGloveAttackHandler 独立处理
 */
public class SilentGloveTrait extends Modifier {

    /** 噤默手套标记类，攻击逻辑独立（不随等级缩放）→ 显示名不带等级 */
    @Override
    public net.minecraft.network.chat.Component getDisplayName(int level) {
        return this.getDisplayName();
    }

}