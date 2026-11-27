package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.util.ModifierLevelDisplay;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.UUID;

public final class DragonHusbandryModifier extends Modifier {
    private static final ModifierId ID = new ModifierId(TinkersNewlife.prefix("dragon_husbandry"));
    private static final UUID HEALTH_ID = UUID.fromString("0e14b910-8233-49ac-b0f0-5fbd41828dee");
    private static final UUID ATTACK_ID = UUID.fromString("1905b532-b27a-4c93-8267-2139508203c1");

    @Override public Component getDisplayName(int level) {
        return ModifierLevelDisplay.NO_LEVELS.nameForLevel(this, level);
    }

    /** Call after loading the dragon and before applying maximum-health-dependent abilities. */
    public static void applyOnRelease(ToolStack staff, LivingEntity dragon, int registeredCount) {
        apply(dragon, ToolHelper.getActiveModifierLevel(staff, ID) > 0 ? Math.max(0, registeredCount) * .1d : 0);
    }

    /** Call after changing the roster, including the addition of a newly tamed dragon. */
    public static void refreshReleased(ToolStack staff, ServerLevel level, ListTag roster) {
        UUID owner;
        try { owner = UUID.fromString(staff.getPersistentData().getString(TinkersNewlife.prefix("staff_owner"))); }
        catch (IllegalArgumentException invalid) { return; }
        double bonus = ToolHelper.getActiveModifierLevel(staff, ID) > 0 ? roster.size() * .1d : 0;
        for (int i = 0; i < roster.size(); i++) {
            var entry = roster.getCompound(i);
            if (entry.getInt("state") != 0) continue;
            UUID uuid;
            try { uuid = UUID.fromString(entry.getString("uuid")); }
            catch (IllegalArgumentException invalid) { continue; }
            for (ServerLevel world : level.getServer().getAllLevels()) {
                if (!(world.getEntity(uuid) instanceof LivingEntity dragon) || !dragon.isAlive()) continue;
                var type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(dragon.getType());
                if (type != null && type.toString().equals(entry.getString("type"))
                        && type.getNamespace().equals("iceandfire") && type.getPath().endsWith("_dragon")
                        && dragon instanceof net.minecraft.world.entity.TamableAnimal tame && tame.isTame()
                        && owner.equals(tame.getOwnerUUID())) apply(dragon, bonus);
                break;
            }
        }
    }

    private static void apply(LivingEntity dragon, double bonus) {
        float health = dragon.getHealth();
        replace(dragon.getAttribute(Attributes.MAX_HEALTH), HEALTH_ID, bonus);
        replace(dragon.getAttribute(Attributes.ATTACK_DAMAGE), ATTACK_ID, bonus);
        // Preserve absolute HP when the maximum rises; only clamp on decreases.
        if (health > dragon.getMaxHealth()) dragon.setHealth(dragon.getMaxHealth());
    }

    private static void replace(AttributeInstance attribute, UUID id, double bonus) {
        if (attribute == null) return;
        var current = attribute.getModifier(id);
        if (current != null && Double.compare(current.getAmount(), bonus) == 0) return;
        attribute.removeModifier(id);
        if (bonus > 0) attribute.addPermanentModifier(new AttributeModifier(id,
                "tinkersnewlife.dragon_husbandry", bonus, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
}
