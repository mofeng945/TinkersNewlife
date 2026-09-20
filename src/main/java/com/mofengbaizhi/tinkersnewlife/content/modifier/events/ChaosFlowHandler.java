package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.ChaosFlowModifier;
import com.mofengbaizhi.tinkersnewlife.integration.irons_spellbooks.IronSpellsSpellAccess;
import com.mofengbaizhi.tinkersnewlife.util.DamagePipeline;
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
 *   <li><b>递归</b>：我们施加的每一段又会触发 {@code LivingHurtEvent} ✗ →
 *       用 {@link DamagePipeline} 的<b>同线程嵌套标记</b>把重入挡掉 ✓
 *       （我们施加的多段伤害<b>不再被拆分</b>、也<b>不再被我们自己的放大器逐段放大</b> ✓）；</li>
 *   <li><b>无敌帧</b>：第二段开始会被目标的 {@code invulnerableTime} 吃掉 ✗ →
 *       每段前后手动清零 ✓，结束时恢复成原版命中后的 20 tick ✓，所以"挨一下的无敌时间"手感不变 ✓；</li>
 *   <li><b>已死目标</b>：中途死去时后续段自然落空 ✓（不特殊处理）。</li>
 * </ul>
 *
 * <h2>⭐⭐ 2026-09-19 用户报告：附加伤害被拆段 + 每段再吃一遍增幅 ⇒ 数值爆炸</h2>
 *
 * 用户原话：「其他特性或饰品效果的<b>附加伤害</b>也会被混沌之流拆成多段伤害，
 * 而且<b>每一段伤害还会吃到饰品或其他特性的增幅</b>，导致出现极大数值膨胀」✓。
 *
 * <p>拆成两个独立缺陷（都在这一条链路上）：
 * <ol>
 *   <li><b>附加伤害被一起拆段</b>：本处理器在 {@code LOWEST} 读到的 {@code amount} 是
 *       "**上游全部处理器（放大类 + 附加类）改完之后**的值"，旧实现把它**当成基数**均分 ✗
 *       ⇒ 别人加进来的那部分也被摊成 N 份 ✓（不是它该有的语义 ✗）；</li>
 *   <li><b>每段再吃一遍增幅</b>：旧实现逐段 {@code hurt()} ⇒ 每一段都会把整条伤害管线重跑一遍 ✗
 *       ⇒ 所有"在伤害事件里改数值"的处理器（黑闪 {@code ^2.5}、群星之子 {@code ×2^级}、
 *       巫师套装 {@code ×(1+0.1×件数)}、模块化魔杖法术增幅、投射咒法 {@code ×2^层}、
 *       堕落 {@code +bonus}、闪电 {@code +bonus}、炽热/冷酷的"追加伤害"…）对每段各生效一次 ✗✗
 *       ⇒ <b>段数 × 增幅 = 指数级膨胀</b> ✗。</li>
 * </ol>
 *
 * <h2>本轮修法（不改任何既有语义 ✓）</h2>
 *
 * <ol>
 *   <li><b>只拆"进入本处理器时的原始数值"</b>：{@code total} 仍然原样拆（段数组成 / 每段大小 /
 *       伤害类型 / 命中特效 / 无敌帧关系<b>全部不变</b> ✓）；
 *       同时记录"**上游把自己的数值抬了多少**" = {@code total − 进入时的快照} ✓（见 {@code SNAPSHOT}）；</li>
 *   <li><b>把"抬升的那一份"只发一次</b>：抬升量整体并入<b>第一段（物理段）</b>，作为它的一部分，
 *       **不再参与均分** ✓ ⇒ 总伤害 = 基数（照旧均分）+ 抬升量（一次） ✓；</li>
 *   <li><b>嵌套段打标</b>：每段 {@code hurt()} 都被 {@link DamagePipeline#enter()}/{@link DamagePipeline#exit()}
 *       包起来 ✓ ⇒ 我们自己的放大类 / 附加类处理器在内层**一律跳过** ✗✗（不再逐段乘）✓。</li>
 * </ol>
 *
 * <p>⚠ <b>能力边界（如实记录，不许含糊）</b>：
 * <ul>
 *   <li>✅ <b>我们自己的</b>处理器：全部管得住（放大类与附加类都加了内层早退 ✓，清单见备忘录）；</li>
 *   <li>✗ <b>外部 mod 的</b>增幅处理器：<b>管不了</b> ✗ —— 标记只存在于我们自己的 {@code ThreadLocal} 里，
 *       别的 mod 读不到它 ⇒ 它们仍会对**每一段**各乘一次 ✗。
 *       典型如「法术反应」（{@code event/ServerHurtEvent}，默认 NORMAL 优先级，按被打中那一下等比放大 ✓）；
 *       测试实例（{@code G:\tex\.minecraft\versions\1.20.1-Forge_47.4.22}）里**没有**它，
 *       也没扫到别的"放大型"外部处理器 ⇒ **本实例下这条风险只停留在理论层面** ✓（备忘录里有清单）。
 *       缓解手段仍然是既有的"减少段数"：{@code chaos_flow.max_school_segments = 0/较小值} 或
 *       {@code chaos_flow.enabled = false} ✓（段数一变少、被外部 mod 各乘一次的次数就变少 ✓）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChaosFlowHandler {

    private ChaosFlowHandler() {
    }

    /**
     * 嵌套段结算标记：本处理器**自己造出来的**每一段 {@code hurt()} 期间为 {@code true}。
     * <p>它同时承担两个职责（原来是两套东西，2026-09-19 合并 ✓）：
     * <ol>
     *   <li><b>防递归</b>：段内又触发 {@code LivingHurtEvent} 时，本方法在最开头就早退 ✗
     *       （不会"段又被拆" ✗）；</li>
     *   <li><b>让"我们的"放大/附加处理器在内层跳过</b> ✗ —— 它们一律调
     *       {@code DamagePipeline.skipNested()} 早退，于是"一次命中只被放大一次" ✓。
     *       为什么必须与①共用同一个标记：段内既可能**又**被拆、**又**被放大，
     *       两个语义必须同时成立，分开两套标记迟早会出现"只挡了一半"✗。</li>
     * </ol>
     * 见 {@link DamagePipeline} 的说明（同线程同步 ⇒ ThreadLocal 足够且零开销 ✓）。
     */
    private static final ThreadLocal<Boolean> SPLITTING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * 上游"改了数值"的凭证：{@code 进入本处理器时的 amount#identity → 那一次的快照值} ✓。
     *
     * <p>为什么必须做成"快照"而不是直接看 {@link LivingHurtEvent} 里的值：
     * 本处理器挂在 {@link EventPriority#LOWEST}（别人都改完之后 ✓）——
     * 这一层**看不到**"别人改之前是多少" ✗。所以在**更早**的 {@link EventPriority#HIGHEST} 里
     * 先记一份快照，到 {@code LOWEST} 再取回来做差 ✓：
     * <pre>抬升量 = 进入 LOWEST 时的 amount − HIGHEST 时的 amount</pre>
     * 差 > 0 ⇒ 上游（放大类 / 附加类处理器）把这一发抬高了 ✓。
     *
     * <p>⚠ 为什么"抬升量"里同时含**放大**与**附加**：两者在事件里都是同一个 {@code float}，
     * 无法区分 ✗（没有 mod 会标注"这是我加的" ✗）。而无论它俩哪来的，
     * <b>正确语义都只有一个</b>：这一发命中"本来该打出多少"就是 {@code total}（上游处理完的值 ✓）——
     * 所以只要保证 {@code total} **完整发一次**，就不会因为拆分而丢伤害或翻倍 ✓。
     *
     * <p>键用 <b>事件对象的 identity</b>（{@code IdentityHashMap} ✓）而不是目标 UUID：
     * 同一 tick 同一目标可能挨好几发（扫击 / 连击 / 多段），用 UUID 会串味 ✗（§392 的教训 ✓）；
     * {@code IdentityHashMap} 在服务端主线程串行访问 ⇒ 不需要锁 ✓。
     * 取值即删（一条凭证只服务一次进入 ✓），双保险。
     */
    private static final java.util.Map<LivingHurtEvent, Float> SNAPSHOT =
            new java.util.IdentityHashMap<>();

    /**
     * 第 ① 步（{@link EventPriority#HIGHEST} = <b>最先</b>跑）：记下"进入混沌之流之前"的伤害 ✓。
     *
     * <p>⚠ 必须**零开销**：本方法对**全服每一发伤害**都会跑 ✗（不只是混沌之流的武器）——
     * 所以第一件事就是"手里有没有带混沌之流的工具"，没有就直接返回 ✓（一次 {@code ThreadLocal}
     * 读 + 一次工具查询，与既有的其它命中类特性同量级 ✓）。
     * 另外：嵌套段期间（{@code SPLITTING}）一律不记 ✗（段不该有自己的快照 ✓）。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSnapshotHurt(LivingHurtEvent event) {
        if (Boolean.TRUE.equals(SPLITTING.get())) return;         // 我们自己造的段 → 不记 ✓
        LivingEntity target = event.getEntity();
        if (target == null || target.level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (attacker == target) return;
        if (ToolHelper.getCombatToolWith(event.getSource(), attacker, ChaosFlowModifier.ID) == null) return;
        SNAPSHOT.put(event, event.getAmount());
        if (SNAPSHOT.size() > 512) SNAPSHOT.clear();              // 兜底：正常永远到不了（见下面 take 的取值即删 ✓）
    }

    /** 取出快照（取值即删 ✓；没有凭证 / 没被抬升 ⇒ 返回 {@code 0} = 不发"抬升"那一段 ✓） */
    private static float takeMarkup(LivingHurtEvent event, float total) {
        Float snap = SNAPSHOT.remove(event);
        if (snap == null) return 0.0F;
        float markup = total - snap;
        return markup > 0.0F ? markup : 0.0F;                     // 被上层"减伤"减掉的不管 ✓（照旧均分 ✓）
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (Boolean.TRUE.equals(SPLITTING.get())) return;         // 我们自己造的段 → 不再拆分 ✓

        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;

        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof LivingEntity attacker)) return;
        if (attacker == target) return;

        ToolStack tool = ToolHelper.getCombatToolWith(source, attacker, ChaosFlowModifier.ID);
        if (tool == null || ToolHelper.getActiveModifierLevel(tool, ChaosFlowModifier.ID) <= 0) {
            logDiagnostic(attacker, tool);                       // 手里有匠魂工具但没这个特性 → 记一笔 ✓
            return;
        }

        float total = event.getAmount();
        // ⭐ 先把快照取走（取值即删 ✓）：即便下面因为"关闭 / 学派表为空 / 上限 0"等理由**不拆**，
        //    凭证也不会留在 IdentityHashMap 里 ✗（那才是真的内存泄漏 ✗）。不拆时 markup 直接丢弃即可 ✓。
        float markup = takeMarkup(event, total);
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

        // ⭐⭐ 2026-09-19：段数组成与每段大小**完全照旧** —— 基数就是进入本处理器时的 total ✓；
        //     唯一的变化是"上游抬升的那一份"只发一次（并入物理段），不再参与均分 ✓。
        //     没有附加伤害、也没有增幅时 markup = 0 ⇒ 与旧实现**逐位一致** ✓。
        int segments = 1 + schoolKeys.size();
        float base = total - markup;
        float per = base / segments;
        if (per <= 0.0F) return;

        event.setCanceled(true);                                 // 原始那一次不再结算 ✓

        int perTick = segmentsPerTick();
        if (DEBUG) TinkersNewlife.LOGGER.info("[混沌之流] {} 的 {} 点伤害拆成 {} 段（每段 {}，上游抬升 {} 只发一次，学派 {} 个，每 tick 最多 {} 段）",
                attacker.getName().getString(), total, segments, per, markup, schoolKeys.size(), perTick);

        // ① 物理段：同 tick 立即结算 ✓ —— "上游抬升的那一份"并进这一段（只发这一次 ✓）
        creditKill(target, attacker);                            // ⭐ 先补击杀归属（关系到 killed_by_player 类战利品 ✓）
        SPLITTING.set(Boolean.TRUE);
        DamagePipeline.enter();
        try {
            target.invulnerableTime = 0;
            target.hurt(physicalSource(target, attacker), per + markup);
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.debug("[混沌之流] 物理段失败（已忽略）: {}", t.toString());
        } finally {
            DamagePipeline.exit();
            SPLITTING.set(Boolean.FALSE);
        }

        // ② 学派段：段数不多（≤ 16 段，普通整合包 ≈ 9 学派 ✓）⇒ 同 tick 全打完，行为与以前完全一致 ✓；
        //    段数多（学派爆炸的包）⇒ 摊到后面若干 tick，每 tick 只发 perTick 段 ⇒ 单帧不再卡 ✓
        if (perTick <= 0 || schoolKeys.size() <= BURST_LIMIT) {
            SPLITTING.set(Boolean.TRUE);
            DamagePipeline.enter();
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
                DamagePipeline.exit();
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
        DamagePipeline.enter();
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
            DamagePipeline.exit();
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
