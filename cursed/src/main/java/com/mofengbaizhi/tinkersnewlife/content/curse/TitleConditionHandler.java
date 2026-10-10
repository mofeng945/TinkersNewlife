package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** §1303 头衔条件：杀墨默 ⇒ 世界厌弃之人；每 20 tick 扫饰品 ⇒ 七咒之戒给"举世皆敌"。 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TitleConditionHandler {

    private TitleConditionHandler() {
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        try {
            if (!(event.getEntity() instanceof com.mofengbaizhi.tinkersnewlife.content.entity.MomoMerchant)) {
                return;
            }
            if (event.getSource().getEntity() instanceof ServerPlayer sp) {
                CurseTitles.grant(sp, "world_abandoned");
            }
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        try {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            if (!(event.player instanceof ServerPlayer sp) || sp.tickCount % 20 != 0) {
                return;
            }
            CurseTitles.scanWorn(sp);
        } catch (Throwable ignored) {
        }
    }
}