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

    /** TNL1147 rate limit for the tamper probe */
    private static long TNL_STAMP = 0L;

    private KtuluDeathGuard() {
    }

    /**
     * TNL1165 user: "isn't it obvious that it cannot attack while casting". Right -- that belongs to
     * the cast itself. We do NOT clear the AI (that sticks); instead every hit the momo lands while it
     * is casting is simply cancelled, so it keeps its goals and loses nothing afterwards.
     */
    @SubscribeEvent
    public static void onMomoAttack(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        try {
            if (!(event.getSource().getEntity() instanceof KtuluMomo attacker)
                    || attacker.level().isClientSide) {
                return;
            }
            if (attacker.tnl$isBusy()) {
                event.setCanceled(true);
            }
        } catch (Throwable ignored) {
        }
    }

    /** TNL1143 probe: show exactly what the momo health does around a hit. */
    @SubscribeEvent
    public static void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        try {
            if (!(event.getEntity() instanceof KtuluMomo momo) || momo.level().isClientSide) {
                return;
            }
            String src;
            try {
                src = event.getSource().typeHolder().unwrapKey()
                        .map(k -> k.location().toString()).orElse("?");
            } catch (Throwable t) {
                src = String.valueOf(event.getSource().getMsgId());
            }
            TinkersNewlife.LOGGER.warn(
                    "TNL1143 hit on momo src={} amount={} before={} auth={} max={}",
                    src, event.getAmount(), momo.getHealth(), com.mofengbaizhi.tinkersnewlife.content.entity.ktulu.a.qF(momo), momo.getMaxHealth());

            // TNL1145 the log proved the real problem: the health FIELD sat at 28 while the mirror
            // still said 1365, so an ordinary 43 damage hit finished it off and the bar looked empty.
            // Resync the field to the mirror and never let a single hit exceed one tick of budget.
            float tnl$auth = com.mofengbaizhi.tinkersnewlife.content.entity.ktulu.a.qF(momo);
            if (tnl$auth > 0.0F) {
                if (momo.getHealth() < tnl$auth - 0.5F) {
                    momo.setHealth(tnl$auth);
                    TinkersNewlife.LOGGER.warn("TNL1145 momo health resynced to {}",
                            String.format(java.util.Locale.ROOT, "%.1f", tnl$auth));
                }
                float tnl$budget = Math.max(1.0F, momo.getMaxHealth()
                        * com.mofengbaizhi.tinkersnewlife.content.entity.ktulu.a.C0);
                if (event.getAmount() > tnl$budget) {
                    event.setAmount(tnl$budget);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // TNL1180 the once a second boss bar watchdog is gone: KtuluMomo.tick() maintains the bar every
    // tick now (progress from the authoritative health, forced remove+add once a second), so this
    // whole handler had become a second source of truth for the same thing.

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        try {
            if (!(event.getEntity() instanceof KtuluMomo momo)) {
                return;
            }
            TinkersNewlife.LOGGER.warn("TNL1143 death of momo src={} health={} auth={}",
                    event.getSource().getMsgId(), momo.getHealth(), com.mofengbaizhi.tinkersnewlife.content.entity.ktulu.a.qF(momo));
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
                momo.tnl$unDead();       // TNL1146 undo the latch so the protection keeps working
                TinkersNewlife.LOGGER.warn(
                        "TNL1139 death blocked for the momo, it still had {} health",
                        String.format(java.util.Locale.ROOT, "%.1f", momo.getHealth()));
            }
        } catch (Throwable ignored) {
        }
    }
}
