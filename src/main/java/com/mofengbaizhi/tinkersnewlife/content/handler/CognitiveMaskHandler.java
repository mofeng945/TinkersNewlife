package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import com.mofengbaizhi.tinkersnewlife.content.item.CognitiveMaskItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 双向认知阻碍面具·服务端效果。
 *
 * <ol>
 *   <li><b>怪物锁定不到你</b>：{@link LivingChangeTargetEvent} 直接取消
 *       （凡是"新的锁定目标是戴着面具的玩家"一律作废）；</li>
 *   <li><b>清扫已有锁定</b>：每秒扫一遍，把"戴上之前就锁着你"的怪物的目标清掉
 *       —— 只靠事件的话，戴面具前已经被锁的怪会一直打你 ✗；</li>
 *   <li><b>隐身态</b>（可配置）：雷达那一层只能靠隐身标记实现（见物品类注释），
 *       每 tick 续期；配置关掉则只保留上面两条。</li>
 * </ol>
 *
 * <p>⚠ 隐身态只影响"别人怎么感知你"，不改变任何数值；{@code BlackBirdEntity} 结束时会把主人的隐身
 * 置回 false（黑鸟操术的收尾），本类每 tick 续期，因此戴着面具时操控黑鸟结束也不会"破隐" ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CognitiveMaskHandler {

    private CognitiveMaskHandler() {}

    /**
     * 被本面具置为隐身态的玩家。
     * <p>⭐ 必须有这张表：只"每 tick 设 true"的话，摘下面具（或把配置关掉）会把人**永久隐下去** ✗。
     * 还原时若玩家另有效果类隐身，原版的药水 tick 会自己再置回 true ✓（两者不打架）。
     */
    private static final java.util.Set<java.util.UUID> MASK_INVISIBLE =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 新锁定：目标是面具佩戴者 → 作废（非玩家实体才管；佩戴者自己锁定别人不受影响） */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof Mob)) return;
        if (!(event.getNewTarget() instanceof ServerPlayer prey)) return;
        if (prey == event.getEntity()) return;
        if (CognitiveMaskItem.isWorn(prey)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;

        boolean invisible = ModConfig.COGNITIVE_MASK_INVISIBLE.get();
        // 隐身态：每 tick 续期；面具摘下 / 配置关掉 → 还原（见 MASK_INVISIBLE 的注释）
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (CognitiveMaskItem.isWorn(player) && invisible) {
                if (!player.isInvisible()) player.setInvisible(true);
                MASK_INVISIBLE.add(player.getUUID());
            } else if (MASK_INVISIBLE.remove(player.getUUID())) {
                player.setInvisible(false);
            }
        }

        // 清扫已有锁定：每秒一次（戴面具前就被锁住的怪也要松开）
        if (server.getTickCount() % 20 != 0) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof Mob mob)) continue;
                if (mob.getTarget() instanceof ServerPlayer prey
                        && CognitiveMaskItem.isWorn(prey)) {
                    mob.setTarget(null);
                }
            }
        }
    }
}
