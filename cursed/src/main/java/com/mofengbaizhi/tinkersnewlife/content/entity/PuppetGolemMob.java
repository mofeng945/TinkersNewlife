package com.mofengbaizhi.tinkersnewlife.content.entity;

import net.minecraft.server.level.ServerPlayer;

/**
 * 傀儡操术 操控傀儡通用接口：
 * 铁傀儡（{@link PuppetIronGolem}）与雪傀儡（{@link PuppetSnowGolem}）都实现本接口，
 * 供 {@code PuppetTechnique} 统一检索 / 召回 / 绑定，及输入包驱动。
 */
public interface PuppetGolemMob {

    /** 客户端输入更新（由 PacketPuppetInput 每 tick 调用，含攻击/使用键与玩家视角） */
    void puppetSetInput(float zza, float xxa, boolean jumping, boolean shift,
                        boolean left, boolean right, float yRot, float xRot);

    /** 绑定主人并记录召唤消耗（用于召回返还 30%） */
    void puppetBindOwner(ServerPlayer player, int paidCost);

    /** 当前主人（在线 ServerPlayer），不在线/死亡返回 null */
    ServerPlayer puppetOwner();

    /** 本次召唤实付咒力（返还基数） */
    int puppetPaidCost();

    void puppetSetPaidCost(int cost);

    /** 结束（消散/自爆/死亡）：解除本体定身、视角回归、实体消失 */
    void puppetFinish(boolean exploded);
}
