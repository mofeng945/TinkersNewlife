package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.curse.shikigami.ShikigamiType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 式神公共接口：所有继承原版生物的子类实现。
 * <p>
 * 只声明式神身份/状态访问，AI 行为统一由 {@link ShikigamiBehavior} 驱动，
 * 渲染/模型/动画/纹理/碰撞箱完全复用原版生物。
 */
public interface ShikigamiMob {

    /** 式神类型 */
    ShikigamiType getShikigamiType();

    /** 共享状态（冷却/锁定/调伏/数据） */
    ShikigamiState getState();

    /** 主人（服务端） */
    @Nullable
    ServerPlayer getOwner();

    /** 当前锁定目标（未调伏时攻击主人与目标） */
    @Nullable
    LivingEntity getLockedTarget();

    /** 是否已调伏 */
    boolean isTamed();

    /** 初始化数值（召唤时调用，服务端）：血量/伤害/移速/体型 */
    void initStats(ServerPlayer player, ShikigamiType type, boolean tamed, @Nullable LivingEntity locked, int variant);

    /** 变体（玉犬黑白等） */
    int getShikigamiVariant();

    /** 体型缩放（渲染与碰撞箱共用） */
    float getShikigamiScale();

    /** 已调伏掩码位（由 ShikigamiHandler 管理，这里仅转发） */
    default boolean isTypeTamed(ServerPlayer player, ShikigamiType type) {
        return com.mofengbaizhi.tinkersnewlife.content.curse.shikigami.ShikigamiHandler.isTamed(player, type);
    }

    /** 供 ShikigamiHandler 使用的 owner UUID（服务端） */
    @Nullable
    UUID getOwnerId();
}
