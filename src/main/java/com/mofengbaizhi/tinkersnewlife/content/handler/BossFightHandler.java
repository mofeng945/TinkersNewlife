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
    //  §865 走"箱子结算"的 boss（暮色森林那一类）
    // ============================================================

    /*
     * 暮色森林的 boss 不掉地上 ✓ 而是把战利品塞进一个箱子（§865 用户指出 ✓ 已核反编译 ✓）：
     *   * `IBossLootBuffer`（`twilightforest.entity.boss.IBossLootBuffer` ✓ 缓冲 27 格 ＝ 一个箱子 ✓）：
     *     - 死亡时 `saveDropsIntoBoss()`（`IBossLootBuffer.java:76-90` ✓）roll **一次**战利品表填进缓冲 ✓；
     *     - 尸体 `remove(KILLED)` 时 `depositDropsIntoChest()`（`:92-102` ✓）在 `EntityUtil.bossChestLocation(尸体)`
     *       （= 竞技场限制点下方 ✓ 拿不到就用尸体位置 ✓ `EntityUtil.java:116-118` ✓）放箱子并把 27 格抄进箱子 ✓；
     *     - 同时 `shouldDropLoot()` 被覆写成"开箱子模式时返回 false"（`Naga.java:654-656` ✓）
     *       ⇒ **原版地上掉落那套对它们根本不发生** ✗ ⇒ §864 的 `LivingDropsEvent` 补 roll 对暮色 boss **完全无效** ✗。
     *   * ⚠ 而且**暮色自己就有"箱子满了"的坑** ✗：`fill()`（`:104-119` ✓）只往空位塞 ✓ 塞不下的**直接丢掉** ✗
     *     （`saveDropsIntoBoss` 里那个"超出 27 就掉地上"的分支只看**原始** roll ✓ 还从索引 28 开始
     *      ⇒ **索引 27 那一件也会丢** ✗）。
     *
     * ⇒ 本补丁对这类 boss 的做法（回答用户的问题「箱子满了怎么办」✓）：
     *   死亡后**下一 tick**（此时暮色的 `saveDropsIntoBoss` 已经跑完 ✓ 而尸体还没 `remove` ⇒ 箱子还没放 ✓）
     *   把多出来的 N−1 次 roll **塞进缓冲的空位** ✓；**空位也没了 ⇒ 掉在"箱子将要出现的位置"旁边** ✓
     *   —— <b>一件都不丢</b> ✓（比暮色本身更保险 ✓）。
     *
     * ⚠ TF 不在编译依赖里 ✗ ⇒ 全程**反射** + 缓存 + 全程 try/catch ✓（没装暮色时整段静默跳过 ✓）。
     */

    private static boolean tfResolved = false;
    private static Class<?> tfLootBuffer;
    private static java.lang.reflect.Method tfGetItemStacks;
    private static java.lang.reflect.Method tfBossChestLocation;

    private static void resolveTwilight() {
        if (tfResolved) return;
        tfResolved = true;
        try {
            tfLootBuffer = Class.forName("twilightforest.entity.boss.IBossLootBuffer");
            tfGetItemStacks = tfLootBuffer.getMethod("getItemStacks");
        } catch (Throwable ignored) {
            tfLootBuffer = null;
            tfGetItemStacks = null;
        }
        try {
            Class<?> util = Class.forName("twilightforest.util.EntityUtil");
            tfBossChestLocation = util.getMethod("bossChestLocation", net.minecraft.world.entity.Mob.class);
        } catch (Throwable ignored) {
            tfBossChestLocation = null;
        }
    }

    /** 这个 boss 是不是"把战利品塞箱子"那种（暮色 ✓） */
    private static boolean isChestSettlementBoss(Entity entity) {
        if (entity == null) return false;
        resolveTwilight();
        if (tfLootBuffer == null) return false;
        try {
            return tfLootBuffer.isInstance(entity);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 待补 roll 的"箱子结算"任务 ✓（死亡后下一 tick 执行 ✓） */
    private static final class ChestTopUp {
        final LivingEntity boss;
        final Set<UUID> players;
        final DamageSource source;
        final long dueTick;

        ChestTopUp(LivingEntity boss, Set<UUID> players, DamageSource source, long dueTick) {
            this.boss = boss;
            this.players = players;
            this.source = source;
            this.dueTick = dueTick;
        }
    }

    private static final Map<UUID, ChestTopUp> PENDING_TOPUP = new ConcurrentHashMap<>();

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
        if (!ModConfig.bossFightEnabled()) return;
        LivingEntity boss = event.getEntity();
        if (!isBoss(boss)) return;
        if (boss.level().isClientSide) return;
        Fight fight = FIGHTS.get(boss.getUUID());
        if (fight == null || fight.players.isEmpty()) return;
        MinecraftServer server = boss.getServer();
        if (server == null) return;

        // ① 击杀成就广播给所有参与者 ✓
        if (ModConfig.bossFightGrantAdvancements()) {
            int granted = 0;
            for (UUID id : fight.players) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player == null) continue;                 // 已下线的就跳过 ✓（没地方发 ✗）
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

        // ② §865 暮色那种"塞箱子"的 boss ⇒ 排一个"下一 tick 补 roll"的任务 ✓
        //    （⚠ 必须等下一 tick：本方法还在 `LivingEntity#die()` 里面 ⇒ 暮色的
        //      `saveDropsIntoBoss()`（它自己那次 roll）此时**还没跑** ✗ 抢跑会被它覆盖 ✗；
        //      而尸体 `remove(KILLED)`（放箱子）又要晚得多 ✓）
        if (isChestSettlementBoss(boss) && ModConfig.bossFightRollsEqualParticipants()) {
            PENDING_TOPUP.put(boss.getUUID(), new ChestTopUp(boss, new java.util.HashSet<>(fight.players),
                    event.getSource(), server.getTickCount() + 1L));
            LOGGER.info("[Boss战] {} 走箱子结算（暮色那一类）⇒ 已排队：下一 tick 按 {} 名参与者补 roll ✓",
                    boss.getName().getString(), fight.players.size());
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
        // §865：走箱子结算的 boss（暮色）⇒ 战利品根本不走地上掉落 ✗（`shouldDropLoot()` 被关掉 ✓）
        //   由 `onDeath` 排的 {@link #PENDING_TOPUP} 那套补 ✓ 这里必须直接跳过 ✗ 否则会重复给 ✗
        if (isChestSettlementBoss(boss)) return;
        if (!ModConfig.bossFightEnabled() || !ModConfig.bossFightRollsEqualParticipants()) return;
        if (fight == null || !isBoss(boss) || boss.level().isClientSide) return;
        int extra = fight.players.size() - 1;                  // 原版那次算 1 ✓
        if (extra <= 0) return;
        int cap = ModConfig.bossFightMaxExtraRolls();
        if (cap > 0) extra = Math.min(extra, cap);

        MinecraftServer server = boss.getServer();
        if (server == null) return;
        LootTable table = lootTableOf(server, boss);
        if (table == null) return;
        ServerLevel level = (ServerLevel) boss.level();

        // 与原生 LivingEntity#dropFromLootTable（`LivingEntity.java:1402-1412` ✓ 已核反编译 ✓）同一套上下文 ✓
        //   ⇒ killed_by_player 之类的条件照旧成立 ✓
        net.minecraft.world.level.storage.loot.LootParams params =
                buildLootParams(level, boss, event.getSource());

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
    //  §865 箱子结算：补 roll（塞箱子，塞不下掉箱子旁）
    // ============================================================

    /** 取这个 boss 的战利品表（取不到 ⇒ null ✓） */
    private static LootTable lootTableOf(MinecraftServer server, LivingEntity boss) {
        try {
            ResourceLocation id = boss.getLootTable();
            if (id == null || id.equals(LootTable.EMPTY)) return null;
            LootTable table = server.getLootData().getLootTable(id);
            return (table == null || table == LootTable.EMPTY) ? null : table;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 与原生 {@code LivingEntity#dropFromLootTable} 同一套掉宝上下文 ✓（两条路径共用 ✓） */
    private static net.minecraft.world.level.storage.loot.LootParams buildLootParams(
            ServerLevel level, LivingEntity boss, DamageSource srcOrNull) {
        DamageSource src = srcOrNull == null ? boss.damageSources().generic() : srcOrNull;
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
        return builder.create(LootContextParamSets.ENTITY);
    }

    /** 这个战利品缓冲里第一个空槽（没有 ⇒ -1 ✓） */
    private static int firstEmptySlot(java.util.List<ItemStack> slots) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack in = slots.get(i);
            if (in == null || in.isEmpty()) return i;
        }
        return -1;
    }

    /**
     * 执行一次"箱子结算"补 roll ✓：
     * 多出来的 N−1 次 roll ⇒ 先塞进 boss 的战利品缓冲空位 ✓；
     * **空位也没了 ⇒ 掉在箱子将要出现的位置旁边** ✓（一件都不丢 ✓ —— 回应"箱子满了怎么办"✓）。
     */
    private static void runChestTopUp(MinecraftServer server, ChestTopUp task) {
        LivingEntity boss = task.boss;
        if (boss == null) return;
        if (boss.isRemoved()) {                                // 尸体已经被移除 ⇒ 箱子已经放过了 ✗ 来不及了
            LOGGER.warn("[Boss战] {} 的箱子结算补 roll 来不及（尸体已移除）✗ —— 下次提升到更早的 tick 即可 ✓",
                    boss.getName().getString());
            return;
        }
        if (!(boss.level() instanceof ServerLevel level)) return;

        java.util.List<ItemStack> slots;
        try {
            //noinspection unchecked
            slots = (java.util.List<ItemStack>) tfGetItemStacks.invoke(boss);
        } catch (Throwable ignored) {
            return;
        }
        if (slots == null) return;

        LootTable table = lootTableOf(server, boss);
        if (table == null) return;
        int extra = task.players.size() - 1;
        if (extra <= 0) return;
        int cap = ModConfig.bossFightMaxExtraRolls();
        if (cap > 0) extra = Math.min(extra, cap);

        // 箱子位置（暮色：竞技场限制点下方／拿不到就用尸体位置 ✓）
        net.minecraft.core.BlockPos chestPos = boss.blockPosition();
        try {
            Object pos = tfBossChestLocation.invoke(null, boss);
            if (pos instanceof net.minecraft.core.BlockPos bp) chestPos = bp;
        } catch (Throwable ignored) {
        }

        net.minecraft.world.level.storage.loot.LootParams params =
                buildLootParams(level, boss, task.source);
        int intoChest = 0;
        int onGround = 0;
        for (int i = 0; i < extra; i++) {
            try {
                for (ItemStack stack : table.getRandomItems(params)) {
                    if (stack.isEmpty()) continue;
                    int free = firstEmptySlot(slots);
                    if (free >= 0) {
                        slots.set(free, stack);                // ✅ 塞进缓冲 ⇒ 暮色放箱子时会一并抄进去 ✓
                        intoChest++;
                    } else {
                        // 🔴 箱子（27 格）满了 ⇒ **掉在箱子旁边** ✓ 绝不静默丢 ✗
                        ItemEntity drop = new ItemEntity(level,
                                chestPos.getX() + 0.5D, chestPos.getY() + 1.0D, chestPos.getZ() + 0.5D, stack);
                        drop.setDefaultPickUpDelay();
                        level.addFreshEntity(drop);
                        onGround++;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        LOGGER.info("[Boss战] {} 走箱子结算 ⇒ {} 名参与者 ⇒ 补 roll {} 次：塞进箱子 {} 件 ✓；箱子放不下 ⇒ 掉在箱子旁 {} 件 ✓",
                boss.getName().getString(), task.players.size(), extra, intoChest, onGround);
    }

    // ============================================================
    //  过期清理（长时间没人打的那场记录别留在表里 ✗）
    // ============================================================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;

        // §865：箱子结算的补 roll（每 tick 都看一眼 ✓ 只在有排队任务时才做事 ✓）
        if (!PENDING_TOPUP.isEmpty()) {
            long now = server.getTickCount();
            for (UUID id : new java.util.ArrayList<>(PENDING_TOPUP.keySet())) {
                ChestTopUp task = PENDING_TOPUP.get(id);
                if (task == null || task.dueTick > now) continue;
                PENDING_TOPUP.remove(id);
                try {
                    runChestTopUp(server, task);
                } catch (Throwable t) {
                    LOGGER.warn("[Boss战] 箱子结算补 roll 出错：{}", t.toString());
                }
            }
        }

        if (server.getTickCount() % 600 != 0) return;
        if (FIGHTS.isEmpty()) return;
        long now = System.currentTimeMillis();
        FIGHTS.entrySet().removeIf(entry -> now - entry.getValue().lastSeen > STALE_MILLIS);
    }
}
