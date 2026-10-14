package com.mofengbaizhi.tinkersnewlife.content.effect;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * TNL1043 \u2605\u2605 \u6052\u6b63\u79e9\u5668\uff1a\u2605"\u7ec6\u96ea/\u7740\u706b"\u5f0f\u7684\u7a00\u91ca tick \u6548\u679c \u2713
 *
 * <p>\u2605\u7528\u6237\u53e3\u5f84 \u2713\uff1a
 * <ul>
 *   <li>\u2605\u6309**\u7d2f\u79ef tick \u6570**\u9010\u6e10\u767e\u5206\u6bd4\u51cf\u5c11\u653b\u51fb\u529b \u2713\uff0c
 *       \u2605\u6700\u5927 **100 tick \u524a\u5f31 100%** \u2713\uff1b</li>
 *   <li>\u2605\u6bcf tick **\u6d41\u901d 1 tick** \u2713\uff08\u2605\u4e0d\u5237\u65b0\u5c31\u81ea\u5df1\u6d88\u6563 \u2713\uff09\uff1b</li>
 *   <li>\u2605**\u6bcf\u5c42\u524a\u5f31\u5f3a\u5ea6\u9012\u589e** \u2713\uff1a\u2605\u6bcf 20 tick \u4e00\u5c42 \u2713\uff0c
 *       \u2605\u56db\u5c42\u5206\u522b\u524a 10% / 20% / 30% / 40% \u2713 \u21d2 \u2605\u5408\u8ba1 100% \u2713\uff1b</li>
 *   <li>\u2605100 tick \u53e0\u6ee1 \u21d2 \u2605**\u7206\u53d1\u751f\u547d\u4e0a\u9650 10% \u7684\u771f\u5b9e\u4f24\u5bb3** \u2713\uff0c
 *       \u2605\u968f\u540e\u9677\u5165**\u4e0d\u53ef\u540d\u72b6\u6001 10 \u79d2**\uff08\u2605\u786c\u63a7 \u2713\uff09
 *       \u2605\u5e76\u6e05\u96f6 tick \u2713\u3002</li>
 * </ul>
 *
 * <p>\u2605\u5b58\u50a8\uff1a\u2605\u6302\u5728**\u5b9e\u4f53\u81ea\u5df1\u7684 persistent data** \u2713
 * \uff08\u2605\u4e0d\u5f15\u5165 capability \u2713\uff0c\u2605\u5b58\u6863\u81ea\u52a8\u8ddf\u7740\u8d70 \u2713\uff09\u3002
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class AeonianWeaken {

    public static final String TICKS = "tnl_ao_weaken";
    public static final String STUN = "tnl_ao_stun";
    /** \u2605\u4e00\u6b21\u547d\u4e2d\u52a0\u591a\u5c11 tick \u2713 */
    public static final int PER_HIT = 30;   // TNL1045 \u2605\u7528\u6237\uff1a\u2605\u547d\u4e2d\u4e00\u6b21\u53e0 30 tick \u2713
    public static final int MAX = 200;   // TNL1047 user: tick cap raised to 200
    /** \u2605\u786c\u63a7\u65f6\u957f\uff08\u260510 \u79d2 \u2713\uff09 */
    public static final int STUN_TICKS = 200;

    /** TNL1072 user: "the timer keeps stalling" -- scanning every entity of every level each tick
     * was the stutter, the effect class itself is fine (its isDurationEffectTick returns true and we
     * no longer refresh anything). We now only look at entities that were actually marked. */
    private static final java.util.Set<java.util.UUID> TRACKED = new java.util.HashSet<>();

    private AeonianWeaken() {
    }

    private static CompoundTag tag(LivingEntity e) {
        return e.getPersistentData();
    }

    /**
     * TNL1049 reads the tick count from the hidden mark effect duration.
     * That works on both sides, so the client can drive the screen effect with it.
     */
    public static int ticks(LivingEntity e) {
        // TNL1070 plain persistent data again: no mob effect means nothing shows up in effect lists
        return tag(e).getInt(TICKS);
    }

    public static int stun(LivingEntity e) {
        return tag(e).getInt(STUN);
    }

    /** \u2605\u4e00\u6b21\u547d\u4e2d\uff1a\u2605\u53e0 tick \u2713 */
    public static void add(LivingEntity e, int amount) {
        // TNL1073 user: "while the unnameable state is on, a new burst seems not to trigger".
        // Two gates were blocking it: this early return, and the ticker handling the stun before
        // the cap. Both are gone now, so hits stack during the state and can burst again.
        int v = Math.min(MAX, ticks(e) + amount);
        TRACKED.add(e.getUUID());
        tag(e).putInt(TICKS, v);
        // TNL1070 for players mirror the count into a hidden item cooldown, which the client can read
        if (e instanceof net.minecraft.world.entity.player.Player pl) {
            pl.getCooldowns().addCooldown(
                    com.mofengbaizhi.tinkersnewlife.content.ModItems.AEONIAN_MARKER.get(), Math.max(1, v));
        }
        TinkersNewlife.LOGGER.warn("TNL1046 weaken stacked on {} -> {} ticks (max {})",
                e.getName().getString(), v, MAX);
        if (v >= MAX) {
            // TNL1071 user: "no matter how I hit it the burst never triggers" -- make the exact moment
            // visible in the log so we can tell stacking apart from the burst gate.
            TinkersNewlife.LOGGER.warn("TNL1071 {} reached the cap on the stack itself, burst should fire "
                    + "on the next server tick pass", e.getName().getString());
        }
    }

    /**
     * TNL1044 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u4e0d\u662f\u53e0\u5c42\uff0c\u662f\u76f4\u63a5\u6309 tick \u6570\u524a\u51cf\u653b\u51fb\u529b" \u2713
     *
     * <p>\u2605\u76f4\u63a5\u7ebf\u6027 \u2713\uff1a\u26051 tick = \u524a 1% \u2713 \u21d2
     * \u2605100 tick = \u524a 100% \u2713\uff08\u2605\u4e0d\u518d\u6309 20 tick \u5206\u5c42\u52a0\u6743 \u2717\uff09\u3002
     */
    public static float factor(LivingEntity e) {
        int t = ticks(e);
        if (t <= 0) {
            return 1.0F;
        }
        return 1.0F - Math.min(1.0F, t / (float) MAX);   // TNL1047 1 tick = 0.5%, 200 ticks = 100%
    }

    /** TNL1043 \u2605\u6bcf tick\uff1a\u2605\u9012\u51cf 1 tick \u002b \u2605\u6ee1\u5c42\u7206\u53d1 \u002b \u2605\u786c\u63a7\u7ef4\u6301 \u2713 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        List<LivingEntity> touched = new ArrayList<>();
        if (!TRACKED.isEmpty()) {
            java.util.Iterator<java.util.UUID> it = TRACKED.iterator();
            while (it.hasNext()) {
                java.util.UUID id = it.next();
                net.minecraft.world.entity.Entity found = null;
                for (ServerLevel level : server.getAllLevels()) {
                    found = level.getEntity(id);
                    if (found != null) {
                        break;
                    }
                }
                if (found instanceof LivingEntity le && le.isAlive()
                        && (ticks(le) > 0 || stun(le) > 0)) {
                    touched.add(le);
                } else {
                    it.remove();                 // TNL1072 stop tracking it once it is done
                }
            }
        }
        for (LivingEntity le : touched) {
            int t = ticks(le);
            if (t <= 0) {
                continue;
            }
            if (t >= MAX) {
                // \u2605\u53e0\u6ee1\uff1a\u2605\u7206\u53d1\u771f\u4f24 \u002b \u2605\u786c\u63a7 10 \u79d2 \u002b \u2605tick \u6e05\u96f6 \u2713
                float dmg = le.getMaxHealth() * 0.10F;
                try {
                    com.mofengbaizhi.tinkersnewlife.util.TruePierce.apply(null, le, dmg);
                } catch (Throwable th) {
                    le.hurt(le.damageSources().magic(), dmg);
                }
                tag(le).putInt(STUN, STUN_TICKS);
                tag(le).putInt(TICKS, 0);
                if (le instanceof net.minecraft.world.entity.player.Player pl2) {
                    pl2.getCooldowns().removeCooldown(
                            com.mofengbaizhi.tinkersnewlife.content.ModItems.AEONIAN_MARKER.get());
                }
                if (le.level() instanceof ServerLevel sl) {
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL,
                            le.getX(), le.getY() + 1.0D, le.getZ(), 24, 0.4D, 0.6D, 0.4D, 0.02D);
                }
                le.level().playSound(null, le.blockPosition(),
                        net.minecraft.sounds.SoundEvents.ELDER_GUARDIAN_CURSE,
                        net.minecraft.sounds.SoundSource.HOSTILE, 1.2F, 0.7F);
                TinkersNewlife.LOGGER.warn("TNL1043 aeonian burst on {} for {} true damage, stunned 10s",
                        le.getName().getString(), dmg);
                continue;
            }
            // TNL1049 the mark effect decays by itself one tick at a time, nothing to do here
            int st = stun(le);
            if (st > 0) {
                // TNL1048 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u4e0d\u662f\u6302\u4e0d\u53ef\u540d\u72b6\u5417\uff1f\u6211\u5199\u7684\u90a3\u4e2a\u6548\u679c" \u2713
                //   \u2605\u771f\u76f8 \u2717\uff1a\u2605\u4f60\u5199\u7684\u662f `UnnameableEffect`\uff08\u2605id ``tinkersnewlife:unnameable`` \u2713\uff09
                //     \u2605\u800c\u6211\u81ea\u5df1\u62fc\u4e86\u4e00\u5806**\u539f\u7248\u6548\u679c**\uff08\u2605\u8fdf\u7f13/\u865a\u5f31/\u53cd\u80c3 \u2717\uff09
                //     \u2605\u6839\u672c\u6ca1\u7528\u4f60\u90a3\u4e2a \u2713\u2713\uff08\u2605\u597d\u5728\u53ea\u662f\u540e\u9762\u8fd9\u6bb5\uff0c\u2605现\u5728\u6362\u6389 \u2713\uff09
                //   \u2605\u73b0\u5728 \u2713\uff1a\u2605\u53cd\u5c04\u53d6\u6ce8\u518c\u8868\u91cc\u7684 ``unnameable`` \u2713
                //     \uff08\u2605\u2605 \u4e0d\u5f15\u5165\u8de8\u6a21\u5757\u7f16\u8bd1\u4f9d\u8d56 \u2713\uff1a\u2605`AeonianWeaken` \u5728 common \u2717
                //      \u2605\u800c `UnnameableEffect` \u5728 imagination \u2713\uff09
                var tnl$eff = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.get(
                        new net.minecraft.resources.ResourceLocation("tinkersnewlife", "unnameable"));
                if (tnl$eff != null) {
                    le.addEffect(new MobEffectInstance(tnl$eff, STUN_TICKS, 0, false, false, false));
                }
                le.setDeltaMovement(Vec3.ZERO);
                le.hurtMarked = true;
                tag(le).putInt(STUN, st - 1);
                continue;
            }
        }
    }

    /** TNL1047 user: "I want it to stick on any attack" -- holding the weapon and attacking stacks ticks */
    @SubscribeEvent
    public static void onAttackHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) {
            return;
        }
        if (attacker == event.getEntity()) {
            return;
        }
        if (attacker.getMainHandItem().getItem()
                instanceof com.mofengbaizhi.tinkersnewlife.content.item.AeonianOrderItem) {
            add(event.getEntity(), PER_HIT);
        }
    }

    /** TNL1043 \u2605\u524a\u5f31\u653b\u51fb\u529b\uff1a\u2605\u53ea\u524a"\u2605\u6709\u4e3b\u7684\u7269\u7406/\u9b54\u6cd5\u653b\u51fb" \u2713 */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        // TNL1043b \u2605\u2605 \u5173\u952e\u4fee\u6b63 \u2713\uff1a
        //   \u2605``LivingHurtEvent`` \u662f\u5728**\u53d7\u5bb3\u8005**\u8eab\u4e0a\u89e6\u53d1\u7684 \u2717
        //   \u2605\u800c\u6211\u4eec\u8981\u524a\u7684\u662f"\u2605**\u653b\u51fb\u8005**\u7684\u653b\u51fb\u529b" \u2713
        //   \u21d2 \u2605\u5e94\u8be5\u770b ``getSource().getEntity()`` \u8eab\u4e0a\u6709\u6ca1\u6709 tick \u2713
        //     \uff08\u2605\u6211\u7b2c\u4e00\u7248\u5199\u6210\u770b\u53d7\u5bb3\u8005 \u2717 \u21d2 \u2605\u6c38\u8fdc\u4e0d\u4f1a\u751f\u6548 \u2713\uff09
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) {
            return;
        }
        if (attacker == event.getEntity()) {
            return;
        }
        float f = factor(attacker);
        if (f < 1.0F) {
            event.setAmount(event.getAmount() * f);
        }
    }
}
