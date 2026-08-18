package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;

public class DragonboneTrait extends Modifier {

    public static boolean hasDragonbone(IToolStackView tool) {
        ModifierNBT modifiers = tool.getModifiers();
        for (ModifierEntry entry : modifiers.getModifiers()) {
            if (entry.getModifier() instanceof DragonboneTrait) {
                return true;
            }
        }
        return false;
    }

    public static boolean isConductionActive(IToolStackView tool) {
        return hasDragonbone(tool);
    }
}