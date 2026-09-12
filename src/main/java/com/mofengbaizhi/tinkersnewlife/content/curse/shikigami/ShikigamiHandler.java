package com.mofengbaizhi.tinkersnewlife.content.curse.shikigami;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
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
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 十种影法术 调伏与召唤处理器（服务端）
 * <p>
 * - 调伏状态：玩家持久数据位掩码，每位对应一种式神；玉犬默认可用（无需调伏）
 * - 首次召唤未调伏式神：式神同时攻击锁定的目标与主人；主人/目标死亡或脱离战斗则消失；
 *   式神被击败 → 调伏成功，此后可正常召唤
 * - 咒力只在召唤时扣除（无维持消耗）；数值/体型/速度受亲和与输出缩放
 * - 玩家死亡：场上所有未回收的式神直接清除（调伏进度是玩家持久数据，不随式神实体消失）
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShikigamiHandler {

    private ShikigamiHandler() {}

    /** 场上该玩家的所有存活式神实体 */
    public static List<net.minecraft.world.entity.Entity> findActiveFor(ServerPlayer player) {
        List<net.minecraft.world.entity.Entity> result = new java.util.ArrayList<>();
        for (net.minecraft.world.entity.Entity e : player.serverLevel().getEntitiesOfClass(
                net.minecraft.world.entity.Entity.class,
                player.getBoundingBox().inflate(512.0),
                entity -> entity instanceof ShikigamiMob)) {
            if (e instanceof ShikigamiMob sm && sm.getState().ownerId != null
                    && sm.getState().ownerId.equals(player.getUUID()) && e.isAlive()) {
                result.add(e);
            }
        }
        return result;
    }

    /** 收回全部式神，返回应返还的咒力（按召唤消耗的一半估算；同类型多只只算一次成本，
     *  如脱兔召唤 8 只只扣 1 次咒力，回收也只按 1 次返还） */
    public static int recallAll(ServerPlayer player, List<net.minecraft.world.entity.Entity> entities) {
        java.util.Set<ShikigamiType> types = new java.util.HashSet<>();
        for (net.minecraft.world.entity.Entity e : entities) {
            if (e instanceof ShikigamiMob sm) {
                types.add(sm.getShikigamiType());
            }
        }
        int totalCost = 0;
        for (ShikigamiType type : types) {
            totalCost += type.summonCost(player);
        }
        return Math.max(1, (int) Math.ceil(totalCost / 2.0));
    }

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

    /**
     * 嵌合影翳庭领域用：召唤"全体十影式神 ×2"（全部已调伏、数值按施术者亲和/输出调幅），
     * 散布于施术者身边索敌。返回所有生成的式神实体 id（供领域关闭时清除）。
     */
    public static java.util.List<Integer> summonDomainSet(ServerPlayer owner) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        var level = owner.serverLevel();
        for (ShikigamiType type : ShikigamiType.values()) {
            // 每种式神 2 倍数量（玉犬默认一对 → 4 只；脱兔默认 8 只 → 16 只；其余 ×2）
            int count = (type == ShikigamiType.DOG ? 2 : type == ShikigamiType.RABBIT ? 8 : 1) * 2;
            for (int i = 0; i < count; i++) {
                net.minecraft.world.entity.Entity e = createEntity(type, level);
                if (e == null) continue;
                double ox = level.random.nextDouble() - 0.5;
                double oz = level.random.nextDouble() - 0.5;
                e.moveTo(owner.getX() + ox, owner.getY() + 0.5, owner.getZ() + oz,
                        owner.getYRot() + 180.0F, 0.0F);
                var mob = (com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiMob) e;
                mob.initStats(owner, type, true, null, i % 2); // 已调伏，跟随/索敌由行为驱动
                level.addFreshEntity(e);
                out.add(e.getId());
            }
        }
        return out;
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

    /** 玩家死亡：场上所有未回收的式神直接清除（不返还咒力；调伏进度在玩家持久数据，不受影响） */
    @SubscribeEvent
    public static void onOwnerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        List<net.minecraft.world.entity.Entity> active = findActiveFor(sp);
        if (active.isEmpty()) return;
        for (net.minecraft.world.entity.Entity e : active) {
            e.discard();
        }
        TinkersNewlife.LOGGER.info("[式神] 玩家 {} 死亡，场上 {} 只未回收式神已直接清除",
                sp.getName().getString(), active.size());
    }

    // ============================================================
    //  ⭐ 调伏战保护：主人打"未调伏式神"永远算数
    // ============================================================

    /**
     * 未调伏的式神在数据上同样带着 {@code ownerId}，于是特别容易被各式"友军保护"顺手豁免掉：
     * 领域/AoE 的阵营判定（{@code isFriendlyTo}）、
     * {@code TamableAnimal#getOwner()} 这类"按方法名反射找主人"的兼容代码、
     * 以及<b>整合包里其它模组</b>的同类逻辑，都可能把"主人打自己的未调伏式神"变成 0 伤害。
     * 而调伏的唯一途径就是<b>击败它</b>——这一卡，调伏战就彻底打不下去了。
     *
     * <p>这里在<b>最低优先级</b>兜底（此时其它监听器都已跑完）：
     * 只要是"主人打自己的未调伏式神"，就撤销任何取消；
     * 若伤害已在事件阶段被抹成 0，则恢复到攻击者的攻击力（至少 1）。
     *
     * <p>例外：<b>攻击者自身</b>被禁止攻击的情况<b>不</b>撤销——投射咒法「静止」罚站、
     * 伏诛赐死「亡灵有罪」攻击力归零，都是对攻击者的惩罚，不该被调伏战绕过。
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onOwnerAttackUntamedShikigami(
            net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide) return;
        ServerPlayer owner = untamedShikigamiOwnerAttacker(event.getEntity(), event.getSource());
        if (owner == null) return;
        if (event.isCanceled()) {
            event.setCanceled(false);
            TinkersNewlife.LOGGER.warn("[调伏战] 撤销了一次对未调伏式神（{}）的攻击拦截：攻击者 {} —— "
                            + "说明有「友军保护」把未调伏式神算成了友军，请把这条日志反馈给作者",
                    net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType()),
                    owner.getName().getString());
        }
    }

    /**
     * 同上，但处理"事件阶段伤害被抹成 0"的情况（{@code LivingHurtEvent}/{@code LivingDamageEvent}
     * 都可能被别的模组把 amount 改成 0）：恢复成攻击者的攻击力（至少 1），保证调伏战打得动。
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onOwnerHurtUntamedShikigami(
            net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        ServerPlayer owner = untamedShikigamiOwnerAttacker(event.getEntity(), event.getSource());
        if (owner == null || event.getAmount() > 0.0F) return;
        float attack = (float) owner.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        event.setAmount(Math.max(1.0F, attack));
        TinkersNewlife.LOGGER.warn("[调伏战] 未调伏式神的伤害在受伤事件里被抹成 0，已按攻击力恢复：攻击者 {}",
                owner.getName().getString());
    }

    /** 同上，兜住 {@code LivingDamageEvent}（最后一关）里被抹成 0 的伤害 */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onOwnerDamageUntamedShikigami(
            net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        ServerPlayer owner = untamedShikigamiOwnerAttacker(event.getEntity(), event.getSource());
        if (owner == null || event.getAmount() > 0.0F) return;
        float attack = (float) owner.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        event.setAmount(Math.max(1.0F, attack));
    }

    /** "主人打自己的未调伏式神"则返回该主人；其它情况（含攻击者自身被限制）返回 null */
    @javax.annotation.Nullable
    private static ServerPlayer untamedShikigamiOwnerAttacker(
            LivingEntity victim, net.minecraft.world.damagesource.DamageSource source) {
        if (!(victim instanceof ShikigamiMob sm) || sm.isTamed()) return null;
        if (sm.getOwnerId() == null) return null;
        if (!(source.getEntity() instanceof ServerPlayer sp)) return null;
        if (!sm.getOwnerId().equals(sp.getUUID())) return null;
        // 投射咒法·静止：攻击者被罚站（正常走 AttackEntityEvent，这里只做防御性排除）
        if (com.mofengbaizhi.tinkersnewlife.content.curse.StunHandler.isStunned(sp)) return null;
        // 伏诛赐死·亡灵有罪：攻击力归零是对攻击者的惩罚
        Long zeroUntil = com.mofengbaizhi.tinkersnewlife.content.curse.domain.ExecutionDomain
                .ATK_ZERO_UNTIL.get(sp.getUUID());
        if (zeroUntil != null && sp.getServer() != null && sp.getServer().getTickCount() < zeroUntil) return null;
        return sp;
    }
}
