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
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 特性「<b>混沌之流</b>」结算器（铁魔法联动，见 {@link ChaosFlowModifier}）。
 *
 * <h2>⭐ 现行语义（2026-09-19 用户口径：「改简单一点，不拆分了」）</h2>
 *
 * 每次攻击<b>随机二选一</b>，<b>只打一次</b>（总数值不变 ✓）：
 * <ol>
 *   <li><b>物理</b>（{@code CHANCE_PHYSICAL}）：<b>什么都不做</b> —— 直接放行原始那一次
 *       伤害源与数值 ✓（"保持原伤害源不动" ✓）；</li>
 *   <li><b>法术</b>：{@link LivingHurtEvent#setCanceled(boolean) 取消}原始那一次，
 *       再<b>随机挑一个学派</b>的伤害源（{@link IronSpellsSpellAccess#schoolDamageKeys()}，
 *       动态读铁魔法学派注册表 ⇒ 附属模组新加的学派自动算进来 ✓），
 *       用<b>同样的总数值</b>打<b>一次</b> ✓。</li>
 * </ol>
 *
 * <p>也就是说：这一下要么是物理、要么是"某一学派的法术伤害"，
 * <b>不再拆成"1 段物理 + 每学派 1 段"</b> ✗ —— 单次结算，没有"段"这个概念了。
 *
 * <h2>为什么不再有"段数 × 增幅"膨胀</h2>
 * 膨胀的两个来源都是"一次命中跑多遍伤害管线"造成的 ✗：
 * <ol>
 *   <li><b>附加伤害被均分</b>：旧实现把上游（饰品/其它特性）加进来的那部分也当基数摊成 N 份 ✗；</li>
 *   <li><b>每段再吃一遍增幅</b>：逐段 {@code hurt()} ⇒ 黑闪 {@code ^2.5}、群星之子 {@code ×2^级}、
 *       巫师套装、魔杖法术增幅… 对每一段各生效一次 ✗。</li>
 * </ol>
 * 现在<b>每次攻击只结算一次</b> ⇒ 管线只跑一遍 ⇒ 既没有"摊薄"也没有"逐段重乘" ✓。
 *
 * <h2>为什么这里还要用 {@link DamagePipeline}</h2>
 * <b>只有"改判成法术"那一条路</b>需要它 ✓：我们取消原始事件、又自己 {@code hurt()} 一次，
 * 于是整条伤害管线（含全模组监听器）会为这一发<b>再跑一遍</b> ✗ ——
 * 而"原事件这一边"已经被上游处理器（放大类/附加类）改过一次了 ✓
 * ⇒ 若不拦，我们自己的放大器/附加器会在这一发上<b>再叠一次</b>（黑闪会被再幂一次、
 * 群星之子会再乘一次…）＝变相的"翻倍" ✗。
 * 所以那次 {@code hurt()} 包在 {@link DamagePipeline#enter()}/{@link DamagePipeline#exit()} 里
 * （{@code exit} 在 {@code finally} 里 ✓）⇒ <b>我们自己的</b>处理器在内层一律早退 ✓，
 * 这一发仍然是"只被放大一次" ✓（"物理"那条路根本没重发 ⇒ 不需要标记 ✓）。
 *
 * <p>⚠ <b>能力边界（如实记录，不许含糊）</b>：标记只存在于我们自己的 {@code ThreadLocal} 里，
 * <b>外部模组读不到</b> ✗ ⇒ 它们仍会对这一发（改判后的法术伤害）按自己的规则各算一次。
 * 这是"改判类型 + 重发一次"这件事的固有代价 ✓（旧实现是每段各一次 ⇒ 现在是"一次" ✓，已经是最小值）。
 *
 * <h2>⭐ 三连禁令（本轮新增的用户口径：「连续三次攻击不能打出有相同种类的伤害」）</h2>
 *
 * <b>实现的是"不得三连"</b>：<b>同一"类型"不会连续出现 3 次</b> ✓
 * —— 这里"类型"= <b>物理</b> 或 <b>某一个学派</b>（{@link IronSpellsSpellAccess#schoolDamageKeys()} 里的一个 ✓）。
 * 注意<b>任意两次相邻相同是允许的</b> ✓（例如 火 → 火 → 冰 ✓ 是合法的；只有 火 → 火 → 火 ✗ 被禁）。
 *
 * <p>做法：按<b>施法者 UUID</b> 记录"最近两次选了什么"（{@link #HISTORY}，只留最近 2 条 ⇒ 够判"是否已连续两次同类型"✓），
 * 每次掷骰<b>之前</b>先问一次 {@link #decide(UUID, List)}：
 * <ol>
 *   <li>最近两次都是<b>物理</b> ⇒ 这一发<b>必须走法术</b>（在全部学派里随机取一个 ✓）；</li>
 *   <li>最近两次都是<b>同一个学派 S</b> ⇒ 这一发<b>必须换类型</b>（物理，或<b>除 S 之外</b>的任一学派 ✓）；</li>
 *   <li>其余情况 ⇒ 保持原来的<b>随机二选一</b>（物理 50% / 法术 50%，法术再随机学派 ✓）。</li>
 * </ol>
 * 约束<b>只影响"选哪一种"</b>：总量不变 ✓、单次结算不变 ✓、{@link DamagePipeline} 的用法不变 ✓
 * —— 它只改"掷骰的结果"，不改"打几次 / 打多少" ✓。
 *
 * <p>举例：{@code 物 物 ⇒ 下一发必出法术}；{@code 火 火 ⇒ 下一发必出非火}（可以是物理，也可以是冰/神圣/…）✓。
 *
 * <p><b>历史什么时候清</b>（由本实现自行决定，理由如实写在下面）：
 * <ul>
 *   <li><b>超过 {@link #HISTORY_TTL_MS}（5 分钟）没打 ⇒ 清空该玩家记录</b> ✓ ——
 *       用户口径是"连续三次攻击"，隔了太久就<b>不再是"连续"</b>了 ✗；
 *       但这个窗口也<b>不能太短</b> ⚠：太短 ⇒ 下一轮攻击常常从"干净历史"开始，
 *       于是"最多三连"这件事<b>在观感上不生效</b>（三刀之间一旦插进一次空档就失效 ✗）——
 *       5 分钟足以覆盖"同一场战斗 + 顺手换一下维度/喘口气"✓；</li>
 *   <li>维度切换<b>不</b>清 ✓（历史按施法者 UUID 记，与维度无关 ✓）；</li>
 *   <li>下线<b>不</b>专门清 ✗ —— 它由 5 分钟超时自然覆盖 ✓（少一个事件钩子、少一处遗漏 ✗）；</li>
 *   <li>内存不会无限增长 ✓：条目数上限 {@link #HISTORY_MAX_ENTRIES}（256，超出按"最久没动"淘汰 ✓），
 *       且每次记录顺手做个 O(n) 超时清理 ✓（n ≤ 256，主线程上可忽略 ✓）。</li>
 * </ul>
 * <p>⚠ <b>与"更强的版本"的区别（用户可能要的其实是那个）</b>：本轮实现的是"<b>不得三连</b>"。
 * 若想要"<b>任意连续三次两两不同</b>"（即 火 火 ⇒ 下一发既要非火、又<b>不能与上上发相同</b>，例如 火 冰 ⇒ 下一发既不能火也不能冰），
 * 只需改一处：{@link #decide(UUID, List)} 里三次判断中的<b>相等判定</b>（把"两次是否相同"读法换成"两次是否<b>两两</b>不同"、
 * 并把 {@link #pickSchool(List, String)} 的排除集从"一个学派"扩成"两个"）✓ —— 大约 <b>3 行 / 1 个条件</b> ✓。
 *
 * <h2>几个必须处理的坑</h2>
 * <ul>
 *   <li><b>递归</b>：我们重发的那一发又会触发 {@code LivingHurtEvent} ✗ →
 *       用 {@code SPLITTING}（同线程嵌套标记）在最开头早退 ✓ —— 否则"法术那一发"会被无限改判 ✗；</li>
 *   <li><b>无敌帧</b>：现在<b>不再手动动</b> {@code invulnerableTime} ✓ ——
 *       旧实现每段前清零、收尾恢复 20 是"多段"才需要的（原版 {@code hurt()} 的 20 tick 冷却会吃掉后续段 ✗）；
 *       单次结算下：物理路直接走原版流程 ✓；法术路就是"一次普通命中" ✓
 *       （原版自己在 {@code hurt()} 里把 {@code invulnerableTime} 置 20 ✓）。
 *       ⚠ 唯一残留：若目标在最近 10 tick 内刚挨过更重的一下，这一发会走原版的
 *       "冷却折减"分支（{@code hurt()} 里 {@code invulnerableTime > 10} 那条）——
 *       与"原版打一下"完全同规则 ✓，不再人为干预（详见备忘录的不确定项）；</li>
 *   <li><b>击杀归属</b>：重发前显式 {@code setLastHurtByMob/setLastHurtByPlayer} 一次 ✓
 *       （关系 {@code killed_by_player} 类战利品条件 ✓；原版 {@code hurt()} 自己也会设，
 *       这里是双保险 —— 与旧实现一致 ✓）；</li>
 *   <li><b>已死目标</b>：目标已死则不改判、让原事件照旧 ✓。</li>
 * </ul>
 *
 * <p>本轮<b>删掉</b>的东西（因为新模型用不上了 ✓）：
 * <ul>
 *   <li>{@link EventPriority#HIGHEST} 优先级的"上游抬升量"快照
 *       （{@code IdentityHashMap} + {@code takeMarkup}）—— 那是为"把上游抬升的一份从**均分基数**里摘出来"服务的 ✗，
 *       现在整发只发一次，没有"基数"可分 ✓；</li>
 *   <li>{@code per = base / segments} 均分、逐学派循环 {@code hurt()}；</li>
 *   <li>滞后发放队列（{@code Flurry} / {@code QUEUES} / {@code onLevelTick}）与它的"每 tick 限量"；</li>
 *   <li>逐段的无敌帧清零/恢复（{@code invulnerableTime = 0 / 20}）；</li>
 *   <li>两个只服务于分段的配置项（{@code max_school_segments} / {@code segments_per_tick}）✓。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChaosFlowHandler {

    private ChaosFlowHandler() {
    }

    /**
     * <b>这一发打"物理"的概率</b>：{@code 0.5} ⇒ 物理与法术各一半 ✓。
     *
     * <p>为什么写成常量而不是配置项：用户本轮的口径是"随机选一种"（简化 ✓），
     * 没有要求可调 ✓；真要调，改这一个数就行 ✓。
     */
    private static final float CHANCE_PHYSICAL = 0.5F;

    /**
     * 服务端主线程用的随机源 ✓（{@code hurt()} / 事件派发都在主线程 ⇒ 不需要额外同步 ✓）。
     * 每次攻击只掷<b>一次</b>骰子：先决定"物理还是法术"，法术那一路再掷一次"哪个学派" ✓。
     */
    private static final RandomSource RANDOM = RandomSource.create();

    /**
     * 嵌套结算标记：<b>我们重发的那一发（改判成法术的那一次）</b>期间为 {@code true}。
     * <p>它同时承担两个职责：
     * <ol>
     *   <li><b>防递归</b>：重发的那一发又触发 {@code LivingHurtEvent} 时，本方法在最开头就早退 ✗
     *       （不会"法术那一发又被改判一次" ✗）；</li>
     *   <li><b>让"我们的"放大/附加处理器在内层跳过</b> ✗ —— 它们一律调
     *       {@code DamagePipeline.skipNested()} 早退，于是"一次命中只被放大一次" ✓。</li>
     * </ol>
     * <p>⚠ 与 {@link DamagePipeline} 的关系：标记本身仍由本类持有（递归防护是本处理器的私事 ✓），
     * 两者<b>同开同关</b>（见下面的 {@code hurt()} 调用点 ✓）。上一轮曾经让两个东西合并成一个标记，
     * 现在保持"各管各的、同进同出"更清楚 ✓。
     */
    private static final ThreadLocal<Boolean> SPLITTING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (Boolean.TRUE.equals(SPLITTING.get())) return;         // 我们自己重发的那一发 → 不再改判 ✓

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
        if (total <= 0.0F) return;

        if (!com.mofengbaizhi.tinkersnewlife.config.ModConfig.CHAOS_FLOW_ENABLED.get()) return;

        // ① 掷骰：这一下是"物理"还是"法术"（⭐ 三连禁令在这一个方法里生效，见 decide() ✓）
        List<ResourceKey<DamageType>> schools = schoolKeysCached();
        UUID caster = attacker.getUUID();
        DamageTypePick pick = decide(caster, schools);

        if (pick.school() == null) {
            // ⭐ 物理：**什么都不做** —— 原始那一次照常结算 ✓
            //    （伤害源、数值、无敌帧、击杀归属、下游减伤/限伤/真伤语义全部原样 ✓）
            remember(caster, PHYSICAL_KEY);                      // ⭐ 记进"最近两次"（为了三连禁令 ✓）
            if (DEBUG) TinkersNewlife.LOGGER.info("[混沌之流] {} 的 {} 点伤害 → 本次走【物理】（原始伤害源不改{}）",
                    attacker.getName().getString(), total, pick.forced() ? "·连出两次同类型⇒强制换型" : "");
            return;
        }

        // ② 法术：用掷出的那个学派 —— 学派注册表为空（铁魔法不在场/反射失败）时退回物理 ✓
        if (schools.isEmpty()) {
            logOnce("[混沌之流] 学派注册表为空（铁魔法不在场或反射失败）→ 本次走物理");
            remember(caster, PHYSICAL_KEY);                      // 退回物理 ⇒ 历史也按物理记 ✓（否则禁令会与实际不符 ✗）
            return;
        }
        ResourceKey<DamageType> school = pick.school();
        DamageSource schoolDamage = schoolSource(target, attacker, school);
        if (schoolDamage == null) {                              // 拿不到伤害源 ⇒ 退回物理（照旧）✓
            remember(caster, PHYSICAL_KEY);
            return;
        }

        if (DEBUG) TinkersNewlife.LOGGER.info("[混沌之流] {} 的 {} 点伤害 → 本次走【法术·{}】（单次结算{}）",
                attacker.getName().getString(), total, school.location(),
                pick.forced() ? "·连出两次同类型⇒强制换型" : "");

        event.setCanceled(true);                                 // 原始那一次不再结算 ✓
        remember(caster, schoolKey(school));                     // ⭐ 这一发真的按该学派打出去了 ⇒ 才记账 ✓

        // ③ 重发**一次**：总数值不变 ✓
        creditKill(target, attacker);                            // ⭐ 先补击杀归属（killed_by_player 类战利品 ✓）
        SPLITTING.set(Boolean.TRUE);
        DamagePipeline.enter();                                  // ⭐ 我们自己的放大器/附加器在内层一律跳过 ✓
        try {
            target.hurt(schoolDamage, total);
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.debug("[混沌之流] 改判后的法术伤害结算失败（已忽略）: {}", t.toString());
        } finally {
            DamagePipeline.exit();
            SPLITTING.set(Boolean.FALSE);
        }
    }

    // ============================================================
    //  三连禁令：按施法者记录"最近两次选了什么"
    // ============================================================

    /** "物理"在历史里的类型键 ✓（学派用 {@link #schoolKey(ResourceKey)} 的字符串 ✓） */
    private static final String PHYSICAL_KEY = "physical";

    /** 历史保留时长（毫秒）：超过这么久没打 ⇒ "连续"断了 ⇒ 清空该玩家记录 ✓（理由见类注释 ✓） */
    private static final long HISTORY_TTL_MS = 5 * 60 * 1000L;

    /** 历史表条目上限（防止 UUID 无限堆积 ✗；超出后按"最久没动"淘汰 ✓） */
    private static final int HISTORY_MAX_ENTRIES = 256;

    /** 每个玩家只留<b>最近两次</b>选择（够判"是否已连续两次同类型"✓；不需要更多 ✓） */
    private static final int HISTORY_KEEP = 2;

    /**
     * 施法者 UUID ⇒ 最近两次选择（列表尾 = 最近一次 ✓）。
     * <p>只在<b>服务端主线程</b>访问（{@code hurt()} 与事件派发都在主线程 ✓）⇒ 用普通 {@link HashMap} 就够 ✓。
     * <p>{@code stamp} 是该玩家<b>最近一次</b>记录的时间戳，用于超时清理 ✓。
     */
    private static final Map<UUID, History> HISTORY = new HashMap<>();

    /** 一个玩家的"最近两次选择" + 最近一次时间戳 ✓ */
    private record History(List<String> picks, long stamp) {
    }

    /** 掷骰结果：{@code school == null} ⇒ 走物理 ✓；{@code forced} ⇒ 是被三连禁令强制换的型（只用于日志 ✓） */
    private record DamageTypePick(ResourceKey<DamageType> school, boolean forced) {
    }

    /** 类型键：物理或某个学派（两者直接比字符串 ⇒ 不依赖 damage type 的注册顺序 ✓） */
    private static String schoolKey(ResourceKey<DamageType> key) {
        return key.location().toString();
    }

    /**
     * <b>三连禁令的核心</b>：决定这一发走"物理"还是"哪个学派" ✓。
     *
     * <p>先清理过期记录（{@link #HISTORY_TTL_MS}），再看该玩家"最近两次"：
     * <ol>
     *   <li>最近两次都是物理 ⇒ <b>必须走法术</b>（{@link #pickSchool(List, String)} 传 {@code null} 作排除项 ✓）；</li>
     *   <li>最近两次都是同一学派 S ⇒ <b>必须换类型</b>（物理，或除 S 外的任一学派 ✓）；</li>
     *   <li>其余（含"没历史 / 只有一次 / 两次不同"）⇒ 原来的<b>随机二选一</b> ✓。</li>
     * </ol>
     * <p>强制时若"退无可退"（要法术但没有学派可选 / 要非 S 但没有别的学派 ✗）
     * ⇒ 如实<b>退回随机二选一</b> ✓ —— 宁可破坏禁令也不取消伤害 ✓（"不影响总量"优先 ✓）。
     */
    private static DamageTypePick decide(UUID caster, List<ResourceKey<DamageType>> schools) {
        purgeExpired(System.currentTimeMillis());
        List<String> h = HISTORY.containsKey(caster) ? HISTORY.get(caster).picks() : List.of();
        boolean two = h.size() >= HISTORY_KEEP;
        String last = two ? h.get(h.size() - 1) : null;
        String prev = two ? h.get(h.size() - 2) : null;

        if (two && PHYSICAL_KEY.equals(last) && PHYSICAL_KEY.equals(prev)) {          // 物 · 物 ⇒ 必出法术 ✓
            ResourceKey<DamageType> s = pickSchool(schools, null);
            if (s != null) return new DamageTypePick(s, true);
        }
        if (two && last.equals(prev) && !PHYSICAL_KEY.equals(last)) {                 // 学派 S · 学派 S ⇒ 必换 ✓
            ResourceKey<DamageType> s = pickSchool(schools, last);                    // 非 S 的学派…
            if (s != null && RANDOM.nextBoolean()) return new DamageTypePick(s, true);
            if (schools.size() > 1) return new DamageTypePick(null, true);            // …或者物理 ✓（两条路各一半 ✓）
            // 只有一个学派 ⇒ 退无可退 ⇒ 落到下面的随机二选一 ✓
        }
        // 常规：随机二选一（物理 50% / 法术 50%，法术再随机学派 ✓）—— 与上一轮完全一致 ✓
        if (RANDOM.nextFloat() < CHANCE_PHYSICAL) return new DamageTypePick(null, false);
        ResourceKey<DamageType> s = pickSchool(schools, null);
        return s == null ? new DamageTypePick(null, false) : new DamageTypePick(s, false);
    }

    /** 从学派表里随机取一个，可排除一个（三连禁令的"除 S 之外"✓）；表空 ⇒ {@code null} ✓ */
    private static ResourceKey<DamageType> pickSchool(List<ResourceKey<DamageType>> schools, String excludeKey) {
        if (schools.isEmpty()) return null;
        List<ResourceKey<DamageType>> pool = schools;
        if (excludeKey != null) {
            pool = new ArrayList<>(schools.size());
            for (ResourceKey<DamageType> k : schools) {
                if (!excludeKey.equals(schoolKey(k))) pool.add(k);
            }
            if (pool.isEmpty()) return null;                     // 排除完没得选 ⇒ 交给调用方退让 ✓
        }
        return pool.get(RANDOM.nextInt(pool.size()));
    }

    /** 记一笔"这一发实际打出去的是什么类型"（只留最近 {@link #HISTORY_KEEP} 条 ✓） */
    private static void remember(UUID caster, String key) {
        long now = System.currentTimeMillis();
        List<String> picks = new ArrayList<>(HISTORY_KEEP);
        History old = HISTORY.get(caster);
        if (old != null) {
            for (String p : old.picks()) picks.add(p);
        }
        picks.add(key);
        while (picks.size() > HISTORY_KEEP) picks.remove(0);       // 只留最近两次 ✓
        HISTORY.put(caster, new History(picks, now));
        if (HISTORY.size() > HISTORY_MAX_ENTRIES) {
            purgeExpired(now);
            evictOldest(HISTORY.size() - HISTORY_MAX_ENTRIES);     // 仍然超 ⇒ 按"最久没动"淘汰 ✓
        }
    }

    /** 清掉超过 {@link #HISTORY_TTL_MS} 没再攻击的玩家（"连续"已经断了 ✓） */
    private static void purgeExpired(long now) {
        if (HISTORY.isEmpty()) return;
        HISTORY.entrySet().removeIf(e -> now - e.getValue().stamp() > HISTORY_TTL_MS);
    }

    /** 条目数超限时淘汰最久没动的那几个 ✓（n ≤ {@link #HISTORY_MAX_ENTRIES} ⇒ 主线程上可忽略 ✓） */
    private static void evictOldest(int count) {
        for (int i = 0; i < count && !HISTORY.isEmpty(); i++) {
            UUID oldest = null;
            long oldestStamp = Long.MAX_VALUE;
            for (Map.Entry<UUID, History> e : HISTORY.entrySet()) {
                if (e.getValue().stamp() < oldestStamp) {
                    oldestStamp = e.getValue().stamp();
                    oldest = e.getKey();
                }
            }
            if (oldest == null) return;
            HISTORY.remove(oldest);
        }
    }

    // ============================================================
    //  学派缓存
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

    // ============================================================
    //  诊断（限流，不刷屏）
    // ============================================================

    /** 诊断日志开关（排查"为什么没改判 / 怎么老是物理"时改 true ✓；平时保持 false 不刷屏） */
    private static final boolean DEBUG = false;
    private static volatile long lastLogTime = 0L;

    /** 手里拿着匠魂工具、但这个工具上没有「混沌之流」→ 记一笔（5 秒最多一条） */
    private static void logDiagnostic(LivingEntity attacker, ToolStack tool) {
        if (!DEBUG) return;
        if (tool == null) return;
        long now = System.currentTimeMillis();
        if (now - lastLogTime < 5000L) return;
        lastLogTime = now;
        TinkersNewlife.LOGGER.info("[混沌之流] 攻击者 {} 手持 {} 但没有该特性（工具等级 0）→ 未改判",
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

    /**
     * 显式把"击杀归属"写给目标 ✓。
     *
     * <p>为什么需要：本处理器是"取消原始伤害 + 自己重发一次"（嵌套 {@code hurt} ✓），
     * 而不少模组的战利品表带 {@code killed_by_player} 之类条件 ✓ —— 显式补一次归属，
     * 让"这一下是玩家打的"在死亡结算时一定成立 ✓（用户实测过：改判后打怪不掉东西 ✗）。
     */
    private static void creditKill(LivingEntity target, LivingEntity attacker) {
        try {
            target.setLastHurtByMob(attacker);
            if (attacker instanceof Player player) {
                target.setLastHurtByPlayer(player);
            }
        } catch (Throwable ignored) {
            // 补不上也不影响伤害结算 ✓
        }
    }

    /** 法术：用该学派的法术伤害类型（{@code SchoolType#getDamageType()}）✓ */
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
