package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.module.ModuleHookMap;

public class DragonStaffTrait extends Modifier {

    public static final String MODIFIER_ID = "dragon_staff";
    private static final ResourceLocation KEY_MODE = new ResourceLocation(TinkersNewlife.MOD_ID, "dragon_staff_mode");
    private static final ResourceLocation KEY_SLOTS = new ResourceLocation(TinkersNewlife.MOD_ID, "dragon_staff_slots");
    
    private static final int BASE_SLOTS = 3;
    private static final int SLOTS_PER_LEVEL = 1;

    // ⭐ 攻击加成系数（Trait 与 Handler 共用，避免双份硬编码漂移）
    /** 每存储一条龙提供的攻击伤害加成 */
    public static final float ATTACK_BONUS_PER_DRAGON = 20.0f;

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
    }

    public static int getMaxSlots(int level) {
        return BASE_SLOTS + (level - 1) * SLOTS_PER_LEVEL;
    }
}