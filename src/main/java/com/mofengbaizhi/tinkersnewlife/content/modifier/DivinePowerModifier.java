package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.module.ModuleHookMap;

/**
 * 近战特性·神圣之力（神灵金近战头自带，联动铁魔法/启示录）：
 * 提高持有者 180 点法力值；武器自带注入法术 火墙术Lv5 / 天使之翼Lv5 / 治愈之环Lv10（模仿
 * RevelationFix ValetteinItemMixin）；火墙无视无敌帧。见 {@code DivinePowerHandler}。
 */
public class DivinePowerModifier extends Modifier {

    public static final ModifierId ID = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "divine_power"));

    /** 效果固定（+180 法力/固定法术），不受等级影响 → 显示名不带等级 */
    @Override
    public net.minecraft.network.chat.Component getDisplayName(int level) {
        return this.getDisplayName();
    }

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
    }

}
