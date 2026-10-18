package com.mofengbaizhi.tinkersnewlife.client;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * TNL1276 ★★ 墨默血条的**客户端兜底清理** ✓
 *
 * <p>★用户口径：★"换维度后变成两条，一条血条是幽灵血条" ✓
 *
 * <h3>⭐ 为什么服务端清理还不够 ✗</h3>
 * <ol>
 *   <li>⭐ 服务端侧已经补了两处 ✓（{@code setRemoved} 摘玩家 ＋ 每 tick 摘掉"不在本维度/
 *       超过 64 格"的玩家）✗ ★但**历史遗留的那一条**（本次修复之前就已经漏在客户端的）
 *       ★不会自己消失 ✗ —— ★它对应的 {@code ServerBossEvent} 早就没人动了 ✓；</li>
 *   <li>⭐ 跨维度那一瞬间的包序也不保险 ✗ —— ⭐ 旧条 REMOVE 与新条 ADD 若颠倒 ✓
 *       ⇒ ⭐ 客户端可能**新条刚挂上又被摘掉** ✓。</li>
 * </ol>
 *
 * <h3>⭐ 判据：★条数 &gt; 场上活着的墨默数 ⇒ 多出来的就是幽灵 ✓</h3>
 * <p>⭐ 处理方式是**把这些墨默条全摘掉** ✓（{@link BossHealthOverlay} 的 map 直接删 ✓）✗
 * ★而不是猜"哪一条是幽灵" ✗ —— ⭐ 因为服务端**每 20 tick 会强制 removePlayer＋addPlayer** ✓
 * ⇒ ⭐ 活着那条**一秒内一定被重新挂上** ✓ ★幽灵条对应的服务端已经没人发 ADD ✓
 * ⇒ ⭐ 永远不会回来 ✓✓
 *
 * <h3>⚠ 照抄 {@code ServantBossBarCleaner} 的安全口径</h3>
 * <ul>
 *   <li>⭐ 按**字段类型**反射找 {@code BossHealthOverlay#events} ✓（★不按字段名 ✗
 *       生产环境是 SRG 名 ✓）；</li>
 *   <li>⭐ 整个方法体包 try/catch ✓ —— ⭐ 这只影响一条血条 ✗
 *       ★无论如何不能把客户端带崩 ✓。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MomoBossBarCleaner {

    /** ★两次检查间隔 ✓（1 秒一次足够 ✓ ★服务端的强制重挂也是 1 秒一轮 ✓） */
    private static final int INTERVAL = 20;

    private static int tnl$cooldown = INTERVAL;
    private static String tnl$name = null;

    private MomoBossBarCleaner() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (--tnl$cooldown > 0) {
            return;
        }
        tnl$cooldown = INTERVAL;
        try {
            prune();
        } catch (Throwable t) {
            // 皮肤级功能：失败最多是"幽灵条没清掉"，绝不往上抛
            tnl$cooldown = INTERVAL * 5;
            TinkersNewlife.LOGGER.debug("TNL1276 momo bar prune failed: {}", t.toString());
        }
    }

    private static void prune() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.gui == null) {
            return;
        }
        if (tnl$name == null) {
            tnl$name = Component.translatable("entity.tinkersnewlife.ktulu_momo.bossbar").getString();
        }
        int alive = 0;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof KtuluMomo momo && momo.isAlive()) {
                alive++;
            }
        }
        Map<?, ?> events = findMapField(mc.gui.getBossOverlay());
        if (events == null) {
            return;
        }
        int bars = 0;
        for (Object value : events.values()) {
            if (isMomoBar(value)) {
                bars++;
            }
        }
        if (bars <= alive) {
            return;
        }
        events.entrySet().removeIf(en -> isMomoBar(en.getValue()));
        TinkersNewlife.LOGGER.warn("TNL1276 dropped {} stale momo boss bar(s), alive={}", bars, alive);
    }

    private static boolean isMomoBar(Object value) {
        if (value instanceof BossEvent be) {
            return tnl$name != null && tnl$name.equals(be.getName().getString());
        }
        return false;
    }

    /** 找对象里"唯一的 Map 字段"（{@code BossHealthOverlay#events}）。按类型找，不按名字找。 */
    private static Map<?, ?> findMapField(Object owner) {
        for (Field f : owner.getClass().getDeclaredFields()) {
            if (!Map.class.isAssignableFrom(f.getType())) {
                continue;
            }
            try {
                f.setAccessible(true);
                Object v = f.get(owner);
                if (v instanceof Map<?, ?> map) {
                    return map;
                }
            } catch (Throwable ignored) {
                // 试下一个
            }
        }
        return null;
    }
}
