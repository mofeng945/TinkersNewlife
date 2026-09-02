package com.mofengbaizhi.tinkersnewlife.client;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 无为转变 客户端渲染替换：
 * <p>
 * 被伪装玩家（服务端广播 PacketWuWeiDisguise）在渲染时：
 * 1. 取消原版玩家模型渲染（RenderPlayerEvent.Pre cancel）；
 * 2. 用"渲染代理实体"（目标生物类型实例，仅客户端、不进世界、无攻击/受击/同步）替代绘制：
 *    每 tick 把被伪装玩家的位置/旋转/动画状态同步给代理，再调用目标生物的渲染器在相同 pose 下渲染。
 * 这样玩家"看起来"变成所选生物，同时不创建任何服务端替身实体（避免崩溃/双身/攻击本体问题）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WuWeiDisguiseRenderer {

    /** 代理实体缓存：玩家 uuid → 目标类型实例（不进世界） */
    private static final Map<UUID, Entity> PROXIES = new ConcurrentHashMap<>();

    private WuWeiDisguiseRenderer() {}

    /** 每 tick 同步代理状态（位置/旋转/动画字段由渲染事件内同步，此处仅清理无效代理） */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (mc.level == null) PROXIES.clear();
            return;
        }
    }

    /** 玩家登出/世界切换清理 */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player p = event.getEntity();
        PROXIES.remove(p.getUUID());
        ClientWuWeiData.clearProxy(p.getUUID());
    }

    /** 渲染玩家前：若处于伪装，取消默认渲染并改绘目标生物代理 */
    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        UUID uuid = player.getUUID();
        if (!ClientWuWeiData.isDisguised(uuid)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        // 第一人称本地玩家不渲染自己身体（无 RenderPlayerEvent），无需额外处理
        Entity proxy = ClientWuWeiData.getOrCreateProxy(uuid, player);
        if (proxy == null || !(proxy instanceof LivingEntity proxyLiving)) {
            // 代理不可用：仍显示玩家本体（保守回退）
            return;
        }

        // 取消玩家默认渲染
        event.setCanceled(true);

        // 同步代理：位置与玩家一致 + 姿态字段
        proxyLiving.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        proxyLiving.yBodyRot = player.yBodyRot;
        proxyLiving.yBodyRotO = player.yBodyRotO;
        proxyLiving.yHeadRot = player.yHeadRot;
        proxyLiving.yHeadRotO = player.yHeadRotO;
        proxyLiving.hurtTime = player.hurtTime;
        proxyLiving.deathTime = player.deathTime;
        proxyLiving.tickCount = player.tickCount;
        proxyLiving.setDeltaMovement(player.getDeltaMovement());

        // 用目标生物渲染器在相同 pose 下渲染代理（不进世界 → 无阴影/名称，可接受）
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        EntityRenderer<? super Entity> renderer = dispatcher.getRenderer(proxy);
        if (renderer == null) return;
        try {
            renderer.render(proxy, player.getYRot(), event.getPartialTick(),
                    event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[WuWei] 代理渲染失败，回退玩家外观: {}", t.toString());
        }
    }
}
