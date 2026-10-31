package com.mofengbaizhi.tinkersnewlife.integration.irons_spellbooks;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.ArcaneConductionModifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 铁魔法联动·特性「<b>魔导</b>」结算器。
 *
 * <h2>两件事</h2>
 * <ol>
 *   <li><b>全类型法术 / 巫术 / 咒术伤害每级 +5%（原"强化刻印法术等级"已移除 ✗）</b>（{@link #onModifySpellLevel}）：监听到铁魔法的
 *       {@code ModifySpellLevelEvent} 时，若施法者持有/穿着带魔导、且<b>该物品里刻印了正在施放的这个法术</b>，
 *       就把法术等级按魔导等级抬高（用户明确：强化的是<b>该法术</b>的强度，不是整体法强）；</li>
 *   <li><b>施法增伤</b>（{@link #onSpellDamage} + {@link #onLivingHurt}）：
 *       手持/身穿魔导物品施法时伤害 ×(1 + 0.4×等级)。铁魔法侧用它的
 *       {@code SpellDamageEvent}；<b>诡厄巫法等其它 mod 的法术</b>用通用的
 *       {@code LivingHurtEvent} + "伤害类型像法术"判定兜住（与用户要求的"兼容诡厄法术"对应）。
 *       两者互斥（铁魔法的伤害不会在 LivingHurt 里二次加成）。
 * </ol>
 *
 * <h2>反射约定</h2>
 * 铁魔法事件类在运行时才确定，所以用<b>原始类型 {@code addListener(Class, Consumer)}</b> 挂载，
 * 事件对象内部一律"先按方法名、再按签名"取值；任何一步失败都静默跳过 —— 联动是增强内容，
 * 不允许影响本体。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IronSpellsArcaneHandler {

    /** TNL1321 法术等级加成的诊断节流（每 10 秒最多一条/玩家） */
    private static final java.util.Map<java.util.UUID, Long> TNL_LAST_BOOST_LOG =
            new java.util.concurrent.ConcurrentHashMap<>();

    private IronSpellsArcaneHandler() {
    }

    /** 由 {@link IronSpellsIntegration#register(IEventBus)} 在铁魔法在场时调用 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void attach(IEventBus modBus) {
        // ⚠ 关键：Integration.register(bus) 传进来的是 **mod 事件总线**（只接受 IModBusEvent），
        //    而 ISS 的这两个事件是**游戏内事件**（SpellDamageEvent extends LivingEvent）
        //    → 必须挂到 **Forge 总线**。曾经挂错总线，报
        //    "takes an argument that is not a subtype of the interface IModBusEvent"，
        //    两个监听全部没生效（日志里能看到）。
        IEventBus bus = net.minecraftforge.common.MinecraftForge.EVENT_BUS;
        try {
            Class<?> castEvent = Class.forName("io.redspace.ironsspellbooks.api.events.SpellOnCastEvent");
            bus.addListener(EventPriority.NORMAL, false, (Class) castEvent,
                    (Consumer) (Object e) -> onSpellOnCast(e));
            // TNL1324 等级下限守卫：挂在**最后一个**优先级 ⇒ 即使前面有人把等级 setLevel 掉了，
            //   这里也只把"超位魔法承诺的 满级×2"补回来（**只抬高、不降低别人** ✓）
            bus.addListener(EventPriority.LOWEST, false, (Class) castEvent,
                    (Consumer) (Object e) -> onSpellOnCastFloor(e));
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[联动] 超位魔法：SpellOnCastEvent 挂载失败（范围/持续补偿不生效）", t);
        }
          // ⭐ 用户口径（2026-09-20）：魔导只保留「全类型法术/巫术/咒术伤害 +5%/级」✓
          //   ⇒ 「强化物品内刻印法术等级」那条效果已移除 ✗
          // ⚠ TNL1267 但注意：删魔导时把 **ModifySpellLevelEvent 的注册**一起删掉了 ✗，
          //   结果 superTierMagic（超位魔法）和 originMagic（奥术始源）**也全都不生效**了 ✗。
          //   onModifySpellLevel 里魔导那项已经是 0（只留 debug 日志用）⇒ 重新注册是安全的 ✓。
        try {
            Class<?> levelEvent = Class.forName("io.redspace.ironsspellbooks.api.events.ModifySpellLevelEvent");
            // TNL1324 用户口径：「现在的逻辑有没有可能被其他加成顶掉」
            //   我们只做 addLevels（增量）✓ 但若别的模组在**我们之后**用 setLevel 写绝对值，仍会盖掉 ✗
            //   ⇒ 尽量挂到**最后**（LOWEST），把"被别人后置覆盖"的窗口压到最小 ✓
            bus.addListener(EventPriority.LOWEST, false, (Class) levelEvent,
                    (Consumer) (Object e) -> onModifySpellLevel(e));
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[联动] ModifySpellLevelEvent 挂载失败（超位魔法/奥术始源不生效）", t);
        }
        try {
            Class<?> dmgEvent = Class.forName("io.redspace.ironsspellbooks.api.events.SpellDamageEvent");
            bus.addListener(EventPriority.LOW, false, (Class) dmgEvent,
                    (Consumer) (Object e) -> onSpellDamage(e));
            // TNL1396 最终数值诊断（LOWEST：所有模组都处理完之后）
            bus.addListener(EventPriority.LOWEST, false, (Class) dmgEvent,
                    (Consumer) (Object e) -> tnl$diagnoseFinalDamage(e));
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[联动] 魔导：SpellDamageEvent 挂载失败（法术增伤不生效）", t);
        }
    }

    // ============================================================
    //  ① 物品内刻印法术的等级强化
    // ============================================================

    private static void onModifySpellLevel(Object event) {
        try {
            Object casterObj = invoke(event, "getEntity");
            if (!(casterObj instanceof LivingEntity caster)) return;
            Object spell = invoke(event, "getSpell");
            String spellId = IronSpellsSpellAccess.spellId(spell);
            if (spellId.isEmpty()) return;

            int best = 0;
            for (ItemStack stack : ArcaneConductionModifier.itemsWith(caster)) {
                Set<String> inscribed = IronSpellsSpellAccess.inscribedSpellIds(stack);
                if (!inscribed.contains(spellId)) continue;
                best = Math.max(best, ArcaneConductionModifier.levelOf(stack));
            }

            // ⭐ 超位魔法（材料「魔金」）：刻印的法术强度 = **该法术自身等级上限 ×2**（"超位"= 突破上限）。
            //    不是固定 50 级 ✗ —— 各法术上限差得远（反汇编：深渊庇佑只有 3 级），
            //    统一抬到 50 级会把 3 级法术放大 16 倍、深渊庇佑变成几分钟无敌 ✗。
            int superBoost = 0;
            if (com.mofengbaizhi.tinkersnewlife.content.modifier.SuperTierMagicModifier
                    .inscribedFor(caster, spellId)) {
                int cap = IronSpellsSpellAccess.maxLevel(spell);
                int target = (cap > 0 ? cap : com.mofengbaizhi.tinkersnewlife.content.modifier
                        .SuperTierMagicModifier.FALLBACK_LEVEL)
                        * com.mofengbaizhi.tinkersnewlife.content.modifier
                        .SuperTierMagicModifier.LEVEL_MULTIPLIER;
                // TNL1323 用户口径：「检查超位魔法等级放大逻辑是否会顶掉其他等级放大」
            //   ✗ 旧写法 `target - currentLevel(event)`：currentLevel 是**已经加上别人加成之后**的等级 ⇒
            //      再补到 target ⇒ 最终恒等于 target，别的模组/来源加的等级被一起抹掉 ✗
            //   ✓ 改成以 `getBaseLevel()`（施法者自身等级，不含其它放大）为基准算**增量** ⇒
            //      最终 = 别人的加成 + (满级×2 − 基础等级) ⇒ 谁都不会被顶掉 ✓
            superBoost = Math.max(0, target - baseLevel(event));
            }

            // （魔导的"刻印等级强化"已按用户口径移除 ✗ —— 本方法现在只服务超位魔法与奥术始源 ✓）
            // ⭐ 源钻合金「奥术始源」：**所有法术** +2 × 等级 级（最高单件生效 ✓）——
            //    与魔导不同，它不要求法术刻印在物品里 ✓。
            int originBoost = com.mofengbaizhi.tinkersnewlife.content.modifier.OriginMagicModifier
                    .SPELL_LEVEL_PER_LEVEL
                    * com.mofengbaizhi.tinkersnewlife.content.modifier.OriginMagicModifier.bestLevel(caster);
            // ⚠ 魔导那一项已按用户口径删除（不再提升刻印法术等级 ✗）⇒ 只剩超位魔法与奥术始源 ✓
              // TNL1392 用户口径：「铁魔法联动特性互相覆盖」——超位魔法与奥术始源**改为叠加** ✓
            //   （旧实现 Math.max ⇒ 两者只生效一个 ✗）
            final int boost = superBoost + originBoost;
            if (boost <= 0) return;
            // TNL1321 用户反馈：「超位魔法还是没有显示法术等级增加」⇒ 打一条限频日志，
            //   能直接看出 ModifySpellLevelEvent 有没有触发、超位/奥术各贡献多少 ✓
            try {
                long now = caster.level().getGameTime();
                Long last = TNL_LAST_BOOST_LOG.get(caster.getUUID());
                if (last == null || now - last > 200L) {
                    TNL_LAST_BOOST_LOG.put(caster.getUUID(), now);
                    TinkersNewlife.LOGGER.info(
                            "[法术等级] {} 施放 {} ⇒ +{} 级（超位={} 奥术={}）",
                            caster.getName().getString(), spellId, boost, superBoost, originBoost);
                }
            } catch (Throwable ignored) {
            }
            if (best > 0) {
                TinkersNewlife.LOGGER.debug("[魔导] {} 在刻印列表里命中（魔导 {} 级）→ 法术等级 +{}",
                        spellId, best, boost);
            }


            Method add = find(event.getClass(), "addLevels", int.class);
            if (add != null) {
                add.invoke(event, boost);
                tnl$markLevelBoosted(caster, spellId, boost);
                return;
            }
            Method get = find(event.getClass(), "getLevel");
            Method set = find(event.getClass(), "setLevel", int.class);
            if (get != null && set != null) {
                set.invoke(event, ((Number) get.invoke(event)).intValue() + boost);
                tnl$markLevelBoosted(caster, spellId, boost);
            }
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  ② 施法增伤（铁魔法侧）
    // ============================================================

    /**
     * 超位魔法：把"范围 ×5"顺带抬起来的<b>伤害 ÷5 还原</b>（用户只要求范围变大，
     * 强度按"50 级"走，不该额外再 ×5）✓。
     *
     * <p>法术 id 从 {@code SpellDamageSource.spell()} 取（反汇编确认的方法 ✓），
     * 取不到就退回"当前这一发是不是超位魔法施法"的上下文标记 ✓。
     */
    private static void onSpellDamage(Object event) {
        try {
            Object src = invoke(event, "getSpellDamageSource");
            LivingEntity caster = casterOf(src);
            // TNL1394 用户反馈（魂樱工匠法典放魔法飞弹只吃基础 20）：先尽量把"施法者"解析出来
            //   ⚠ 若 Sakura 的法术伤害源不带 entity，我们的百分比/属性补偿全都会静默跳过 ✗
            if (caster == null) {
                caster = resolveCasterFallback(src, event);
            }
            Object amountObj = invoke(event, "getAmount");
            if (!(amountObj instanceof Number n)) return;
            Method set = find(event.getClass(), "setAmount", float.class);
            if (set == null) return;
            float amount = n.floatValue();
            tnl$diagnoseSpellDamage(event, src, caster, amount);

            // ① 魔导：施法增伤
            int level = ArcaneConductionModifier.totalLevel(caster);
            if (level > 0) {
                amount = (float) (amount * (1.0 + ArcaneConductionModifier.DAMAGE_BONUS_PER_LEVEL * level));
            }

            // ② 超位魔法：把 getSpellPower 的 ×5 除回去
            if (isSuperTierHit(caster, src)) {
                amount /= com.mofengbaizhi.tinkersnewlife.content.modifier.SuperTierMagicModifier
                        .RANGE_MULTIPLIER;
            }

            if (amount != n.floatValue()) set.invoke(event, amount);
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  TNL1394 链路诊断（临时：用户实测"魂樱法典只吃基础伤害"时用）
    // ============================================================
    private static final java.util.Map<java.util.UUID, Long> TNL_DIAG_LAST =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 兜底解析施法者：directEntity（弹射物主人）/ 原版击杀凭据 */
    private static LivingEntity resolveCasterFallback(Object src, Object event) {
        try {
            if (src instanceof net.minecraft.world.damagesource.DamageSource ds) {
                net.minecraft.world.entity.Entity de = ds.getDirectEntity();
                if (de instanceof net.minecraft.world.entity.projectile.Projectile p
                        && p.getOwner() instanceof LivingEntity owner) {
                    return owner;
                }
                if (de instanceof LivingEntity le) {
                    return le;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Object victimObj = invoke(event, "getEntity");
            if (victimObj instanceof net.minecraft.world.entity.LivingEntity victim) {
                net.minecraft.world.entity.Entity credit = victim.getKillCredit();
                if (credit instanceof LivingEntity le) {
                    return le;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * 打一条限频诊断：法术 id / 伤害 / 伤害源 owner / 直接实体 / 解析出的施法者 /
     * 施法者的 SPELL_POWER 属性 / 暴击前的施法等级。用来确认"魂樱法典"这条链在哪一步丢加成。
     */
    private static void tnl$diagnoseSpellDamage(Object event, Object src, LivingEntity caster, float amount) {
        try {
            if (caster == null || caster.level().isClientSide) {
                return;
            }
            long now = System.currentTimeMillis();
            Long last = TNL_DIAG_LAST.get(caster.getUUID());
            if (last != null && now - last < 1000L) {
                return;
            }
            TNL_DIAG_LAST.put(caster.getUUID(), now);

            String spellId = "";
            Object spell = null;
            try {
                Method sp = find(src.getClass(), "spell");
                if (sp != null) {
                    spell = sp.invoke(src);
                    spellId = IronSpellsSpellAccess.spellId(spell);
                }
            } catch (Throwable ignored) {
            }

            String srcOwner = "null";
            String direct = "null";
            try {
                if (src instanceof net.minecraft.world.damagesource.DamageSource ds) {
                    net.minecraft.world.entity.Entity e = ds.getEntity();
                    srcOwner = e == null ? "null" : e.getClass().getSimpleName()
                            + (e instanceof LivingEntity le ? "(" + le.getName().getString() + ")" : "");
                    net.minecraft.world.entity.Entity d = ds.getDirectEntity();
                    direct = d == null ? "null" : d.getClass().getSimpleName();
                }
            } catch (Throwable ignored) {
            }

            double spellPower = -1.0D;
            try {
                net.minecraft.world.entity.ai.attributes.Attribute attr =
                        IronSpellsSpellAccess.attribute("SPELL_POWER");
                if (attr != null) {
                    var inst = caster.getAttribute(attr);
                    if (inst != null) {
                        spellPower = inst.getValue();
                    }
                }
            } catch (Throwable ignored) {
            }

            String hand = caster.getMainHandItem().isEmpty() ? "empty"
                    : caster.getMainHandItem().getItem().builtInRegistryHolder().key().location().toString();

            TinkersNewlife.LOGGER.info(
                    "[法术链路诊断] spell={} amount={} srcOwner={} direct={} caster={} SPELL_POWER={} mainHand={}",
                    spellId, amount, srcOwner, direct, caster.getName().getString(), spellPower, hand);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 这一发伤害是不是"超位魔法"打出来的（法术 id 命中刻印，或被上下文标记包着）
     */
    private static boolean isSuperTierHit(LivingEntity caster, Object damageSource) {
        if (caster == null) return false;
        try {
            Method spellGetter = find(damageSource.getClass(), "spell");
            if (spellGetter != null) {
                Object spell = spellGetter.invoke(damageSource);
                String id = IronSpellsSpellAccess.spellId(spell);
                if (!id.isEmpty()) {
                    return com.mofengbaizhi.tinkersnewlife.content.modifier.SuperTierMagicModifier
                            .inscribedFor(caster, id);
                }
            }
        } catch (Throwable ignored) {
        }
        return com.mofengbaizhi.tinkersnewlife.content.modifier.SuperTierMagicModifier.inSuperTierCast()
                && com.mofengbaizhi.tinkersnewlife.content.modifier.SuperTierMagicModifier.isSuperTierCastActive(caster);
    }

    /** 事件里"当前法术等级"（读不到按 1 算） */
    /**
     * 事件的「基础等级」＝施法者自身等级（**不含**其他 mod / 其他来源在事件里加过的等级）✓
     * 读不到 {@code getBaseLevel()}（老版本铁魔法）才回退 {@link #currentLevel}。
     */
    private static int baseLevel(Object event) {
        try {
            Method get = find(event.getClass(), "getBaseLevel");
            if (get != null) {
                Object v = get.invoke(event);
                if (v instanceof Number num) {
                    return num.intValue();
                }
            }
        } catch (Throwable ignored) {
        }
        return currentLevel(event);
    }

    private static int currentLevel(Object event) {
        try {
            Method get = find(event.getClass(), "getLevel");
            if (get != null) {
                Object v = get.invoke(event);
                if (v instanceof Number num) return num.intValue();
            }
        } catch (Throwable ignored) {
        }
        return 1;
    }

    // ============================================================
    //  ② 施法增伤（诡厄巫法等其它 mod 的法术，走通用受伤事件）
    // ============================================================

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // ⭐ 混沌之流改判成法术时会自己重发一次那一发 ⇒ "魔导增伤"不再对它生效两遍 ✗（见 util/DamagePipeline）
        //    注：本闸门对混沌之流而言**目前是冗余的** ✓ —— 它重发的类型是 `irons_spellbooks.*`，
        //    已被 looksLikeForeignSpell 明确排除（铁魔法的伤害在 SpellDamageEvent 加过一次，
        //    不在这里重复加成 ✓）。留着是为了以后"混沌之流扩展到 goety/magic 等类型"时不会突然漏 ✗。
        if (com.mofengbaizhi.tinkersnewlife.util.DamagePipeline.skipNested()) return;
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker.level().isClientSide) return;
        if (!looksLikeForeignSpell(source)) return;
        int level = ArcaneConductionModifier.totalLevel(attacker);
        if (level <= 0) return;
        event.setAmount((float) (event.getAmount()
                * (1.0 + ArcaneConductionModifier.DAMAGE_BONUS_PER_LEVEL * level)));
    }

    /**
     * "像是别的 mod 的法术伤害"？（诡厄巫法的法术、原版 magic、以及其它命名空间里带 magic 的类型）
     * <p>刻意排除 {@code irons_spellbooks.*}：铁魔法的伤害已经在 {@code SpellDamageEvent} 加过一次，
     * 不在这里重复加成。
     */
    private static boolean looksLikeForeignSpell(DamageSource source) {
        String id = source.getMsgId();
        if (id == null || id.isEmpty()) return false;
        String s = id.toLowerCase();
        if (s.startsWith("irons_spellbooks.")) return false;
        // ⭐ 用户口径（2026-09-20）：魔导要覆盖"全类型法术 / 巫术 / **咒术**伤害" ✓
        //   ⇒ 本模组自己的咒术伤害也吃增幅 ✓（⚠ 但**剔除真伤** `true_pierce`：
        //     那是"穿透/真伤"体系，与咒术增幅不是一回事 ✗）
        if (s.startsWith("tinkersnewlife.")) return !s.contains("true_pierce");
        return s.contains("magic") || s.startsWith("goety.");
    }

    // ============================================================
    //  反射小工具
    // ============================================================

    private static Object invoke(Object target, String name, Class<?>... params) {
        try {
            Method m = find(target.getClass(), name, params);
            return m == null ? null : m.invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method find(Class<?> type, String name, Class<?>... params) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    /** 从法术伤害源里取施法者（getEntity / getCaster / 直接实体 任一） */
    private static LivingEntity casterOf(Object src) {
        if (src == null) return null;
        if (src instanceof LivingEntity le) return le;
        try {
            Object e = invoke(src, "getEntity");
            if (e instanceof LivingEntity le) return le;
        } catch (Throwable ignored) {
        }
        try {
            Object c = invoke(src, "getCaster");
            if (c instanceof LivingEntity le) return le;
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * 铁魔法施法开始（{@code SpellOnCastEvent}）：若施法者身上有刻印了这个法术的超位魔法物品，
     * 就标记"这一发是超位魔法"（{@code SuperTierMagicModifier#markCast}）✓ ——
     * 供 {@code SuperTierEffectMixin} 做治疗 ÷3 与状态时长 ×(2/3) ✓。
     */
    /** 诊断日志开关（平时 false 不刷屏 ✓） */
    private static final boolean DEBUG = false;
    private static volatile long lastSuperTierLog = 0L;

    /**
     * 每 tick 维护「超位魔法」的法术强度加成：
     * 窗口内就（幂等地）挂上；读条法术还在读就续期 ✓；窗口过期就撤掉 ✓。
     */
    @SubscribeEvent
    public static void onSuperTierTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return;
        if (player.level().isClientSide) return;
        var mod = com.mofengbaizhi.tinkersnewlife.content.modifier.SuperTierMagicModifier.class;
        if (com.mofengbaizhi.tinkersnewlife.content.modifier.SuperTierMagicModifier.isSuperTierCastActive(player)) {
            String spell = com.mofengbaizhi.tinkersnewlife.content.modifier.SuperTierMagicModifier.currentSpell(player);
            if (spell != null && IronSpellsSpellAccess.isCastingSpell(player, spell)) {
                com.mofengbaizhi.tinkersnewlife.content.modifier.SuperTierMagicModifier.extendCast(player);
            }
            com.mofengbaizhi.tinkersnewlife.content.modifier.SuperTierMagicModifier.applyCastPower(player);
        } else {
            com.mofengbaizhi.tinkersnewlife.content.modifier.SuperTierMagicModifier.clearCastPower(player);
        }
    }

    private static void onSpellOnCast(Object event) {
        try {
            Object playerObj = invoke(event, "getEntity");
            if (!(playerObj instanceof net.minecraft.world.entity.LivingEntity caster)) return;
            Object idObj = invoke(event, "getSpellId");
            if (!(idObj instanceof String spellId) || spellId.isEmpty()) return;
            com.mofengbaizhi.tinkersnewlife.content.modifier.SuperTierMagicModifier.markCast(caster, spellId);
            tnl$diagnoseCast(caster, spellId, event);
            // ⭐ 属性方案：施法瞬间给 SPELL_POWER +2.0（×3）——
            //    getRadius()/getDuration() 都经由 getSpellPower → 读这个属性 ✓，
            //    所以范围/持续会立刻变大 ✓（不再依赖 mixin ✗）
            com.mofengbaizhi.tinkersnewlife.content.modifier.SuperTierMagicModifier.applyCastPower(caster);
            long now = System.currentTimeMillis();
            if (now - lastSuperTierLog > 2000L) {
                lastSuperTierLog = now;
                if (DEBUG) TinkersNewlife.LOGGER.info("[超位魔法] {} 施放 {} → 法术强度 +200%（范围/持续放大生效）",
                        caster.getName().getString(), spellId);
            }
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  TNL1324 施法等级下限守卫（EventPriority.LOWEST）
    //    ⚠ 只做"抬高到超位魔法的承诺值"，**绝不降低**别处给的更高等级 ✓
    // ============================================================
    private static void onSpellOnCastFloor(Object event) {
        try {
            Object playerObj = invoke(event, "getEntity");
            if (!(playerObj instanceof net.minecraft.world.entity.LivingEntity caster)) return;
            Object idObj = invoke(event, "getSpellId");
            if (!(idObj instanceof String spellId) || spellId.isEmpty()) return;
            Object levelObj = invoke(event, "getSpellLevel");
            if (!(levelObj instanceof Number levelNum)) return;
            int current = levelNum.intValue();
            Method setLevel = find(event.getClass(), "setSpellLevel", int.class);

            // ★ TNL1395 魂樱工匠等「绕过 ModifySpellLevelEvent」的施法路径：
            //   实测日志：铁魔法法典施法有 [法术等级] 日志，魂樱法典施法**没有** ⇒ 它的 cast 不经过 getLevelFor
            //   ⇒ 奥术始源的 +2×等级 会整个丢掉（伤害停在基础值）✗
            //   这里在 SpellOnCastEvent（**每次施法必过**）做兜底：
            //     · 若同一次施法已经由 ModifySpellLevelEvent 加过（标记新鲜）⇒ 不重复加 ✓
            //     · 否则按奥术始源补上 +2×等级 ✓
            boolean alreadyBoosted = tnl$consumeLevelBoostMark(caster, spellId);
            // 只对"玩家正在用某本法术书施放它自己刻着的法术"兜底 ✓
            //   万法归一赠送的回响打击/深渊庇佑是免费施法，不该被再抬 8 级 ✗
            if (!alreadyBoosted && setLevel != null && tnl$castFromSpellbook(caster, spellId)) {
                int originExtra = com.mofengbaizhi.tinkersnewlife.content.modifier.OriginMagicModifier
                        .SPELL_LEVEL_PER_LEVEL
                        * com.mofengbaizhi.tinkersnewlife.content.modifier.OriginMagicModifier
                        .bestLevel(caster);
                if (originExtra > 0) {
                    int before = current;
                    current += originExtra;
                    setLevel.invoke(event, current);
                    long now = caster.level().getGameTime();
                    Long last = TNL_LAST_ORIGIN_FALLBACK_LOG.get(caster.getUUID());
                    if (last == null || now - last > 200L) {
                        TNL_LAST_ORIGIN_FALLBACK_LOG.put(caster.getUUID(), now);
                        TinkersNewlife.LOGGER.info(
                                "[法术等级] 绕路兜底：{} 施放 {} 未经等级事件（{} → {}，奥术 +{}）",
                                caster.getName().getString(), spellId, before, current, originExtra);
                    }
                }
            }

            if (!com.mofengbaizhi.tinkersnewlife.content.modifier.SuperTierMagicModifier
                    .inscribedFor(caster, spellId)) {
                return;
            }

            Object spell = com.mofengbaizhi.tinkersnewlife.integration.irons_spellbooks
                    .IronSpellsSpellAccess.spellById(spellId);
            if (spell == null) return;
            int cap = com.mofengbaizhi.tinkersnewlife.integration.irons_spellbooks
                    .IronSpellsSpellAccess.maxLevel(spell);
            if (cap <= 0) return;
            int floor = cap * com.mofengbaizhi.tinkersnewlife.content.modifier
                    .SuperTierMagicModifier.LEVEL_MULTIPLIER;
            if (current >= floor) {
                return;   // 别人给得更高 ⇒ 不动 ✓
            }
            if (setLevel == null) return;
            setLevel.invoke(event, floor);
            long now = caster.level().getGameTime();
            Long last = TNL_LAST_FLOOR_LOG.get(caster.getUUID());
            if (last == null || now - last > 200L) {
                TNL_LAST_FLOOR_LOG.put(caster.getUUID(), now);
                TinkersNewlife.LOGGER.info(
                        "[法术等级] 超位魔法下限守卫：{} 的 {} 被压到 {}，已抬回 {}",
                        caster.getName().getString(), spellId, current, floor);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 下限守卫的日志节流 */
    private static final java.util.Map<java.util.UUID, Long> TNL_LAST_FLOOR_LOG =
            new java.util.concurrent.ConcurrentHashMap<>();

    // ============================================================
    //  TNL1395 等级事件标记（判断"这次施法是否已经吃到了 ModifySpellLevelEvent"）
    // ============================================================
    private record LevelBoostMark(String spellId, long tick) {
    }

    private static final java.util.Map<java.util.UUID, LevelBoostMark> TNL_LEVEL_BOOSTED =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Long> TNL_LAST_ORIGIN_FALLBACK_LOG =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** ModifySpellLevelEvent 应用增益后记一笔（有效期 5 tick，足够覆盖同一次施法） */
    private static void tnl$markLevelBoosted(net.minecraft.world.entity.LivingEntity caster, String spellId,
                                             int boost) {
        try {
            if (caster == null || spellId == null || spellId.isEmpty() || boost <= 0) {
                return;
            }
            long now = caster.level().getGameTime();
            TNL_LEVEL_BOOSTED.put(caster.getUUID(), new LevelBoostMark(spellId, now));
        } catch (Throwable ignored) {
        }
    }

    /** 这次施法是否已经由 ModifySpellLevelEvent 加过等级（消费标记，避免重复加） */
    private static boolean tnl$consumeLevelBoostMark(net.minecraft.world.entity.LivingEntity caster,
                                                     String spellId) {
        try {
            if (caster == null || spellId == null || spellId.isEmpty()) {
                return false;
            }
            LevelBoostMark mark = TNL_LEVEL_BOOSTED.remove(caster.getUUID());
            if (mark == null || !spellId.equals(mark.spellId())) {
                return false;
            }
            long now = caster.level().getGameTime();
            return now - mark.tick() <= 5L;
        } catch (Throwable ignored) {
            return false;
        }
    }


    /** 施法诊断：法术 id / 原始等级 / 当前等级 / 法术书物品类（判断是不是走铁魔法标准路径） */
    private static void tnl$diagnoseCast(net.minecraft.world.entity.LivingEntity caster, String spellId,
                                         Object event) {
        try {
            long now = System.currentTimeMillis();
            Long last = TNL_LAST_CAST_DIAG.get(caster.getUUID());
            if (last != null && now - last < 1000L) {
                return;
            }
            TNL_LAST_CAST_DIAG.put(caster.getUUID(), now);
            int original = -1;
            int current = -1;
            Object o = invoke(event, "getOriginalSpellLevel");
            if (o instanceof Number n) original = n.intValue();
            Object c = invoke(event, "getSpellLevel");
            if (c instanceof Number n) current = n.intValue();
            String book = "none";
            try {
                Class<?> utils = Class.forName("io.redspace.ironsspellbooks.api.util.Utils");
                java.lang.reflect.Method m = utils.getMethod("getPlayerSpellbookStack",
                        net.minecraft.world.entity.player.Player.class);
                if (caster instanceof net.minecraft.world.entity.player.Player p) {
                    Object stack = m.invoke(null, p);
                    if (stack instanceof net.minecraft.world.item.ItemStack is && !is.isEmpty()) {
                        book = is.getItem().getClass().getSimpleName();
                    }
                }
            } catch (Throwable ignored) {
            }
            String hand = caster.getMainHandItem().isEmpty() ? "empty"
                    : caster.getMainHandItem().getItem().builtInRegistryHolder().key().location().toString();
            TinkersNewlife.LOGGER.info("[施法诊断] spell={} original={} current={} caster={} hand={} spellbook={}",
                    spellId, original, current, caster.getName().getString(), hand, book);
        } catch (Throwable ignored) {
        }
    }

    private static final java.util.Map<java.util.UUID, Long> TNL_LAST_CAST_DIAG =
            new java.util.concurrent.ConcurrentHashMap<>();


    /** 这次施法是不是"玩家用某本法术书/法典施放它自己刻着的法术"（用来限制等级兜底的范围） */
    private static boolean tnl$castFromSpellbook(net.minecraft.world.entity.LivingEntity caster, String spellId) {
        try {
            if (!(caster instanceof net.minecraft.world.entity.player.Player player)) {
                return false;
            }
            Class<?> utils = Class.forName("io.redspace.ironsspellbooks.api.util.Utils");
            java.lang.reflect.Method m = utils.getMethod("getPlayerSpellbookStack",
                    net.minecraft.world.entity.player.Player.class);
            Object obj = m.invoke(null, player);
            if (!(obj instanceof net.minecraft.world.item.ItemStack book) || book.isEmpty()) {
                return false;
            }
            return IronSpellsSpellAccess.inscribedSpellIds(book).contains(spellId);
        } catch (Throwable ignored) {
            return false;
        }
    }


    /** TNL1396 最终伤害诊断（仅服务端、每玩家 500ms 一条）：看所有 handler 跑完后的数值 */
    private static void tnl$diagnoseFinalDamage(Object event) {
        try {
            Object src = invoke(event, "getSpellDamageSource");
            LivingEntity caster = casterOf(src);
            if (caster == null || caster.level().isClientSide) {
                return;
            }
            long now = System.currentTimeMillis();
            Long last = TNL_LAST_FINAL_DIAG.get(caster.getUUID());
            if (last != null && now - last < 500L) {
                return;
            }
            TNL_LAST_FINAL_DIAG.put(caster.getUUID(), now);
            float amount = -1.0F;
            Object a = invoke(event, "getAmount");
            if (a instanceof Number n) {
                amount = n.floatValue();
            }
            boolean canceled = false;
            Object c = invoke(event, "isCanceled");
            if (c instanceof Boolean b) {
                canceled = b;
            }
            String spellId = "?";
            try {
                java.lang.reflect.Method sp = find(src.getClass(), "spell");
                if (sp != null) {
                    spellId = IronSpellsSpellAccess.spellId(sp.invoke(src));
                }
            } catch (Throwable ignored) {
            }
            TinkersNewlife.LOGGER.info("[伤害终值] spell={} amount={} canceled={} caster={}",
                    spellId, amount, canceled, caster.getName().getString());
        } catch (Throwable ignored) {
        }
    }

    private static final java.util.Map<java.util.UUID, Long> TNL_LAST_FINAL_DIAG =
            new java.util.concurrent.ConcurrentHashMap<>();

}
