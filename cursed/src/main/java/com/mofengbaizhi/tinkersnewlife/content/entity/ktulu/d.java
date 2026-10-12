package com.mofengbaizhi.tinkersnewlife.content.entity.ktulu;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * TNL866 \u2605\u2605 \u6700\u7ec8\u9650\u4f24\uff08\u2605LOWEST \u4f18\u5148\u7ea7 \u2713\uff09
 *
 * <p>\u4f4d\u7f6e\u5f88\u91cd\u8981\uff1a{@link LivingDamageEvent} \u662f**\u6240\u6709\u52a0\u4f24\u90fd\u7b97\u5b8c\u4e4b\u540e**\u7684\u6700\u540e\u4e00\u9053 \u2713\u3002
 * \u2605\u7528 LOWEST \u4f18\u5148\u7ea7 \u21d2 \u4fdd\u8bc1\u5728\u6240\u6709\u5176\u4ed6\u76d1\u542c\u5668\uff08\u2605\u5305\u62ec\u81ea\u5bb6
 * {@code ChildOfTheStarsHandler} \u7fa4\u661f\u4e4b\u5b50\u7684 1.3^\u7ea7 \u2717\uff09**\u4e4b\u540e**\u624d\u622a\u65ad \u2713\u3002
 *
 * <p>\u2605\u4e3a\u4f55\u4e0d\u5728 {@code hurt()} \u91cc\u622a\uff1a\u56e0\u4e3a {@code hurt()} \u91cc\u622a\u7684\u662f**\u4e58\u4e4b\u524d**\u7684\u6570 \u2717
 * \u21d2 \u2605\u7528\u6237\u5b9e\u6d4b\uff1a\u7070\u767d\u4e4b\u58a8\u4e00\u51fb 117 \u2713\uff08\u260637.5 \u00d7 1.3^\u7ea7 \u2713\uff09\u3002
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class d {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        try {
            if (!(event.getEntity() instanceof KtuluMomo boss)) {
                return;
            }
            if (boss.level().isClientSide) {
                return;
            }
            float allowed = a.q9(boss, event.getAmount(), boss.tickCount);
            event.setAmount(allowed);              // \u2605\u2605 \u5199\u56de\u6700\u7ec8\u4f24\u5bb3 \u2713
            if (allowed > 0.0F) {
                boss.invulnerableTime = a.V0;      // \u2605\u8fdb\u5165 1.5s \u65e0\u654c\u7a97 \u2713
            }
        } catch (Throwable ignored) {
        }
    }
}
