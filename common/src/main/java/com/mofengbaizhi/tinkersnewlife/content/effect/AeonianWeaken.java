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
    public static final int PER_HIT = 10;
    public static final int MAX = 100;
    /** \u2605\u786c\u63a7\u65f6\u957f\uff08\u260510 \u79d2 \u2713\uff09 */
    public static final int STUN_TICKS = 200;

    private AeonianWeaken() {
    }

    private static CompoundTag tag(LivingEntity e) {
        return e.getPersistentData();
    }

    public static int ticks(LivingEntity e) {
        return tag(e).getInt(TICKS);
    }

    public static int stun(LivingEntity e) {
        return tag(e).getInt(STUN);
    }

    /** \u2605\u4e00\u6b21\u547d\u4e2d\uff1a\u2605\u53e0 tick \u2713 */
    public static void add(LivingEntity e, int amount) {
        if (stun(e) > 0) {
            return;
        }
        int v = Math.min(MAX, ticks(e) + amount);
        tag(e).putInt(TICKS, v);
    }

    /** \u2605\u524a\u5f31\u7cfb\u6570\uff08\u26051.0 = \u4e0d\u524a \u2713\uff09\uff1a\u2605\u56db\u5c42\u9012\u589e \u2713 */
    public static float factor(LivingEntity e) {
        int t = ticks(e);
        if (t <= 0) {
            return 1.0F;
        }
        int layer = Math.min(4, (t + 19) / 20);              // \u26051..4 \u5c42 \u2713
        float reduce;
        if (layer == 1) {
            reduce = 0.10F;
        } else if (layer == 2) {
            reduce = 0.30F;
        } else if (layer == 3) {
            reduce = 0.60F;
        } else {
            reduce = 1.00F;
        }
        return 1.0F - reduce;
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
        for (ServerLevel level : server.getAllLevels()) {
            for (var e : level.getAllEntities()) {
                if (e instanceof LivingEntity le && !le.level().isClientSide
                        && (ticks(le) > 0 || stun(le) > 0)) {
                    touched.add(le);
                }
            }
        }
        for (LivingEntity le : touched) {
            int st = stun(le);
            if (st > 0) {
                // \u2605\u4e0d\u53ef\u540d\u72b6\u6001\uff1a\u2605\u786c\u63a7 \u2713\uff08\u2605\u4e0d\u80fd\u52a8/\u4e0d\u80fd\u8df3/\u4e0d\u80fd\u653b\u51fb \u2713\uff09
                le.setDeltaMovement(Vec3.ZERO);
                le.hurtMarked = true;
                le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 250, false, false, false));
                le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 25, 250, false, false, false));
                le.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 25, 250, false, false, false));
                le.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 25, 4, false, false, false));
                tag(le).putInt(STUN, st - 1);
                continue;
            }
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
            tag(le).putInt(TICKS, t - 1);           // \u2605\u6bcf tick \u6d41\u901d 1 tick \u2713
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
