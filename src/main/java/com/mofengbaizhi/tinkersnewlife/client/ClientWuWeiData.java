package com.mofengbaizhi.tinkersnewlife.client;

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

    private ClientWuWeiData() {}

    /** 设置/解除某玩家的伪装（空 formId = 解除） */
    public static void setDisguise(UUID playerId, String formId) {
        if (formId == null || formId.isEmpty()) {
            DISGUISES.remove(playerId);
            PROXIES.remove(playerId);
        } else {
            DISGUISES.put(playerId, formId);
            // 强制下次重建代理（形态可能已切换）
            PROXIES.remove(playerId);
        }
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

    /** 释放某玩家的代理（玩家登出/世界切换时调用） */
    public static void clearProxy(UUID playerId) {
        PROXIES.remove(playerId);
        DISGUISES.remove(playerId);
    }

    /** 世界卸载时清空全部 */
    public static void clearAll() {
        DISGUISES.clear();
        PROXIES.clear();
    }

    private static EntityType<?> entityType(String id) {
        return ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(id));
    }
}
