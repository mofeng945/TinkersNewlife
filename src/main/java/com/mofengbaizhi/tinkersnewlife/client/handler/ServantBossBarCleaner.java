package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.mixin.BossHealthOverlayAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;

/**
 * 客户端兜底：清理"监守者血条"。
 *
 * <p>给监守者加血条的是第三方 mod <b>akaishi</b>
 * （{@code WardenBossHandler}：监守者进世界就建 {@code ServerBossEvent}，UUID 随机；
 * 实体消失时它的清理没把玩家从血条上摘掉 → 条会一直留在屏幕上）。
 * 我们不是那条血条的主人，服务端按实体 UUID 删不掉它（随机 UUID），
 * 所以只能由客户端把本地血条移掉 —— 客户端正好持有全部血条的名字与 UUID。
 *
 * <p>安全措施：
 * <ul>
 *   <li>只在**场上已无其它监守者**时才清（避免误伤真的/别的来源的监守者条）；</li>
 *   <li>收到请求后 1 秒内每 tick 重试 —— 因为"实体移除"与"本包"的到达顺序不保证，
 *       实体还在时先跳过，等它被移除后再清。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServantBossBarCleaner {

    private ServantBossBarCleaner() {
    }

    /** 剩余重试 tick（1 秒） */
    private static int pending = 0;

    /** 服务端包到达时调用（见 {@code PacketDropWardenBars}） */
    public static void request() {
        pending = 20;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pending <= 0) return;
        pending--;
        dropWardenBars();
    }

    /** 场上没有别的监守者时，移掉本地所有"名字是监守者"的血条 */
    private static void dropWardenBars() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.gui == null) return;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof Warden && e.isAlive()) {
                return;      // 还有监守者在场 → 不动（可能是别的条的主人）
            }
        }
        BossHealthOverlay overlay = mc.gui.getBossOverlay();
        Map<UUID, LerpingBossEvent> events =
                ((BossHealthOverlayAccessor) (Object) overlay).tinkersnewlife$events();
        String wardenName = Component.translatable("entity.minecraft.warden").getString();
        boolean removed = events.entrySet().removeIf(en ->
                en.getValue().getName().getString().equals(wardenName));
        if (removed) {
            pending = 0;
            TinkersNewlife.LOGGER.debug("[咒灵] 已清除残留的监守者血条");
        }
    }
}
