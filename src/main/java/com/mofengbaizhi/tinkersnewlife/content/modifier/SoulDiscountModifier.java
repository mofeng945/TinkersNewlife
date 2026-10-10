package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.module.ModuleHookMap;

/** 盔甲特性·灵魂折扣（神灵金盔甲自带，参照原版 Apocalyptium ISoulDiscount）：每级 5% 灵魂能量消耗减免。 */
public class SoulDiscountModifier extends Modifier {

    public static final ModifierId ID = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "soul_discount"));

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
    }

}
