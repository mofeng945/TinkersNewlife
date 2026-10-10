package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** §1303 击杀墨默至少一次 ⇒ 头衔"世界厌弃之人"（用户口径 ✓）。 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TitleKillHandler {

    private TitleKillHandler() {
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
}