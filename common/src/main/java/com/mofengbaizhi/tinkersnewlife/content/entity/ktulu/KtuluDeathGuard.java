package com.mofengbaizhi.tinkersnewlife.content.entity.ktulu;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * TNL1139 user: "add protection, block die while it still has health".
 *
 * The Avaritia Neo sword does THREE things in a row: hurt(Float.MAX_VALUE), setHealth(0) and then
 * die(source). The vitals mixin already floors setHealth, so the health no longer reaches zero -- but
 * die() ignores health entirely and kills anyway. This cancels that death and, because a cancelled
 * death still leaves the entity in a death-ish state, it also restores the health and clears the
 * death timers so the boss keeps fighting.
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class KtuluDeathGuard {

    private KtuluDeathGuard() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        try {
            if (!(event.getEntity() instanceof KtuluMomo momo)) {
                return;
            }
            if (momo.level().isClientSide) {
                return;
            }
            // TNL1142 user: "one punch and the boss bar event is gone, the second one kills from full".
            // Cancelling EVERY death broke the normal flow (the bar is dropped and the entity is left
            // half dead). Only block the cheat kill: the Avaritia Neo infinity damage source, which
            // does hurt(MAX_VALUE) + setHealth(0) + die() in a row.
            String id;
            try {
                id = event.getSource().typeHolder().unwrapKey()
                        .map(k -> k.location().toString()).orElse("");
            } catch (Throwable t) {
                id = String.valueOf(event.getSource().getMsgId());
            }
            boolean cheatKill = id.contains("infinity");
            if (cheatKill && momo.getHealth() > 0.0F) {
                event.setCanceled(true);
                momo.deathTime = 0;
                momo.hurtTime = 0;
                momo.setHealth(Math.max(1.0F, momo.getHealth()));
                TinkersNewlife.LOGGER.warn(
                        "TNL1139 death blocked for the momo, it still had {} health",
                        String.format(java.util.Locale.ROOT, "%.1f", momo.getHealth()));
            }
        } catch (Throwable ignored) {
        }
    }
}
