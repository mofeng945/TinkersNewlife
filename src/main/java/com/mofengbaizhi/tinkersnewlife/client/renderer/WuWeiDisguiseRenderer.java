package com.mofengbaizhi.tinkersnewlife.client.renderer;
import com.mofengbaizhi.tinkersnewlife.client.data.ClientWuWeiData;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 无为转变 客户端渲染替换：
 * <p>
 * 被伪装玩家（服务端广播 PacketWuWeiDisguise）渲染时：
 * 1. 取消原版玩家模型渲染（RenderPlayerEvent.Pre cancel）；
 * 2. 用"渲染代理实体"（目标生物类型实例，仅客户端、不进世界）替代绘制。
 * 代理不进世界不 tick，因此每帧渲染前需把真实玩家的动画状态反射同步给代理：
 *    - walkAnimation（speedOld/speed/position）：走路摆腿、闲置浮动
 *    - tickCount / 姿态旋转（yBodyRot/yHeadRot/xRot/yRot 及 *O 平滑值）
 *    - hurtTime/deathTime：受击闪白
 * 代理仅作为渲染参数，不参与攻击/受击/同步，避免替身崩溃与双身问题。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WuWeiDisguiseRenderer {

    /** 代理实体缓存：玩家 uuid → 目标类型实例（不进世界） */
    private static final Map<UUID, Entity> PROXIES = new ConcurrentHashMap<>();

    // WalkAnimationState 私有字段反射（拷贝走路动画状态用）
    private static Field WAS_SPEED_OLD;
    private static Field WAS_SPEED;
    private static Field WAS_POSITION;
    private static boolean fieldsReady = false;

    static {
        try {
            // ⭐ 必须用 ObfuscationReflectionHelper：生产环境字段名是 SRG（f_xxxxx_），
            // 直接 getDeclaredField("speedOld") 必然抛 NoSuchFieldException（曾导致代理走路动画缺失）。
            Class<?> c = WalkAnimationState.class;
            WAS_SPEED_OLD = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(c, "speedOld");
            WAS_SPEED = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(c, "speed");
            WAS_POSITION = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(c, "position");
            fieldsReady = true;
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[WuWei] 无法访问 WalkAnimationState 字段，走路动画将缺失: {}", t.toString());
        }
    }

    private WuWeiDisguiseRenderer() {}

    /** 玩家登出/世界切换清理 */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player p = event.getEntity();
        PROXIES.remove(p.getUUID());
        ClientWuWeiData.clearProxy(p.getUUID());
    }

    /**
     * 伪装期间隐藏第一人称的手（含 YSM 的自模型/手臂）：
     * 本地玩家已经变成生物了，第一人称不该再看到人类的手。
     */
    @SubscribeEvent
    public static void onRenderHand(net.minecraftforge.client.event.RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (ClientWuWeiData.isDisguised(mc.player.getUUID())) {
            event.setCanceled(true);
        }
    }

    /** 渲染玩家前：若处于伪装，取消默认渲染并改绘目标生物代理 */
    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        UUID uuid = player.getUUID();
        if (!ClientWuWeiData.isDisguised(uuid)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Entity proxy = ClientWuWeiData.getOrCreateProxy(uuid, player);
        if (!(proxy instanceof LivingEntity proxyLiving)) {
            return; // 代理不可用：保守回退玩家本体渲染
        }

        // 取消玩家默认渲染
        event.setCanceled(true);

        // 同步位置与全部动画状态
        syncProxy(player, proxyLiving);

        // 用目标生物渲染器在相同 pose 下渲染代理（不进世界 → 无阴影/名称）
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

    /**
     * 把真实实体（玩家 / 被无为转变换形态的可操控单位）的动画状态完整拷贝到代理（让代理"动起来"）；
     * 供本类与伪装渲染 Mixin 共用。用 {@link LivingEntity} 而不是 {@link Player}：
     * 傀儡操术的傀儡、黑鸟操术的黑鸟这些"原地换形态"的单位也要走同一套同步。
     */
    public static void syncProxy(LivingEntity real, LivingEntity proxy) {
        // 位置与朝向（渲染以 pose 为基准，此处保证字段一致供模型姿态计算）
        proxy.moveTo(real.getX(), real.getY(), real.getZ(), real.getYRot(), real.getXRot());
        proxy.xRotO = real.xRotO;
        proxy.yRotO = real.yRotO;
        proxy.yBodyRot = real.yBodyRot;
        proxy.yBodyRotO = real.yBodyRotO;
        proxy.yHeadRot = real.yHeadRot;
        proxy.yHeadRotO = real.yHeadRotO;
        proxy.tickCount = real.tickCount;
        proxy.hurtTime = real.hurtTime;
        proxy.deathTime = real.deathTime;
        proxy.setPose(real.getPose());
        proxy.setOnGround(real.onGround());
        proxy.setSprinting(real.isSprinting());
        proxy.setSwimming(real.isSwimming());
        proxy.setShiftKeyDown(real.isShiftKeyDown());
        proxy.setDeltaMovement(real.getDeltaMovement());
        // ⭐ 攻击/挥手动画与位置历史：模型摆臂用 attackAnim，插值用 xo/yo/zo（之前没同步 → 走路像"立着滑行"）
        proxy.attackAnim = real.attackAnim;
        proxy.oAttackAnim = real.oAttackAnim;
        proxy.xo = real.xo;
        proxy.yo = real.yo;
        proxy.zo = real.zo;
        proxy.xOld = real.xOld;
        proxy.yOld = real.yOld;
        proxy.zOld = real.zOld;
        proxy.hurtDuration = real.hurtDuration;
        proxy.invulnerableTime = real.invulnerableTime;
        proxy.setAirSupply(real.getAirSupply());
        // 走路动画状态（speedOld/speed/position）
        if (fieldsReady) {
            try {
                WalkAnimationState src = real.walkAnimation;
                WalkAnimationState dst = proxy.walkAnimation;
                WAS_SPEED_OLD.setFloat(dst, WAS_SPEED_OLD.getFloat(src));
                WAS_SPEED.setFloat(dst, WAS_SPEED.getFloat(src));
                WAS_POSITION.setFloat(dst, WAS_POSITION.getFloat(src));
            } catch (Throwable t) {
                // 忽略：个别字段失败不影响主体渲染
            }
        }
    }
}
