package com.mofengbaizhi.tinkersnewlife.content.curse.shikigami;

import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHandler;
import com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 十种影法术 调伏与召唤处理器（服务端）
 * <p>
 * - 调伏状态：玩家持久数据位掩码，每位对应一种式神；玉犬默认可用（无需调伏）
 * - 首次召唤未调伏式神：式神同时攻击锁定的目标与主人；主人/目标死亡或脱离战斗则消失；
 *   式神被击败 → 调伏成功，此后可正常召唤
 * - 咒力只在召唤时扣除（无维持消耗）；数值/体型/速度受亲和与输出缩放
 */
public final class ShikigamiHandler {

    private ShikigamiHandler() {}

    /** 玩家持久数据：已调伏式神位掩码（位 = ShikigamiType.ordinal()） */
    public static final String KEY_TAMED_MASK = "tinkersnewlife.tamed_shikigami";

    // ============================================================
    //  调伏状态
    // ============================================================

    /** 该式神是否已调伏（玉犬永远可用） */
    public static boolean isTamed(ServerPlayer player, ShikigamiType type) {
        if (type == ShikigamiType.DOG) return true;
        return (getTamedMask(player) & (1 << type.ordinal())) != 0;
    }

    /** 已调伏位掩码 */
    public static int getTamedMask(ServerPlayer player) {
        return player.getPersistentData().getInt(KEY_TAMED_MASK);
    }

    /** 标记调伏成功 */
    public static void markTamed(ServerPlayer player, ShikigamiType type) {
        int mask = getTamedMask(player) | (1 << type.ordinal());
        player.getPersistentData().putInt(KEY_TAMED_MASK, mask);
        // 立即同步客户端（选择界面高亮/提示）
        CursePowerHandler.syncToClient(player);
    }

    // ============================================================
    //  召唤
    // ============================================================

    /**
     * 选择界面选择式神后调用（服务端）：
     * 场上已有同类型存活式神 → 召回并返还一半咒力；
     * 否则检查咒力 → 扣除 → 生成式神（未调伏 → 敌意模式，锁定视线目标）。
     */
    public static boolean summon(ServerPlayer player, ShikigamiType type) {
        // 熔断期间无法召唤
        if (CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return false;
        }
        // 召回：场上已有同类型存活式神 → 收回并返还一半咒力
        List<ShikigamiEntity> alive = player.serverLevel().getEntitiesOfClass(ShikigamiEntity.class,
                player.getBoundingBox().inflate(512.0),
                s -> s.isAlive() && s.getOwner() == player && s.getShikigamiType() == type);
        if (!alive.isEmpty()) {
            int cost = type.summonCost(player);
            int refund = Math.max(1, (int) Math.ceil(cost / 2.0));
            if (!CursePowerHelper.isCurseInfinite(player)) {
                CursePowerHelper.addCurse(player, refund);
            }
            for (ShikigamiEntity s : alive) {
                s.discard();
            }
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.ten_shadows.recall",
                    Component.translatable(type.getLangKey()), refund), true);
            return true;
        }
        // 咒力只在此刻扣除
        if (!CursePowerHelper.isCurseInfinite(player)) {
            int cost = type.summonCost(player);
            if (CursePowerHelper.payCurseWithSoulFallback(player, cost) < 0) {
                player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
                return false;
            }
        }
        boolean tamed = isTamed(player, type);
        // 锁定目标：玩家视线上的实体（用于未调伏式神的敌意目标）
        LivingEntity locked = tamed ? null : findLookTarget(player);
        ShikigamiEntity.spawnPair(player, type, tamed, locked);
        player.displayClientMessage(Component.translatable(
                tamed ? "message.tinkersnewlife.ten_shadows.summon" : "message.tinkersnewlife.ten_shadows.summon_untamed",
                Component.translatable(type.getLangKey())), true);
        return true;
    }

    /** 未调伏式神被击败 → 调伏成功 */
    public static void onShikigamiDefeated(ServerPlayer owner, ShikigamiType type) {
        if (!isTamed(owner, type)) {
            markTamed(owner, type);
            owner.displayClientMessage(Component.translatable(
                    "message.tinkersnewlife.ten_shadows.tamed", Component.translatable(type.getLangKey())), true);
        }
    }

    /** 视线索敌（与术式相同的 16 格判定） */
    public static LivingEntity findLookTarget(ServerPlayer player) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(16.0));
        AABB box = player.getBoundingBox().expandTowards(look.scale(16.0)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
                e -> !e.isSpectator() && e.isPickable() && (e instanceof LivingEntity), 16.0 * 16.0);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }
}
