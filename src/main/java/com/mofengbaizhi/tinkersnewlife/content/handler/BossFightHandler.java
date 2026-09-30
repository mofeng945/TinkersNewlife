package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>Boss 战支持</b>（§864 用户口径 ✓）：
 * <ol>
 *   <li><b>自动检测有多少玩家参与了这场 boss 战</b> ✓
 *       —— "参与"= <b>对 boss 造成过伤害</b>（宠物／弹射物算主人的 ✓）
 *          <b>或</b> <b>被 boss 打过</b>（含 boss 的法术／陷阱／狱云那种带主人的伤害 ✓）；</li>
 *   <li><b>结算时按参与人数 roll 奖励</b> ✓
 *       —— 总次数 = 参与人数 ✓（原版死亡时那次已经 roll 过 ✓ ⇒ 我们补 <b>N−1</b> 次 ✓），
 *          多出来的战利品**掉在地上**（大家分 ✓ 不是直接塞背包 ✓）；</li>
 *   <li><b>击杀 boss 的成就广播给所有参与者</b> ✓
 *       —— 给每个参与者重放原版的 {@code minecraft:player_killed_entity} 触发器 ✓
 *          ⇒ 所有用这个触发器的击杀成就都会发给他们 ✓
 *          （已核：诡厄的 {@code kill_a_apostle} 用的正是它 ✓ 原版 {@code end/kill_dragon} 也是 ✓），
 *          因为成就本身带 {@code announce_to_chat} ✓ ⇒ 聊天栏／toast 会广播出来 ✓。</li>
 * </ol>
 *
 * <h2>boss 怎么判定</h2>
 * 用实体类型标签 <b>{@code forge:bosses}</b> ✓ —— 各模组的 boss 都往这里挂 ✓
 * （已核实：诡厄 jar 里就有 {@code data/forge/tags/entity_types/bosses.json} 并含 {@code goety:apostle} 等 ✓）。
 * 这样不用写死任何一个 boss 的类名 ✗ 也不挑模组 ✓。
 *
 * <h2>配置</h2>
 * {@code config/mofengbaizhi/tinkersnewlife-common.toml} 的 {@code [boss_fight]} 段 ✓：
 * {@code enabled}（总开关 ✓ 默认开）、{@code rolls_equal_participants}（默认 true ✓）、
 * {@code grant_advancements_to_all}（默认 true ✓）、{@code max_extra_rolls}（额外次数上限 ✓ 0 = 不限 ✓）。
 *
 * <p>⚠ 边界（如实记录 ✓）：
 * <ul>
 *   <li>只补"<b>战利品表</b>"那部分 ✓ —— 某些模组 boss 在自己死亡逻辑里**直接给物品**（不走战利品表 ✗），
 *       那部分我们管不到 ✗；</li>
 *   <li>只认 <b>player_killed_entity</b> 这一类成就触发器 ✓ —— 自定义触发器的成就（如诡厄
 *       {@code goety:servant_killed_entity} 那一族"仆从击杀"）不在此列 ✗；</li>
 *   <li>参与者表按"这一场"记 ✓ —— boss 死亡结算后清掉 ✓；长时间没人打的记录会过期清理 ✓。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BossFightHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("TinkersNewlife/BossFight");

    /** {@code forge:bosses} ✓（各模组 boss 的公共标签 ✓） */
    private static final TagKey<EntityType<?>> FORGE_BOSSES =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("forge", "bosses"));

    /** 一段时间没人动这场战斗 ⇒ 记录过期（毫秒 ✓ 15 分钟 ✓） */
    private static final long STALE_MILLIS = 15L * 60L * 1000L;

    /** boss(UUID) → 这一场的参与者与最后活动时间 ✓ */
    private static final Map<UUID, Fight> FIGHTS = new ConcurrentHashMap<>();

    private BossFightHandler() {}

    /** 一场 boss 战的记录 ✓ */
    private static final class Fight {
        final Set<UUID> players = ConcurrentHashMap.newKeySet();
        volatile long lastSeen = System.currentTimeMillis();
    }

    /** 是不是 boss ✓（{@code forge:bosses} 标签 ✓ 取不到标签时按"不是"处理 ✓ 绝不抛 ✓） */
    public static boolean isBoss(Entity entity) {
        if (entity == null) return false;
        try {
            return entity.getType().is(FORGE_BOSSES);
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ============================================================
    //  参与者登记
    // ============================================================

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!ModConfig.bossFightEnabled()) return;
        if (event.getAmount() <= 0.0F) return;
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;

        DamageSource src = event.getSource();
        // ① boss 挨打 ⇒ 打它的那个玩家算参与者 ✓
        if (isBoss(victim)) {
            ServerPlayer attacker = asPlayer(src == null ? null : src.getEntity());
            if (attacker == null && src != null) attacker = asPlayer(src.getDirectEntity());
            if (attacker != null && attacker != victim) record(victim, attacker);
            return;
        }
        // ② 玩家挨 boss 的打 ⇒ 那个玩家也算参与者 ✓（肉盾／辅助不会因为没输出被漏掉 ✓）
        if (victim instanceof ServerPlayer player && src != null) {
            LivingEntity boss = asBoss(src.getEntity());
            if (boss == null) boss = asBoss(src.getDirectEntity());
            // ⚠ 只认"死者正是 boss 自己"的伤害 ✓ 不把 boss 的仆从（掠夺者等）算进来 ✗
            if (boss != null && boss != player) record(boss, player);
        }
    }

    /** 伤害来源里能不能抠出一个玩家 ✓（本体 ✓ 宠物／弹射物按主人算 ✓） */
    private static ServerPlayer asPlayer(Entity entity) {
        if (entity instanceof ServerPlayer player) return player;
        if (entity instanceof OwnableEntity owned
                && owned.getOwner() instanceof ServerPlayer player) {
            return player;                                   // 宠物／召唤物／弹射物 ⇒ 算主人 ✓
        }
        return null;
    }

    /** 伤害来源里能不能抠出一个 boss（本体或"主人是 boss"的实体 ✓ 狱云／爆燃陷阱／箭都算 ✓） */
    private static LivingEntity asBoss(Entity entity) {
        if (isBoss(entity)) return entity instanceof LivingEntity le ? le : null;
        if (entity instanceof OwnableEntity owned) {
            Entity owner = owned.getOwner();
            if (isBoss(owner)) return owner instanceof LivingEntity le ? le : null;
        }
        return null;
    }

    private static void record(LivingEntity boss, ServerPlayer player) {
        Fight fight = FIGHTS.computeIfAbsent(boss.getUUID(), k -> new Fight());
        fight.players.add(player.getUUID());
        fight.lastSeen = System.currentTimeMillis();
    }

    // ============================================================
    //  结算一：击杀成就广播给所有参与者
    // ============================================================

    /**
     * boss 死亡 ⇒ 给**每一个参与者**重放原版 {@code minecraft:player_killed_entity} 触发器 ✓
     * ⇒ 所有以"击杀某个实体"为条件的成就（含各模组自带的）都会发给他们 ✓。
     * <p>⚠ 只给**这场战斗的参与者** ✓ 不是全服广播 ✗（用户口径是"参与战斗的玩家"✓）。
     */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!ModConfig.bossFightEnabled() || !ModConfig.bossFightGrantAdvancements()) return;
        LivingEntity boss = event.getEntity();
        if (!isBoss(boss)) return;
        if (boss.level().isClientSide) return;
        Fight fight = FIGHTS.get(boss.getUUID());
        if (fight == null || fight.players.isEmpty()) return;
        MinecraftServer server = boss.getServer();
        if (server == null) return;

        int granted = 0;
        for (UUID id : fight.players) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) continue;                     // 已下线的就跳过 ✓（没地方发 ✗）
            try {
                CriteriaTriggers.PLAYER_KILLED_ENTITY.trigger(player, boss, event.getSource());
                granted++;
            } catch (Throwable ignored) {
            }
        }
        if (granted > 0) {
            LOGGER.info("[Boss战] {} 被击杀 ⇒ 已给 {} 名参与者重放击杀成就触发器 ✓",
                    boss.getName().getString(), granted);
        }
    }

    // ============================================================
    //  结算二：按参与人数补 roll 战利品
    // ============================================================

    /**
     * boss 掉落结算 ⇒ 额外 roll（参与人数 − 1）次 ✓（原版那一次已经 roll 过了 ✓），
     * 战利品**掉在地上** ✓。
     * <p>⚠ 用 {@link LivingDropsEvent} 而不是自己在死亡事件里刷物品 ✓：
     * 这样会尊重别的模组对这个事件的取消／修改 ✓ 也自然只在"确实会掉落"时发生 ✓。
     */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity boss = event.getEntity();
        // ⚠ 先取出并移除记录 ✓ ⇒ 同一个 boss 只结算一次 ✓（哪怕这个事件被别的模组重复触达 ✓）
        Fight fight = FIGHTS.remove(boss.getUUID());
        if (!ModConfig.bossFightEnabled() || !ModConfig.bossFightRollsEqualParticipants()) return;
        if (fight == null || !isBoss(boss) || boss.level().isClientSide) return;
        int extra = fight.players.size() - 1;                  // 原版那次算 1 ✓
        if (extra <= 0) return;
        int cap = ModConfig.bossFightMaxExtraRolls();
        if (cap > 0) extra = Math.min(extra, cap);

        MinecraftServer server = boss.getServer();
        if (server == null) return;
        ResourceLocation tableId = boss.getLootTable();
        if (tableId == null || tableId.equals(LootTable.EMPTY)) return;
        LootTable table = server.getLootData().getLootTable(tableId);
        if (table == null || table == LootTable.EMPTY) return;
        ServerLevel level = (ServerLevel) boss.level();

        // 与原生 LivingEntity#dropFromLootTable（`LivingEntity.java:1402-1412` ✓ 已核反编译 ✓）同一套上下文 ✓
        //   ⇒ killed_by_player 之类的条件照旧成立 ✓
        DamageSource src = event.getSource() == null
                ? boss.damageSources().generic() : event.getSource();
        net.minecraft.world.level.storage.loot.LootParams.Builder builder =
                new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                        .withParameter(LootContextParams.THIS_ENTITY, boss)
                        .withParameter(LootContextParams.ORIGIN, boss.position())
                        .withParameter(LootContextParams.DAMAGE_SOURCE, src)
                        .withOptionalParameter(LootContextParams.KILLER_ENTITY, src.getEntity())
                        .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, src.getDirectEntity());
        if (boss.getKillCredit() instanceof ServerPlayer killer) {
            builder = builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
                    .withLuck(killer.getLuck());
        }
        net.minecraft.world.level.storage.loot.LootParams params =
                builder.create(LootContextParamSets.ENTITY);

        int rolled = 0;
        for (int i = 0; i < extra; i++) {
            try {
                for (ItemStack stack : table.getRandomItems(params)) {
                    if (stack.isEmpty()) continue;
                    ItemEntity drop = new ItemEntity(level,
                            boss.getX(), boss.getY() + 0.5D, boss.getZ(), stack);
                    drop.setDefaultPickUpDelay();
                    event.getDrops().add(drop);                // ✅ 与原生掉落一起落地 ✓
                }
                rolled++;
            } catch (Throwable ignored) {
            }
        }
        if (rolled > 0) {
            LOGGER.info("[Boss战] {} 的掉落 ⇒ {} 名参与者 ⇒ 额外 roll {} 次 ✓（总次数 = 参与人数 ✓）",
                    boss.getName().getString(), fight.players.size(), rolled);
        }
    }

    // ============================================================
    //  过期清理（长时间没人打的那场记录别留在表里 ✗）
    // ============================================================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null || server.getTickCount() % 600 != 0) return;
        if (FIGHTS.isEmpty()) return;
        long now = System.currentTimeMillis();
        FIGHTS.entrySet().removeIf(entry -> now - entry.getValue().lastSeen > STALE_MILLIS);
    }
}
