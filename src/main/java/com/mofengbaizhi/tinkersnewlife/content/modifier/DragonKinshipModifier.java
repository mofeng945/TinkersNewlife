package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.util.ModifierLevelDisplay;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.UUID;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID,
        bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public final class DragonKinshipModifier extends Modifier {
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void ensureDragonToughness(net.minecraftforge.event.entity.EntityAttributeModificationEvent event) {
        for (String id : java.util.List.of("fire_dragon", "ice_dragon", "lightning_dragon")) {
            var type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(new net.minecraft.resources.ResourceLocation("iceandfire", id));
            for (var livingType : event.getTypes()) {
                if (livingType == type && !event.has(livingType, Attributes.ARMOR_TOUGHNESS)) {
                    event.add(livingType, Attributes.ARMOR_TOUGHNESS, 0);
                }
            }
        }
    }
    private static final ModifierId ID = new ModifierId(TinkersNewlife.prefix("dragon_kinship"));
    private static final UUID ARMOR_ID = UUID.fromString("6b030475-fafe-4e38-a799-ff8c7e66d470");
    private static final UUID TOUGHNESS_ID = UUID.fromString("ec675d4b-cd85-466c-99f5-f55c194f8178");

    @Override public Component getDisplayName(int level) {
        return ModifierLevelDisplay.NO_LEVELS.nameForLevel(this, level);
    }

    public static void applyOnRelease(ToolStack staff, LivingEntity player, LivingEntity dragon) {
        boolean active = ToolHelper.getActiveModifierLevel(staff, ID) > 0;
        double hp = player.getMaxHealth();
        double dragonHp = dragon.getMaxHealth();
        replace(dragon.getAttribute(Attributes.ARMOR), ARMOR_ID,
                active ? bonus(player.getAttributeValue(Attributes.ARMOR), hp, dragonHp, .50) : 0);
        replace(dragon.getAttribute(Attributes.ARMOR_TOUGHNESS), TOUGHNESS_ID,
                active ? bonus(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS), hp, dragonHp, .30) : 0);
    }

    static double bonus(double defense, double playerHp, double dragonHp, double cap) {
        return playerHp > 0 ? Math.max(0, Math.min(cap, defense / playerHp)) * dragonHp : 0;
    }

    private static void replace(AttributeInstance instance, UUID id, double amount) {
        if (instance == null) return;
        // The stored dragon may already carry this modifier from an earlier release.
        instance.removeModifier(id);
        if (amount > 0 && Double.isFinite(amount)) instance.addPermanentModifier(
                new AttributeModifier(id, "tinkersnewlife.dragon_kinship", amount, AttributeModifier.Operation.ADDITION));
    }
}
