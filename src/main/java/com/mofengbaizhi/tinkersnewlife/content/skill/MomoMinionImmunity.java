package com.mofengbaizhi.tinkersnewlife.content.skill;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Applies before ordinary damage effects; direct health-cut skills must also filter allies. */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class MomoMinionImmunity {
    private MomoMinionImmunity() {}
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof KtuluMomo
                && KtuluMomo.tnl$isMomoMinion(event.getEntity())) event.setCanceled(true);
    }
}
