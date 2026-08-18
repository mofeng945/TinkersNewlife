package com.mofengbaizhi.tinkersnewlife.content.modifier.util;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

public class ArmorModifierHelper {

    /**
     * 检查实体穿戴的盔甲上是否有指定修饰符
     * @param entity 实体
     * @param modifierId 修饰符ID（可带命名空间，如 "tinkersnewlife:dragonsteel_fire_armor" 或简写 "dragonsteel_fire_armor"）
     */
    public static boolean hasModifierOnArmor(LivingEntity entity, String modifierId) {
        String fullId = modifierId.contains(":") ? modifierId : TinkersNewlife.MOD_ID + ":" + modifierId;
        ModifierId id = new ModifierId(new ResourceLocation(fullId));

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!slot.isArmor()) continue;
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            ToolStack tool = ToolStack.from(stack);
            if (tool == null) continue;
            if (tool.getModifierLevel(id) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取实体穿戴的盔甲上指定修饰符的总等级
     * @param entity 实体
     * @param modifierId 修饰符ID
     * @return 总等级
     */
    public static int getTotalModifierLevelOnArmor(LivingEntity entity, String modifierId) {
        String fullId = modifierId.contains(":") ? modifierId : TinkersNewlife.MOD_ID + ":" + modifierId;
        ModifierId id = new ModifierId(new ResourceLocation(fullId));

        int total = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!slot.isArmor()) continue;
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            ToolStack tool = ToolStack.from(stack);
            if (tool == null) continue;
            total += tool.getModifierLevel(id);
        }
        return total;
    }
}