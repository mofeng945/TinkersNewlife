package com.mofengbaizhi.tinkersnewlife.content.modifier;

import net.minecraft.network.chat.Component;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.modules.build.ModifierSlotModule;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.SlotType;

/**
 * 无槽位强化·苍白镀层（占用升级槽：安装不占槽，直接给工具 +1 升级槽）。
 * 效果与原版 writable（可塑）一致，用 {@link ModifierSlotModule} 在易变数据里加 1 个升级槽。
 * 单级（配方 level 1），不可洗掉（remove_blacklist）、不可剥离（extract_blacklist）。
 */
public class PalePlatingModifier extends Modifier {

    /** 苍白镀层：单级（+1升级槽固定）→ 显示名不带等级 */
    @Override
    public net.minecraft.network.chat.Component getDisplayName(int level) {
        return this.getDisplayName();
    }

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        hookBuilder.addModule(ModifierSlotModule.slot(SlotType.UPGRADE).amount(1, 0));
    }

}
