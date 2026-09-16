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
 *   <li><b>强化物品内刻印的法术</b>（{@link #onModifySpellLevel}）：监听到铁魔法的
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
            Class<?> levelEvent = Class.forName("io.redspace.ironsspellbooks.api.events.ModifySpellLevelEvent");
            bus.addListener(EventPriority.NORMAL, false, (Class) levelEvent,
                    (Consumer) (Object e) -> onModifySpellLevel(e));
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[联动] 魔导：ModifySpellLevelEvent 挂载失败（法术等级强化不生效）", t);
        }
        try {
            Class<?> dmgEvent = Class.forName("io.redspace.ironsspellbooks.api.events.SpellDamageEvent");
            bus.addListener(EventPriority.LOW, false, (Class) dmgEvent,
                    (Consumer) (Object e) -> onSpellDamage(e));
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

            // ⭐ 超位魔法（材料「魔金」）：刻印的法术"提升至 50 级"——
            //    是**抬到** 50（不是 +50），已经更高的保持原样 ✓；比魔导的加成更高时以它为准。
            int superBoost = 0;
            if (com.mofengbaizhi.tinkersnewlife.content.modifier.SuperTierMagicModifier
                    .inscribedFor(caster, spellId)) {
                int current = currentLevel(event);
                superBoost = Math.max(0, com.mofengbaizhi.tinkersnewlife.content.modifier
                        .SuperTierMagicModifier.INSCRIBED_LEVEL - current);
            }

            // 每级 +5 级法术等级（用户口径：注入法术强度 = 5 × 魔导等级）
            final int boost = Math.max(superBoost,
                    best * ArcaneConductionModifier.SPELL_LEVEL_PER_LEVEL);
            if (boost <= 0) return;
            if (best > 0) {
                TinkersNewlife.LOGGER.debug("[魔导] {} 在刻印列表里命中（魔导 {} 级）→ 法术等级 +{}",
                        spellId, best, boost);
            }


            Method add = find(event.getClass(), "addLevels", int.class);
            if (add != null) {
                add.invoke(event, boost);
                return;
            }
            Method get = find(event.getClass(), "getLevel");
            Method set = find(event.getClass(), "setLevel", int.class);
            if (get != null && set != null) {
                set.invoke(event, ((Number) get.invoke(event)).intValue() + boost);
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
            Object amountObj = invoke(event, "getAmount");
            if (!(amountObj instanceof Number n)) return;
            Method set = find(event.getClass(), "setAmount", float.class);
            if (set == null) return;
            float amount = n.floatValue();

            // ① 魔导：施法增伤
            int level = ArcaneConductionModifier.bestLevel(caster);
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
                && caster == com.mofengbaizhi.tinkersnewlife.content.modifier
                        .SuperTierMagicModifier.castingCaster();
    }

    /** 事件里"当前法术等级"（读不到按 1 算） */
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
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker.level().isClientSide) return;
        if (!looksLikeForeignSpell(source)) return;
        int level = ArcaneConductionModifier.bestLevel(attacker);
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
}
