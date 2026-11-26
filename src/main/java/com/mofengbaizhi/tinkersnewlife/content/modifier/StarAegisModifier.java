package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierId;

public final class StarAegisModifier extends Modifier {
    private static final ModifierId ID = new ModifierId(TinkersNewlife.prefix("star_aegis"));
    public static float mitigate(LivingEntity wearer, float damage) {
        if (wearer.level().isClientSide || !(damage > 0) || !Float.isFinite(damage)) return damage;
        int level = 0;
        for (var slot : EquipmentSlot.values()) {
            var stack = wearer.getItemBySlot(slot);
            boolean worn = slot.getType() == EquipmentSlot.Type.HAND ? stack.is(TinkerTags.Items.SHIELDS)
                    : stack.is(TinkerTags.Items.WORN_ARMOR) && LivingEntity.getEquipmentSlotForItem(stack) == slot;
            if (worn) level += ToolHelper.getActiveModifierLevel(ToolHelper.getToolStack(stack), ID);
        }
        if (level <= 0) return damage;
        float armor = (float)wearer.getAttributeValue(Attributes.ARMOR);
        float toughness = (float)wearer.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        return damage * (1f - rate(level, armor, toughness, damage));
    }
    public static float rate(int level, float armor, float toughness, float originalDamage) {
        int l = Math.max(0, Math.min(level, 8));
        float a = Math.max(0f, Math.min(armor, 20f));
        float t = Math.max(0f, Math.min(toughness, 12f));
        float d = Math.max(0f, originalDamage);
        float effective = Math.max(a / 5f, a - d / (2f + t / 4f));
        return Math.max(0f, Math.min(.40f * (l / 8f) * (effective / 16f), 1f));
    }
}
