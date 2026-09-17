package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
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
 * 双向认知阻碍面具·服务端效果：<b>怪物锁定不到你</b>。
 *
 * <ol>
 *   <li>{@link LivingChangeTargetEvent} 直接取消 —— 凡是"新的锁定目标是戴着面具的玩家"一律作废
 *       （该事件由 {@code ForgeHooks.onLivingChangeTarget} 触发，脑 AI（监守者那类）也走它 ✓）；</li>
 *   <li><b>清扫已有锁定</b>：每秒扫一遍，把"戴上之前就已经锁着你"的怪物的目标清掉
 *       —— 只靠事件的话，戴面具前已经被锁的怪会一直打你 ✗。</li>
 * </ol>
 *
 * <h2>⚠ 这里**故意不再**给玩家挂"隐身标记"（用户实测教训）</h2>
 * 曾经的做法是：把佩戴者标记成 {@code isInvisible()}（MC 里"雷达不显示/索敌不到/名牌不显示"共用它），
 * 再在渲染层把身体画回来。
 * <ul>
 *   <li>原版渲染路径能画回来 ✓，**但接管玩家渲染的模组（YSM / 是，史蒂夫模型）自己读那个标记、
 *       模型照样被藏掉** ✗ —— 用户实测："不渲染 ysm 时没隐身，但 ysm 还是把模型隐藏了"；</li>
 *   <li>YSM 的类是混淆名，往它身上注入又脆又脏 ✗。</li>
 * </ul>
 * 所以现在**玩家身上不带任何状态**：人（原版 / YSM / 任何渲染模组）照常可见 ✓，
 * "雷达不显示"改由 {@code XaeroRadarMixin} 在雷达层定点过滤、
 * "无名字"由 {@code CognitiveMaskClientHandler} 挡、">索敌不到"由本类挡 ✓。
 * <p>代价：以前靠 {@code isInvisible()} 判断目标是否可见的**其他模组**（部分怪 AI、部分雷达）
 * 不再自动把你当隐身 ✗ —— 本整合包内需要的那几处已由上面三条覆盖 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CognitiveMaskHandler {

    private CognitiveMaskHandler() {}

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

    /** 每秒清扫一次"已经锁在面具佩戴者身上"的怪物目标 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;
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
