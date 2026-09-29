package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.module.ModuleHookMap;

/**
 * 远程特性·冈格尼尔（神灵金远程武器自带，模仿原版 Gungnir）：
 * 远程弹射物为"无视重力的冈格尼尔虚影"，伤害为弹射物原伤害的 80%；
 * 命中给目标震撼效果；命中时（直接命中）震撼更强、伤害更高，并在目标脚下点燃狱火。
 * 见 {@code GungnirHandler}。
 */
public class GungnirModifier extends Modifier {

    public static final ModifierId ID = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "gungnir"));

    /** 效果固定（80% 伤害/震撼/狱火），不受等级影响 → 显示名不带等级 */
    @Override
    public net.minecraft.network.chat.Component getDisplayName(int level) {
        return this.getDisplayName();
    }

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
    }

}
