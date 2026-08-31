package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.curse.shikigami.ShikigamiType;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 式神共享状态（每只式神一个实例，存于子类实体）。
 * <p>
 * 只保存运行时数据；渲染/动画/碰撞箱全部来自原版生物本身。
 */
public class ShikigamiState {

    /** 式神类型 */
    public ShikigamiType type = ShikigamiType.DOG;
    /** 主人 UUID（服务端） */
    @Nullable
    public UUID ownerId;
    /** 未调伏时的锁定目标 UUID */
    @Nullable
    public UUID lockedId;
    /** 是否已调伏 */
    public boolean tamed;

    // 行为冷却/状态
    public int attackCooldown;
    public int rangedCooldown;
    public int healCooldown;
    public int despawnTimer = 600;
    public int pounceTicks;      // 玉犬扑击
    public int diveTicks;        // 鵺俯冲
    public int stompTicks;       // 满象踏压
    @Nullable public UUID boundTargetId; // 大蛇盘绕
    public int boundTicks;
    public boolean charging;     // 贯牛冲撞
    public double chargeDist;
    public int chargeTimer;
    public int adaptation;       // 魔虚罗适应
    public int awayTicks;        // 未调伏脱离判定

    // 缩放后数值（召唤时由 initStats 计算）
    public double damage;
    public double speed;
    public double scale = 1.0;

    /** 变体（玉犬黑白） */
    public int variant;
}
