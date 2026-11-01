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

    @SubscribeEvent
    public static void onServerTickEnd(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || KtuluMomo.TNL_ACTIVE.isEmpty()) {
            return;
        }
        try {
            var it = KtuluMomo.TNL_ACTIVE.entrySet().iterator();
            while (it.hasNext()) {
                KtuluMomo momo = it.next().getValue();
                if (momo == null || momo.isRemoved() || !momo.isAlive()
                        || !(momo.level() instanceof ServerLevel) || momo.tnl$isDead()) {
                    it.remove();
                    continue;
                }
                if (!momo.tnl$isBossForm()) {
                    it.remove();
                    continue;
                }
                // ★直接写同步数据：★谁也改写不了 ✓
                a.qS(momo, a.qF(momo));
            }
        } catch (Throwable ignored) {
            // ★同步失败绝不能连累服务器 tick ✓
        }
    }
}
