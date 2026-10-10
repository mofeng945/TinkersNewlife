package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.modules.build.ModifierSlotModule;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.SlotType;

public class NyarlathotepDesireTrait extends Modifier {

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        hookBuilder.addModule(ModifierSlotModule.slot(SlotType.ABILITY).eachLevel(1));
    }

}