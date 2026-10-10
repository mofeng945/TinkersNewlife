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

    /** §1307 上一次看到的"头衔前缀"缓存（★用于实时刷新 ✓ 变了才踢名字缓存 ✓） */
    private static final java.util.Map<java.util.UUID, String> LAST_PREFIX = new java.util.concurrent.ConcurrentHashMap<>();

    /** 每 20 tick：前缀变了 ⇒ 踢掉名字缓存 ＋ 重新同步给客户端（★实时 ✓） */
    public static void refreshIfChanged(ServerPlayer sp) {
        try {
            String now = CurseTitles.prefix(sp);
            String before = LAST_PREFIX.get(sp.getUUID());
            if (now.equals(before)) {
                return;
            }
            LAST_PREFIX.put(sp.getUUID(), now);
            sp.refreshDisplayName();     // ★Forge：重算 displayName（聊天/玩家列表立刻变 ✓）
            CurseTitles.sync(sp);        // ★客户端镜像同步（选择界面 ✓）
        } catch (Throwable ignored) {
        }
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
            refreshIfChanged(sp);         // §1307 头衔实时刷新 ✓
        } catch (Throwable ignored) {
        }
    }
}