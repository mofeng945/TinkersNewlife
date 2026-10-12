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
}
