package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import com.mofengbaizhi.tinkersnewlife.content.entity.ktulu.a;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * TNL1409 <b>墨默镜像血量"最后一步"同步</b> ✓
 *
 * <p>⚠ 为什么需要 ✗：有模组会在 <b>实体 tick 之后</b>（★关卡 tail / 自己的 tick 尾部 ✓）
 * 直接写 `DATA_HEALTH_ID` ✗ ⇒ ⭐我们在 `KtuluMomo#tick` 里写的镜像会被它再覆盖 ✓
 * ⇒ ⭐客户端看到低血/负血 ✗ ⭐而权威值没变 ⇒ ⭐一直不判定死亡 ✓。
 *
 * <p>⭐ 修法（通用 ✓ 不认模组）：⭐在 {@code ServerTickEvent.END}（★全服所有关卡都 tick 完之后 ✓）
 * 把每个存活 Boss 的**同步血量**强制写回**权威值** ✓ ⇒ ⭐谁先写都没用，⭐我们永远是最后一个 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KtuluMirrorSyncHandler {

    private KtuluMirrorSyncHandler() {
    }

    private static long TNL_LAST_LOG = 0L;
    private static long TNL_LAST_DELTA_LOG = 0L;

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onServerTickEnd(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || KtuluMomo.TNL_ACTIVE.isEmpty()) {
            return;
        }
        try {
            var it = KtuluMomo.TNL_ACTIVE.entrySet().iterator();
            while (it.hasNext()) {
                KtuluMomo momo = it.next().getValue();
                // TNL1410 ★只在"已移除 / 真死 / 不是 Boss"时出表 ✗
                //   ⚠ 旧写法还看 `isAlive()` ✗ —— 而某些模组把血写成负数后
                //     `isAlive()` 会变 false ⇒ ⭐我们直接把 Boss 移出表 ⇒ ⭐**再也不回写** ✓ ✓
                if (momo == null || momo.isRemoved() || momo.tnl$isDead()
                        || !(momo.level() instanceof ServerLevel) || !momo.tnl$isBossForm()) {
                    it.remove();
                    continue;
                }
                float auth = a.qF(momo);
                if (!Float.isFinite(auth) || auth < 0.0F) {
                    auth = 0.0F;
                }
                // ★直接写同步数据：★谁也改写不了 ✓
                a.qS(momo, auth);
                // TNL1411 ★中和"显示血量 delta"（★getHealth 被套了 min(真血, max+delta) ✓）
                boolean tnlFixed = a.qT(momo);
                if (tnlFixed && System.currentTimeMillis() - TNL_LAST_DELTA_LOG > 2000L) {
                    TNL_LAST_DELTA_LOG = System.currentTimeMillis();
                    TinkersNewlife.LOGGER.warn(
                            "TNL1411 health-delta neutralized: auth={} getHealth={}",
                            String.format(java.util.Locale.ROOT, "%.2f", auth),
                            String.format(java.util.Locale.ROOT, "%.2f", momo.getHealth()));
                }
                // TNL1410 诊断：三路血量分叉就报一行（每 2 秒最多一次）
                float shown = momo.getHealth();
                float raw = a.qR(momo);
                if ((Math.abs(shown - auth) > 0.5F || Math.abs(raw - auth) > 0.5F)
                        && System.currentTimeMillis() - TNL_LAST_LOG > 2000L) {
                    TNL_LAST_LOG = System.currentTimeMillis();
                    TinkersNewlife.LOGGER.warn(
                            "TNL1410 health divergence: getHealth={} DATA={} auth={} cache={} dead={} alive={}",
                            String.format(java.util.Locale.ROOT, "%.2f", shown),
                            String.format(java.util.Locale.ROOT, "%.2f", raw),
                            String.format(java.util.Locale.ROOT, "%.2f", auth),
                            String.format(java.util.Locale.ROOT, "%.2f", momo.tnl$authCache()),
                            momo.tnl$isDead(), momo.isAlive());
                }
            }
        } catch (Throwable ignored) {
            // ★同步失败绝不能连累服务器 tick ✓
        }
    }
}
