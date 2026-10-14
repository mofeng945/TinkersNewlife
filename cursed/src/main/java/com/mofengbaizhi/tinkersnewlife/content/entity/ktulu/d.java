package com.mofengbaizhi.tinkersnewlife.content.entity.ktulu;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * TNL867 \u2605\u2605 \u6700\u7ec8\u9650\u4f24\uff08\u2605\u4e24\u9053\u4e8b\u4ef6\u90fd\u622a \u2713\uff09
 *
 * <p>\u2605\u4e3a\u4f55\u8981\u4e24\u9053\uff1a\u2605\u67d0\u4e9b\u6a21\u7ec4\u4f1a\u76f4\u63a5\u8c03 {@code actuallyHurt}/\u5199\u8840 \u2717
 * \u21d2 \u2605\u53ea\u622a\u4e00\u9053\u53ef\u80fd\u88ab\u8df3\u8fc7 \u2713\u3002
 * <p>\u2605\u4f18\u5148\u7ea7\u90fd\u662f LOWEST \u21d2 \u4fdd\u8bc1\u5728\u81ea\u5bb6\u7fa4\u661f\u4e4b\u5b50\uff081.3^\u7ea7\uff09\u4e4b\u540e \u2713\u3002
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class d {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        try {
            if (!(event.getEntity() instanceof KtuluMomo boss) || boss.level().isClientSide) {
                return;
            }
            float in = event.getAmount();
            float out = a.qA(boss, in);
            a.qB("HURT", event.getSource().getMsgId(), in, "->", out, a.qC(boss));
            event.setAmount(out);
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        try {
            if (!(event.getEntity() instanceof KtuluMomo boss) || boss.level().isClientSide) {
                return;
            }
            float in = event.getAmount();
            float out = a.q9(boss, in, boss.tickCount);
            a.qB("DAMAGE", event.getSource().getMsgId(), in, "->", out, a.qC(boss));
            event.setAmount(out);
            if (out > 0.0F) {
                boss.invulnerableTime = a.V0;
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * TNL910 \u2605\u2605 \u4e8b\u4ef6\u7ea7\u68c0\u6d4b\uff08\u2605\u7528\u6237\u63d0\u793a\uff1a
     * \u2605"\u4f60\u662f\u4e0d\u662f\u514d\u75ab\u7a97\u6709\u5199\u53d6\u6d88\u53d7\u51fb\u4e8b\u4ef6\u548c\u963b\u6b62\u6539\u8840\u7684\u903b\u8f91\uff0c\u68c0\u6d4b\u4e0d\u5230\u554a" \u2713\uff09
     *
     * <p>\u2605\u5bf9\uff1a\u2605\u82e5\u4e0a\u6e38\u628a {@code LivingAttackEvent} \u53d6\u6d88\u4e86 \u2717
     * \uff08\u2605\u6bd4\u5982\u65e0\u4e0b\u9650/\u7edd\u5bf9\u9632\u5fa1\u90a3\u7c7b\u903b\u8f91 \u2717\uff09\uff0c
     * \u2605{@code hurt()} **\u6839\u672c\u4e0d\u4f1a\u8dd1** \u2717 \u21d2 \u2605\u6211\u57cb\u5728 hurt/q2 \u91cc\u7684\u68c0\u6d4b
     * \u2605\u6c38\u8fdc\u4e0d\u4f1a\u4eae \u2713\u3002
     * \u2605\u73b0\u5728\uff1a\u2605\u76f4\u63a5\u542c\u4e8b\u4ef6 \u2713\uff08\u2605LOWEST \u4fdd\u8bc1\u770b\u5230\u6700\u7ec8\u7ed3\u679c \u2713\uff09
     *   \u00b7 \u2605``LivingAttackEvent`` \u88ab\u53d6\u6d88 \u2713
     *   \u00b7 \u2605``LivingHurtEvent`` \u91cf\u88ab\u538b\u5230 &lt;= 0 \u2713
     * \u21d2 \u2605\u90fd\u7b97"\u88ab\u6321\u4e0b"\u2717 \u21d2 \u2605\u53d1\u95ea\u8272\u5305 \u2713
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttack(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        try {
            if (!(event.getEntity() instanceof KtuluMomo boss) || boss.level().isClientSide) {
                return;
            }
            if (event.isCanceled()) {
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                        "TNL910 attack cancelled upstream: {}", event.getSource().getMsgId());
                boss.tnl$shieldFlash();
            }
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurtZero(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        try {
            if (!(event.getEntity() instanceof KtuluMomo boss) || boss.level().isClientSide) {
                return;
            }
            if (event.getAmount() <= 0.0F) {
                boss.tnl$shieldFlash();
            }
        } catch (Throwable ignored) {
        }
    }
}
