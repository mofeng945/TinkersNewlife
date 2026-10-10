package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * 客户端兜底：清理"监守者血条"。
 *
 * <p>给监守者加血条的是第三方 mod <b>akaishi</b>
 * （{@code WardenBossHandler}：监守者进世界就建 {@code ServerBossEvent}，UUID 随机；
 * 实体消失时它的清理没把玩家从血条上摘掉 → 条一直留在屏幕上）。
 * 我们不是条的主人，服务端也删不掉它（随机 UUID），只能由客户端把本地血条移掉。
 *
 * <h2>⚠ 这里踩过两次坑，代码必须保持"绝不可能抛异常"</h2>
 * <ol>
 *   <li>第一版用 accessor mixin 读 {@code BossHealthOverlay#events}：refmap 没映射上，
 *       mixin 静默失败 → 强转调用直接 {@code AbstractMethodError} → <b>客户端崩溃</b>。
 *       现在改成<b>按字段类型反射</b>（找该类里唯一的 {@code Map} 字段），不依赖字段名，
 *       也不依赖 refmap；</li>
 *   <li>这类"皮肤级"功能一旦抛异常就会毁掉整局游戏 —— 所以整个方法体包在 try/catch 里，
 *       失败最多就是"血条没清掉"，绝不往上抛。</li>
 * </ol>
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
        try {
            if (dropWardenBars()) {
                pending = 0;
            }
        } catch (Throwable t) {
            // 皮肤级功能：无论如何不能影响游戏
            pending = 0;
            TinkersNewlife.LOGGER.debug("[咒灵] 清理监守者血条失败（已忽略）：{}", t.toString());
        }
    }

    /**
     * 场上没有别的监守者时，移掉本地所有"名字是监守者"的血条。
     *
     * @return 是否已经清掉（清掉后不必再重试）
     */
    private static boolean dropWardenBars() throws Exception {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.gui == null) return false;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof Warden && e.isAlive()) {
                return false;    // 还有监守者在场 → 不动（可能是别的条的主人）
            }
        }
        BossHealthOverlay overlay = mc.gui.getBossOverlay();
        Map<?, ?> events = findMapField(overlay);
        if (events == null) return true;      // 找不到就当没这回事，不再重试
        String wardenName = Component.translatable("entity.minecraft.warden").getString();
        boolean removed = events.entrySet().removeIf(en -> {
            Object value = en.getValue();
            if (value == null) return false;
            try {
                Object name = value.getClass().getMethod("getName").invoke(value);
                return name instanceof Component c && c.getString().equals(wardenName);
            } catch (Throwable t) {
                return false;
            }
        });
        if (removed) {
            TinkersNewlife.LOGGER.debug("[咒灵] 已清除残留的监守者血条");
        }
        return true;
    }

    /**
     * 找对象里"唯一的 Map 字段"（{@code BossHealthOverlay#events}）。
     * <p>按类型找而不是按名字找：生产环境字段名是 SRG 名，按名字反射不可靠。
     */
    private static Map<?, ?> findMapField(Object owner) {
        for (Field f : owner.getClass().getDeclaredFields()) {
            if (!Map.class.isAssignableFrom(f.getType())) continue;
            try {
                f.setAccessible(true);
                Object v = f.get(owner);
                if (v instanceof Map<?, ?> map) return map;
            } catch (Throwable ignored) {
                // 试下一个
            }
        }
        return null;
    }
}
