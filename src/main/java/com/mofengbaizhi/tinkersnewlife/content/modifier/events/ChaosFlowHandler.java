package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.ChaosFlowModifier;
import com.mofengbaizhi.tinkersnewlife.integration.irons_spellbooks.IronSpellsSpellAccess;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.List;

/**
 * 特性「<b>混沌之流</b>」结算器（铁魔法联动，见 {@link ChaosFlowModifier}）。
 *
 * <p>做法：在 {@code LivingHurtEvent}（{@link EventPriority#LOWEST}，即别人都改完之后）里，
 * 如果这次伤害来自"带混沌之流的近战 / 弹射工具"：
 * <ol>
 *   <li><b>取消原始那一次</b> ✓；</li>
 *   <li>把总伤害均分成 <b>1 + 学派数</b> 段，先施加<b>一段物理</b>，
 *       再为<b>每个学派</b>各施加一段该学派的法术伤害 ✓；</li>
 *   <li>每段各按自己的伤害类型结算 → 目标的各项抗性分别生效 ✓（这正是这个特性的意义）。</li>
 * </ol>
 *
 * <h2>⭐ 为什么要有"学派分段上限"（性能，2026-09-17 用户实测）</h2>
 * 用户整合包里装了<b>法术反应</b>类附属模组：它注册了<b>大量学派</b>，而且学派之间互相反应 ✗ ——
 * 而本特性原本是"1 + **全部**学派数"段 ⇒ 一刀就是几十次 {@code hurt()}，
 * 每一次都会触发一轮学派反应结算 ⇒ <b>源钻合金每次攻击都卡一下</b> ✗。
 *
 * <p>⭐ 但**段数不能砍** —— 用户指出："我混沌之流重要的是**伤害类型**不是段数啊" ✓
 * （这个特性的意义就是"每个学派的伤害类型各按自己的抗性结算" ✗ ⇒ 少打几种就变味了 ✗）。
 * 所以对策是<b>把同一串摊到后面若干 tick</b>（{@code segments_per_tick}，默认 4 ✓）：
 * <b>伤害类型一个不少 ✓、总伤害不变 ✓、单帧只跑几段 ⇒ 不再卡帧 ✓</b>。
 * 另外：学派列表<b>缓存</b>（30 秒 ✓）、可整体关闭 ✓、{@code max_school_segments} 只作
 * "学派爆炸"时的硬保险（默认 64 ≈ 不限 ✓）。
 *
 * <h2>为什么学派是"动态"的</h2>
 * 学分数从铁魔法的<b>学派注册表</b>里现取（{@code SchoolRegistry}#getAllSchools），
 * 每个学派自带 {@code getDamageType()} ✓ —— 所以<b>附属模组新加的学派也自动算进来</b> ✓，
 * 不需要我们维护一张表 ✗。例：默认 9 个学派 → 共 10 段。
 *
 * <h2>几个必须处理的坑</h2>
 * <ul>
 *   <li><b>递归</b>：我们施加的每一段又会触发 {@code LivingHurtEvent} ✗ → 用
 *       {@link ThreadLocal} 标记把重入挡掉 ✓（我们施加的多段伤害<b>不再被拆分</b> ✓）；</li>
 *   <li><b>无敌帧</b>：第二段开始会被目标的 {@code invulnerableTime} 吃掉 ✗ →
 *       每段前后手动清零 ✓，结束时恢复成原版命中后的 20 tick ✓，所以"挨一下的无敌时间"手感不变 ✓；</li>
 *   <li><b>已死目标</b>：中途死去时后续段自然落空 ✓（不特殊处理）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChaosFlowHandler {

    private ChaosFlowHandler() {
    }

    /** 拆分中标记：防止我们施加的分段伤害又被自己拆一遍 */
    private static final ThreadLocal<Boolean> SPLITTING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (Boolean.TRUE.equals(SPLITTING.get())) return;

        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;

        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof LivingEntity attacker)) return;
        if (attacker == target) return;

        ToolStack tool = ToolHelper.getCombatToolWith(source, attacker, ChaosFlowModifier.ID);
        if (tool == null || tool.getModifierLevel(ChaosFlowModifier.ID) <= 0) {
            logDiagnostic(attacker, tool);                       // 手里有匠魂工具但没这个特性 → 记一笔 ✓
            return;
        }

        float total = event.getAmount();
        if (total <= 0.0F) return;

        // ⭐ 配置：可整体关闭；学派段上限（默认 64 ≈ 不限，只作"学派爆炸"的硬保险）；每 tick 限量几段 ✓
        if (!com.mofengbaizhi.tinkersnewlife.config.ModConfig.CHAOS_FLOW_ENABLED.get()) return;
        int cap = maxSchoolSegments();
        if (cap <= 0) return;                                    // 上限 0 = 只用物理那一段 = 等价于不拆 ✓

        List<ResourceKey<DamageType>> allSchools = schoolKeysCached();
        if (allSchools.isEmpty()) {
            logOnce("[混沌之流] 学派注册表为空（铁魔法不在场或反射失败）→ 本次不拆分");
            return;
        }
        // ⭐ <b>不丢伤害类型</b>：默认上限 64 ⇒ 学派**全部**参与 ✓
        //    （用户 2026-09-17 指出："我混沌之流重要的是伤害类型不是段数啊" ⇒ 不能靠"只取前 N 个学派"省开销 ✗）
        List<ResourceKey<DamageType>> schoolKeys = allSchools.size() > cap
                ? allSchools.subList(0, cap) : allSchools;

        int segments = 1 + schoolKeys.size();
        float per = total / segments;
        if (per <= 0.0F) return;

        event.setCanceled(true);                                 // 原始那一次不再结算 ✓

        int perTick = segmentsPerTick();
        if (DEBUG) TinkersNewlife.LOGGER.info("[混沌之流] {} 的 {} 点伤害拆成 {} 段（每段 {}，学派 {} 个，每 tick 最多 {} 段）",
                attacker.getName().getString(), total, segments, per, schoolKeys.size(), perTick);

        // ① 物理段：同 tick 立即结算 ✓
        creditKill(target, attacker);                            // ⭐ 先补击杀归属（关系到 killed_by_player 类战利品 ✓）
        SPLITTING.set(Boolean.TRUE);
        try {
            target.invulnerableTime = 0;
            target.hurt(physicalSource(target, attacker), per);
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.debug("[混沌之流] 物理段失败（已忽略）: {}", t.toString());
        } finally {
            SPLITTING.set(Boolean.FALSE);
        }

        // ② 学派段：段数不多（≤ 16 段，普通整合包 ≈ 9 学派 ✓）⇒ 同 tick 全打完，行为与以前完全一致 ✓；
        //    段数多（学派爆炸的包）⇒ 摊到后面若干 tick，每 tick 只发 perTick 段 ⇒ 单帧不再卡 ✓
        if (perTick <= 0 || schoolKeys.size() <= BURST_LIMIT) {
            SPLITTING.set(Boolean.TRUE);
            try {
                for (ResourceKey<DamageType> key : schoolKeys) {
                    if (target.isDeadOrDying()) break;
                    DamageSource schoolSource = schoolSource(target, attacker, key);
                    if (schoolSource == null) continue;
                    creditKill(target, attacker);                 // ⭐ 每段前都补一次 ✓
                    target.invulnerableTime = 0;                  // 每段都清无敌帧
                    target.hurt(schoolSource, per);
                }
                target.invulnerableTime = 20;                     // 恢复成原版命中后的无敌时间 ✓
            } catch (Throwable t) {
                TinkersNewlife.LOGGER.debug("[混沌之流] 分段失败（已忽略）: {}", t.toString());
            } finally {
                SPLITTING.set(Boolean.FALSE);
            }
            return;
        }
        enqueue(target, attacker, per, schoolKeys);
    }

    // ============================================================
    //  ⭐ 学派段的"每 tick 限量发放"（不丢伤害类型，只摊开时间）
    // ============================================================

    /**
     * 一"串"待发放的学派段。
     *
     * <p>为什么需要它：学派爆炸的整合包里，"1 段物理 + 全部学派"这种打法在**同一 tick** 里
     * 要跑几十次完整 {@code hurt()}（每条伤害管线 + 全模组监听）⇒ 单帧直接卡住 ✗。
     * 而段数本身**不能砍**（用户："我混沌之流重要的是伤害类型不是段数啊" ✗）——
     * 所以改成把同一串摊到后面若干 tick：**类型一个不少 ✓、总伤害不变 ✓、单帧只跑几段 ✓**。
     */
    private static final class Flurry {
        final java.util.UUID targetId;
        final java.util.UUID attackerId;
        final float per;
        final List<ResourceKey<DamageType>> keys;
        int index;

        Flurry(java.util.UUID targetId, java.util.UUID attackerId, float per, List<ResourceKey<DamageType>> keys) {
            this.targetId = targetId;
            this.attackerId = attackerId;
            this.per = per;
            this.keys = keys;
        }
    }

    /** 每维度一条待发放队列（key = 维度） */
    private static final java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>,
            List<Flurry>> QUEUES = new java.util.concurrent.ConcurrentHashMap<>();

    /** 段数不超过这个值时仍然"同 tick 打完"（普通整合包 ≈ 9 学派 ⇒ 行为与以前完全一致 ✓） */
    private static final int BURST_LIMIT = 16;

    private static void enqueue(LivingEntity target, LivingEntity attacker, float per,
                                List<ResourceKey<DamageType>> keys) {
        if (!(target.level() instanceof ServerLevel sl)) return;
        QUEUES.computeIfAbsent(sl.dimension(), k -> new java.util.ArrayList<>())
                .add(new Flurry(target.getUUID(), attacker.getUUID(), per, List.copyOf(keys)));
    }

    /**
     * 每 tick 发放：**全局**（整个维度）最多 {@code segments_per_tick} 段 ✓ ——
     * 这样无论同时有多少串、或玩家疯狂点，单帧的伤害管线开销都有硬上限 ✓。
     */
    @SubscribeEvent
    public static void onLevelTick(net.minecraftforge.event.TickEvent.LevelTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (event.level.isClientSide) return;
        if (!(event.level instanceof ServerLevel serverLevel)) return;

        List<Flurry> queue = QUEUES.get(serverLevel.dimension());
        if (queue == null || queue.isEmpty()) return;

        int budget = Math.max(1, segmentsPerTick());
        SPLITTING.set(Boolean.TRUE);
        try {
            java.util.Iterator<Flurry> it = queue.iterator();
            while (it.hasNext() && budget > 0) {
                Flurry f = it.next();
                net.minecraft.world.entity.Entity te = serverLevel.getEntity(f.targetId);
                net.minecraft.world.entity.Entity ae = serverLevel.getEntity(f.attackerId);
                if (!(te instanceof LivingEntity target) || target.isRemoved() || target.isDeadOrDying()
                        || !(ae instanceof LivingEntity attacker)) {
                    if (te instanceof LivingEntity gone) gone.invulnerableTime = 20;
                    it.remove();
                    continue;
                }
                while (f.index < f.keys.size() && budget > 0) {
                    DamageSource src = schoolSource(target, attacker, f.keys.get(f.index++));
                    if (src == null) continue;
                    creditKill(target, attacker);                // ⭐ 摊开路径每段前也补一次 ✓
                    target.invulnerableTime = 0;
                    target.hurt(src, f.per);
                    budget--;
                    if (target.isDeadOrDying()) break;
                }
                if (f.index >= f.keys.size() || target.isDeadOrDying()) {
                    target.invulnerableTime = 20;                    // 收尾恢复原版无敌时间 ✓
                    it.remove();
                } else {
                    // ⭐ 这一 tick 没发完 ⇒ 也先把无敌帧恢复成原版的 20 ✓
                    //    （否则"摊开的这段时间"里目标等于没有受击间隔，别人可以随便打 ✗）
                    target.invulnerableTime = 20;
                }
            }
            if (queue.isEmpty()) QUEUES.remove(serverLevel.dimension());
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.debug("[混沌之流] 分段队列结算失败（已忽略）: {}", t.toString());
        } finally {
            SPLITTING.set(Boolean.FALSE);
        }
    }

    /** 每 tick 最多发放几段（读配置；异常退回默认 4 ✓ —— 性能兜底不能因为配置异常而失效 ✗） */
    private static int segmentsPerTick() {
        try {
            return com.mofengbaizhi.tinkersnewlife.config.ModConfig.CHAOS_FLOW_SEGMENTS_PER_TICK.get();
        } catch (Throwable ignored) {
            return 4;
        }
    }

    // ============================================================
    //  配置 / 学派缓存
    // ============================================================

    /** 学派列表缓存时长（毫秒）：注册表很大时"每刀遍历一遍注册表"本身也是开销 ✗ */
    private static final long SCHOOL_CACHE_MS = 30_000L;
    private static volatile List<ResourceKey<DamageType>> cachedSchools = List.of();
    private static volatile long cachedSchoolsAt = 0L;

    /** 学派列表（带 30 秒缓存 ✓；数据包重载后最多 30 秒跟上 ✓） */
    private static List<ResourceKey<DamageType>> schoolKeysCached() {
        long now = System.currentTimeMillis();
        List<ResourceKey<DamageType>> cached = cachedSchools;
        if (!cached.isEmpty() && now - cachedSchoolsAt < SCHOOL_CACHE_MS) return cached;
        List<ResourceKey<DamageType>> fresh = IronSpellsSpellAccess.schoolDamageKeys();
        if (!fresh.isEmpty()) {
            cachedSchools = fresh;
            cachedSchoolsAt = now;
        }
        return fresh;
    }

    /** 学派段上限（读配置；出任何问题退回默认 64 ≈ 不限 ✓ —— 性能兜底绝不能因为配置异常而失效 ✗） */
    private static int maxSchoolSegments() {
        try {
            return Math.max(0, com.mofengbaizhi.tinkersnewlife.config.ModConfig
                    .CHAOS_FLOW_MAX_SCHOOL_SEGMENTS.get());
        } catch (Throwable ignored) {
            return 64;
        }
    }

    // ============================================================
    //  诊断（排查"为什么只看到一段伤害"用；限流，不刷屏）
    // ============================================================

    /** 诊断日志开关（排查"为什么只看到一段伤害"时改 true ✓；平时保持 false 不刷屏） */
    private static final boolean DEBUG = false;
    private static volatile long lastLogTime = 0L;

    /** 手里拿着匠魂工具、但这个工具上没有「混沌之流」→ 记一笔（5 秒最多一条） */
    private static void logDiagnostic(LivingEntity attacker, ToolStack tool) {
        if (!DEBUG) return;
        if (tool == null) return;
        long now = System.currentTimeMillis();
        if (now - lastLogTime < 5000L) return;
        lastLogTime = now;
        TinkersNewlife.LOGGER.info("[混沌之流] 攻击者 {} 手持 {} 但没有该特性（工具等级 0）→ 未拆分",
                attacker.getName().getString(), tool.getItem());
    }

    private static void logOnce(String message) {
        if (!DEBUG) return;
        long now = System.currentTimeMillis();
        if (now - lastLogTime < 5000L) return;
        lastLogTime = now;
        TinkersNewlife.LOGGER.info(message);
    }

    // ============================================================
    //  伤害源
    // ============================================================

    /** 物理段：**玩家攻击就用"玩家攻击"类型** ✓（原来一律 mobAttack ⇒ 玩家的击杀被标成"生物攻击" ✗，可能丢掉 killed_by_player 类战利品条件 ✗） */
    private static DamageSource physicalSource(LivingEntity target, LivingEntity attacker) {
        Holder<DamageType> holder;
        if (attacker instanceof net.minecraft.world.entity.player.Player player) {
            holder = target.damageSources().playerAttack(player).typeHolder();
        } else {
            holder = target.damageSources().mobAttack(attacker).typeHolder();
        }
        return new DamageSource(holder, attacker, attacker);
    }

    /**
     * 显式把"击杀归属"写给目标 ✓。
     *
     * <p>为什么需要：混沌之流是**嵌套 hurt**（在 {@code LivingHurtEvent} 里取消原始伤害、再自己打几段 ✓），
     * 而不少模组的战利品表带 {@code killed_by_player} 之类条件 ✓ —— 显式补一次归属，
     * 让"这一刀是玩家打的"在死亡结算时一定成立 ✓（用户实测：开了混沌之流后打怪不掉东西 ✗）。
     */
    private static void creditKill(LivingEntity target, LivingEntity attacker) {
        try {
            target.setLastHurtByMob(attacker);
            if (attacker instanceof net.minecraft.world.entity.player.Player player) {
                target.setLastHurtByPlayer(player);
            }
        } catch (Throwable ignored) {
            // 补不上也不影响伤害结算 ✓
        }
    }

    /** 学派段：用该学派的法术伤害类型（{@code SchoolType#getDamageType()}）✓ */
    private static DamageSource schoolSource(LivingEntity target, LivingEntity attacker, ResourceKey<DamageType> key) {
        try {
            if (!(target.level() instanceof ServerLevel serverLevel)) return null;
            Holder<DamageType> holder = serverLevel.registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key);
            return new DamageSource(holder, attacker, attacker);
        } catch (Throwable t) {
            return null;
        }
    }
}
