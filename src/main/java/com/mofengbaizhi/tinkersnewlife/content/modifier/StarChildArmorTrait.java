package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.module.ModuleHookMap;

public class StarChildArmorTrait extends Modifier {

    public static final ModifierId ID = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "star_child_armor"));
    private static final ResourceLocation TAG_KILLS = new ResourceLocation(TinkersNewlife.MOD_ID, "star_child_kills");

    // ⭐ 生命加成系数（Trait 与 Handler 共用，避免双份硬编码漂移）
    /** 每级盔甲的最大生命加成上限 */
    public static final float MAX_HP_PER_LEVEL = 50.0f;
    /** 每击杀提供的生命加成 */
    public static final float HP_PER_KILL = 0.5f;

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
    }

}