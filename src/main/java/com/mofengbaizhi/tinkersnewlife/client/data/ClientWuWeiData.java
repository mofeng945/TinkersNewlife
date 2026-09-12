package com.mofengbaizhi.tinkersnewlife.client.data;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 无为转变 客户端状态：
 * <ul>
 *   <li>disguise：玩家 UUID → 伪装形态 EntityType 注册名（由 PacketWuWeiDisguise 广播同步）</li>
 *   <li>渲染代理：每个伪装玩家对应一个"仅渲染"的代理实体（目标生物类型实例，不进世界），
 *       每 tick 从被伪装玩家同步位置/姿态后由 RenderPlayerEvent 手动渲染，实现"玩家渲染成生物"</li>
 * </ul>
 */
public final class ClientWuWeiData {

    /** 玩家 UUID → 伪装形态注册名（空 = 未伪装） */
    private static final Map<UUID, String> DISGUISES = new HashMap<>();
    /** 玩家 UUID → 渲染代理实体（目标类型实例，仅渲染用，不进世界） */
    private static final Map<UUID, Entity> PROXIES = new HashMap<>();

    // ===== 非玩家实体（傀儡操术的傀儡 / 黑鸟操术的黑鸟等"可操控单位"）的形态伪装 =====
    /** 实体 UUID → 形态注册名；用 UUID 而非实体 id（id 会被复用）。带访问序淘汰，见 MOB_LIMIT */
    private static final Map<UUID, String> MOB_DISGUISES =
            new java.util.LinkedHashMap<>(16, 0.75F, true);
    /** 实体 UUID → 渲染代理实体 */
    private static final Map<UUID, Entity> MOB_PROXIES =
            new java.util.LinkedHashMap<>(16, 0.75F, true);
    /**
     * 伪装项上限：实体消失后客户端收不到通知，只能靠"最久未用先淘汰"兜底。
     * 正常游玩同时存在的傀儡/黑鸟只有个位数，这个上限纯粹是防御性内存保护。
     */
    private static final int MOB_LIMIT = 512;

    private ClientWuWeiData() {}

    /** 设置/解除某玩家的伪装（空 formId = 解除） */
    public static void setDisguise(UUID playerId, String formId) {
        boolean empty = formId == null || formId.isEmpty();
        String old = DISGUISES.get(playerId);
        boolean changed = empty ? old != null : !formId.equals(old);
        if (empty) {
            DISGUISES.remove(playerId);
            PROXIES.remove(playerId);
        } else {
            DISGUISES.put(playerId, formId);
            // 仅在形态真正变化时重建代理（服务端每 5s 会重播一次，避免无谓重建）
            if (!formId.equals(old)) PROXIES.remove(playerId);
        }
        if (changed) {
            TinkersNewlife.LOGGER.info("[WuWei] 客户端伪装同步: {} -> {}",
                    playerId, empty ? "解除" : formId);
            // 本地玩家自己变形时给一条动作栏提示：第一人称看不到自己，必须切第三人称
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (!empty && mc.player != null && mc.player.getUUID().equals(playerId)) {
                mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.tinkersnewlife.wu_wei.disguise_hint"), true);
            }
        }
    }

    /** 当前伪装形态注册名（空字符串 = 未伪装）——诊断用 */
    public static String getDisguise(UUID playerId) {
        String id = DISGUISES.get(playerId);
        return id == null ? "" : id;
    }

    /** 该玩家是否处于伪装 */
    public static boolean isDisguised(UUID playerId) {
        return DISGUISES.containsKey(playerId);
    }

    /** 获取渲染代理（没有则创建目标类型实例；创建失败返回 null） */
    public static Entity getOrCreateProxy(UUID playerId, LivingEntity real) {
        Entity proxy = PROXIES.get(playerId);
        String formId = DISGUISES.get(playerId);
        if (formId == null) return null;
        if (proxy == null || !proxy.getType().equals(entityType(formId))) {
            EntityType<?> type = entityType(formId);
            if (type == null || Minecraft.getInstance().level == null) return null;
            try {
                proxy = type.create(Minecraft.getInstance().level);
            } catch (Exception e) {
                return null;
            }
            if (proxy == null) return null;
            PROXIES.put(playerId, proxy);
        }
        return proxy;
    }

    /** ⭐ 反查：这个渲染代理属于哪个玩家（用于把玩家的装备/手持物品画到伪装生物身上） */
    @javax.annotation.Nullable
    public static UUID ownerOfProxy(net.minecraft.world.entity.Entity proxy) {
        if (proxy == null) return null;
        for (Map.Entry<UUID, Entity> e : PROXIES.entrySet()) {
            if (e.getValue() == proxy) return e.getKey();
        }
        return null;
    }

    /** 释放某玩家的代理（玩家登出/世界切换时调用） */
    public static void clearProxy(UUID playerId) {
        PROXIES.remove(playerId);
        DISGUISES.remove(playerId);
    }

    /** 世界卸载时清空全部 */
    public static void clearAll() {
        DISGUISES.clear();
        PROXIES.clear();
        MOB_DISGUISES.clear();
        MOB_PROXIES.clear();
    }

    // ============================================================
    //  非玩家实体（可操控单位）的形态伪装
    // ============================================================

    /** 设置/解除某实体的形态伪装（formId 空 = 解除） */
    public static void setMobDisguise(UUID entityId, String formId) {
        if (entityId == null) return;
        boolean empty = formId == null || formId.isEmpty();
        String old = MOB_DISGUISES.get(entityId);
        if (empty) {
            MOB_DISGUISES.remove(entityId);
            MOB_PROXIES.remove(entityId);
        } else {
            MOB_DISGUISES.put(entityId, formId);
            if (!formId.equals(old)) MOB_PROXIES.remove(entityId);   // 形态变了才重建代理
            // 兜底淘汰：实体没了客户端不会收到通知，靠访问序把最久未用的挤出去
            java.util.Iterator<UUID> it = MOB_DISGUISES.keySet().iterator();
            while (MOB_DISGUISES.size() > MOB_LIMIT && it.hasNext()) {
                UUID victim = it.next();
                if (victim.equals(entityId)) continue;
                MOB_PROXIES.remove(victim);
                it.remove();
            }
        }
        if (empty ? old != null : !formId.equals(old)) {
            TinkersNewlife.LOGGER.debug("[WuWei] 实体形态伪装同步: {} -> {}", entityId, empty ? "解除" : formId);
        }
    }

    /** 某实体的形态伪装注册名（空字符串 = 无） */
    public static String getMobDisguise(UUID entityId) {
        String id = MOB_DISGUISES.get(entityId);
        return id == null ? "" : id;
    }

    /** 取实体渲染代理（没有则按形态创建；失败返回 null） */
    public static Entity getOrCreateMobProxy(UUID entityId, LivingEntity real) {
        Entity proxy = MOB_PROXIES.get(entityId);
        String formId = MOB_DISGUISES.get(entityId);
        if (formId == null) return null;
        if (proxy == null || !proxy.getType().equals(entityType(formId))) {
            EntityType<?> type = entityType(formId);
            if (type == null || Minecraft.getInstance().level == null) return null;
            try {
                proxy = type.create(Minecraft.getInstance().level);
            } catch (Exception e) {
                return null;
            }
            if (proxy == null) return null;
            MOB_PROXIES.put(entityId, proxy);
        }
        return proxy;
    }


    private static EntityType<?> entityType(String id) {
        return ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(id));
    }
}
