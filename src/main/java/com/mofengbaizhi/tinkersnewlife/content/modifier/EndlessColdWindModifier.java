package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.module.ModuleHookMap;

/**
 * 铁魔法联动特性·<b>无止寒风</b>（材料「无相冰」自带，<b>无等级</b>）：
 *
 * <p>手持该工具施法时，<b>冰霜学派法术强度 +50%</b>（铁魔法 {@code ICE_SPELL_POWER} 属性 +0.5）。
 * 属性由 {@code content.modifier.events.FormlessIceHandler} 每 10 tick 维持（与神圣之力的做法一致）。
 */
public class EndlessColdWindModifier extends Modifier {

    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "endless_cold_wind"));

    /** 冰霜法强加成：+50%（铁魔法该属性以 1.0 为 100%） */
    public static final double ICE_POWER_BONUS = 0.25;

    /** 效果固定 → 显示名不带等级 */
    @Override
    public Component getDisplayName(int level) {
        return this.getDisplayName();
    }

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
    }

    /** 该物品是否带无止寒风 */
    public static boolean has(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var tool = ToolHelper.getToolStack(stack);
        return ToolHelper.getActiveModifierLevel(tool, ID) > 0;
    }

    /** 手持（主手或副手）是否带着无止寒风 */
    public static boolean heldBy(LivingEntity entity) {
        if (entity == null) return false;
        return has(entity.getMainHandItem()) || has(entity.getOffhandItem());
    }
}
