package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.util.ModifierLevelDisplay;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.Map;
import java.util.UUID;

public final class DragonPitModifier extends Modifier {
    private static final ModifierId ID = new ModifierId(TinkersNewlife.prefix("dragon_pit"));
    private static final String WINNER = "tinkersnewlife.dragon_pit_winner";
    private static final UUID HEALTH_ID = UUID.fromString("a8d25617-f9a6-48b5-a7d2-0803db202351");
    private static final Map<Attribute, UUID> MULTIPLIERS = Map.of(
            Attributes.MAX_HEALTH, HEALTH_ID,
            Attributes.ATTACK_DAMAGE, UUID.fromString("041dd729-f860-450d-a2be-709713a70a7f"),
            Attributes.ARMOR, UUID.fromString("3436390a-bfab-40d9-ae3e-ad8a2501402a"),
            Attributes.ARMOR_TOUGHNESS, UUID.fromString("0a84872d-d427-4850-bfe7-0ebeb4f4ec47"));

    @Override public Component getDisplayName(int level) {
        return ModifierLevelDisplay.NO_LEVELS.nameForLevel(this, level);
    }

    public static boolean isActive(ToolStack tool) {
        return ToolHelper.getActiveModifierLevel(tool, ID) > 0;
    }

    /** Apply after the other release bonuses. A past win is permanent, even on a different staff. */
    public static void applyOnRelease(LivingEntity dragon, boolean winner) {
        if (winner) dragon.getPersistentData().putBoolean(WINNER, true);
        if (!dragon.getPersistentData().getBoolean(WINNER)) return;
        MULTIPLIERS.forEach((attribute, id) -> {
            AttributeInstance instance = dragon.getAttribute(attribute);
            if (instance != null && instance.getModifier(id) == null) {
                instance.addPermanentModifier(new AttributeModifier(id, WINNER, 2d,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        });
        // Increasing max health never fills the new hearts.
    }

    public static boolean isWinner(LivingEntity dragon) {
        return dragon.getPersistentData().getBoolean(WINNER);
    }

    /** Remove only the release bonus. The earned growth is stored independently. */
    public static void stripStoredAttribute(Attribute attribute, AttributeInstance instance) {
        UUID id = MULTIPLIERS.get(attribute);
        if (id != null) instance.removeModifier(id);
    }

    public static void stripReleaseBonus(LivingEntity dragon) {
        MULTIPLIERS.forEach((attribute, id) -> {
            var instance = dragon.getAttribute(attribute);
            if (instance != null) instance.removeModifier(id);
        });
        if (dragon.getHealth() > dragon.getMaxHealth()) dragon.setHealth(dragon.getMaxHealth());
    }

    public static void awardWin(LivingEntity dragon, int participants) {
        stripReleaseBonus(dragon);
        dragon.getPersistentData().putBoolean(WINNER, true);
        MULTIPLIERS.forEach((attribute, releaseId) -> {
            var instance = dragon.getAttribute(attribute);
            if (instance == null) return;
            UUID growthId = UUID.nameUUIDFromBytes((WINNER + ":growth:" + releaseId)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var previous = instance.getModifier(growthId);
            double multiplier = (previous == null ? 1 : 1 + previous.getAmount()) * (1 + participants * .025);
            instance.removeModifier(growthId);
            instance.addPermanentModifier(new AttributeModifier(growthId, WINNER + ".growth",
                    multiplier - 1, AttributeModifier.Operation.MULTIPLY_TOTAL));
        });
    }
}
