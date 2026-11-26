package com.mofengbaizhi.tinkersnewlife.content.modifier;

import net.minecraft.network.chat.Component;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.module.ModuleHookMap;

/**
 * 西中之虎特性
 * <p>
 * 加持在拥有黑闪特性的武器上时，黑闪的基础触发概率额外增加
 * （玩家当前攻击力 ÷ 10000）。实际概率加成由 BlackFlashHandler 处理，
 * 本类仅作注册标记与工具提示。
 */
public class WestTigerTrait extends Modifier {

    /** 西中之虎：黑闪基础触发概率额外加成（按攻击力/10000，非等级）→ 显示名不带等级 */
    @Override
    public net.minecraft.network.chat.Component getDisplayName(int level) {
        return this.getDisplayName();
    }

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
    }

}
