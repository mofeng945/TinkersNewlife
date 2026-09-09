package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

public class ChaosButterflyModifier extends Modifier {

    /** 混沌蝴蝶（占位，实际逻辑由 FeverHandler/WarScytheItem 处理，不随等级缩放）→ 显示名不带等级 */
    @Override
    public net.minecraft.network.chat.Component getDisplayName(int level) {
        return this.getDisplayName();
    }
}