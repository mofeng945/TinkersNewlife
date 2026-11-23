package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.capability.EntityModifierCapability;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class ArthropodFuryModifier extends Modifier {
    private static final ModifierId ID = new ModifierId(TinkersNewlife.prefix("arthropod_fury"));

    @SubscribeEvent public static void damage(LivingHurtEvent event) {
        var victim = event.getEntity();
        var source = event.getSource();
        if (victim.level().isClientSide || !IntegrationLoader.isIceAndFire()
                || !(source.getEntity() instanceof LivingEntity attacker)) return;
        int rank;
        if (source.getDirectEntity() instanceof Projectile projectile) {
            rank = EntityModifierCapability.getOrEmpty(projectile).getLevel(ID);
        } else if (source.getDirectEntity() == attacker) {
            rank = ToolHelper.getActiveModifierLevel(ToolHelper.getToolStack(attacker.getMainHandItem()), ID);
        } else return;
        if (rank <= 0) return;
        var entityId = ForgeRegistries.ENTITY_TYPES.getKey(victim.getType());
        boolean deathworm = entityId != null && entityId.toString().equals("iceandfire:deathworm");
        boolean arthropod = victim.getMobType() == MobType.ARTHROPOD && !deathworm;
        event.setAmount(event.getAmount() * (1 + rank * (arthropod ? .05f : .10f)));
    }
}
