package com.mofengbaizhi.tinkersnewlife.content.curse.shikigami;

import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHandler;
import com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiMob;
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
        List<ShikigamiMob> alive = new java.util.ArrayList<>();
        for (net.minecraft.world.entity.Entity e : player.serverLevel().getEntitiesOfClass(
                net.minecraft.world.entity.Entity.class,
                player.getBoundingBox().inflate(512.0),
                entity -> entity instanceof ShikigamiMob)) {
            if (e instanceof ShikigamiMob sm && sm.getState().ownerId != null
                    && sm.getState().ownerId.equals(player.getUUID())
                    && sm.getShikigamiType() == type) {
                alive.add(sm);
            }
        }
        if (!alive.isEmpty()) {
            int cost = type.summonCost(player);
            int refund = Math.max(1, (int) Math.ceil(cost / 2.0));
            if (!CursePowerHelper.isCurseInfinite(player)) {
                CursePowerHelper.addCurse(player, refund);
            }
            for (ShikigamiMob s : alive) {
                ((net.minecraft.world.entity.Entity) s).discard();
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
        spawnShikigami(player, type, tamed, locked);
        player.displayClientMessage(Component.translatable(
                tamed ? "message.tinkersnewlife.ten_shadows.summon" : "message.tinkersnewlife.ten_shadows.summon_untamed",
                Component.translatable(type.getLangKey())), true);
        return true;
    }

    /** 生成式神（玉犬生成黑白一对，其余一只；脱兔额外生成 7 只兔群） */
    private static void spawnShikigami(ServerPlayer player, ShikigamiType type, boolean tamed, LivingEntity locked) {
        var level = player.serverLevel();
        int count = type == ShikigamiType.DOG ? 2 : 1;
        for (int i = 0; i < count; i++) {
            spawnOne(player, type, tamed, locked, i, level);
        }
        if (type == ShikigamiType.RABBIT) {
            for (int i = 0; i < 7; i++) {
                spawnOne(player, type, tamed, locked, 0, level);
            }
        }
    }

    private static void spawnOne(ServerPlayer player, ShikigamiType type, boolean tamed, LivingEntity locked,
                                 int variant, net.minecraft.server.level.ServerLevel level) {
        var e = createEntity(type, level);
        if (e == null) return;
        double ox = level.random.nextDouble() - 0.5;
        double oz = level.random.nextDouble() - 0.5;
        e.moveTo(player.getX() + ox, player.getY() + 0.2, player.getZ() + oz,
                player.getYRot() + 180.0F, 0.0F);
        var mob = (com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiMob) e;
        mob.initStats(player, type, tamed, locked, variant);
        level.addFreshEntity(e);
    }

    /** 按类型创建对应原版生物子类 */
    private static net.minecraft.world.entity.Entity createEntity(ShikigamiType type,
                                                                  net.minecraft.server.level.ServerLevel level) {
        return switch (type) {
            case DOG -> new com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiWolf(
                    com.mofengbaizhi.tinkersnewlife.content.ModEntities.SHIKIGAMI_WOLF.get(), level);
            case NUE -> new com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiPhantom(
                    com.mofengbaizhi.tinkersnewlife.content.ModEntities.SHIKIGAMI_PHANTOM.get(), level);
            case SERPENT -> new com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiSilverfish(
                    com.mofengbaizhi.tinkersnewlife.content.ModEntities.SHIKIGAMI_SILVERFISH.get(), level);
            case TOAD -> new com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiFrog(
                    com.mofengbaizhi.tinkersnewlife.content.ModEntities.SHIKIGAMI_FROG.get(), level);
            case ELEPHANT -> new com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiPig(
                    com.mofengbaizhi.tinkersnewlife.content.ModEntities.SHIKIGAMI_PIG.get(), level);
            case RABBIT -> new com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiRabbit(
                    com.mofengbaizhi.tinkersnewlife.content.ModEntities.SHIKIGAMI_RABBIT.get(), level);
            case DEER -> new com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiGoat(
                    com.mofengbaizhi.tinkersnewlife.content.ModEntities.SHIKIGAMI_GOAT.get(), level);
            case OX -> new com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiCow(
                    com.mofengbaizhi.tinkersnewlife.content.ModEntities.SHIKIGAMI_COW.get(), level);
            case TIGER -> new com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiSheep(
                    com.mofengbaizhi.tinkersnewlife.content.ModEntities.SHIKIGAMI_SHEEP.get(), level);
            case MAHORAGA -> new com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiIronGolem(
                    com.mofengbaizhi.tinkersnewlife.content.ModEntities.SHIKIGAMI_IRON_GOLEM.get(), level);
        };
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
