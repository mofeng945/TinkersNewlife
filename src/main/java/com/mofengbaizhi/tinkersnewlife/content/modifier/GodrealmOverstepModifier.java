package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.module.ModuleHookMap;

/** 盔甲特性·越过神域（神灵金盔甲自带，参照原版 Apocalyptium IInvulnerableItem）：免疫爆炸/火焰/魔法伤害。 */
public class GodrealmOverstepModifier extends Modifier {

    public static final ModifierId ID = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "godrealm_overstep"));

    /** 效果固定（免疫火/爆/魔），不受等级影响 → 显示名不带等级 */
    @Override
    public net.minecraft.network.chat.Component getDisplayName(int level) {
        return this.getDisplayName();
    }

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
    }

}
