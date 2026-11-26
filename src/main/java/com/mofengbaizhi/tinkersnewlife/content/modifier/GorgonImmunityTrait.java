package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 蛇发女妖凝视免疫特性（标记类）
 * 实际逻辑由 GorgonImmunityHandler 处理
 */
public class GorgonImmunityTrait extends Modifier {

    /** 蛇发女妖凝视免疫（标记类，效果固定），不受等级影响 → 显示名不带等级 */
    @Override
    public net.minecraft.network.chat.Component getDisplayName(int level) {
        return this.getDisplayName();
    }
}