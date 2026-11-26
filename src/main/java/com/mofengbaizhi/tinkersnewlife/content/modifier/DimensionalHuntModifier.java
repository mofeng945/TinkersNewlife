package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.capability.EntityModifierCapability;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class DimensionalHuntModifier extends Modifier {
    private static final ModifierId ID = new ModifierId(TinkersNewlife.prefix("dimensional_hunt"));
    private static final float DAMAGE_MULTIPLIER = 0.9f;

    @Override public net.minecraft.network.chat.Component getDisplayName(int level) {
        return slimeknights.tconstruct.library.modifiers.util.ModifierLevelDisplay.NO_LEVELS.nameForLevel(this, level);
    }

    public static boolean hasHunt(Projectile projectile) {
        return EntityModifierCapability.getOrEmpty(projectile).getLevel(ID) > 0;
    }

    /** Check the persisted launch snapshot, never the shooter's currently held item. */
    public static boolean hasHunt(DamageSource source) {
        return source.getDirectEntity() instanceof Projectile projectile
                && !source.is(DamageTypeTags.IS_EXPLOSION) && hasHunt(projectile);
    }

    @SubscribeEvent
    public static void attenuate(LivingHurtEvent event) {
        if (!event.getEntity().level().isClientSide && hasHunt(event.getSource())) {
            event.setAmount(event.getAmount() * DAMAGE_MULTIPLIER);
        }
    }
}
