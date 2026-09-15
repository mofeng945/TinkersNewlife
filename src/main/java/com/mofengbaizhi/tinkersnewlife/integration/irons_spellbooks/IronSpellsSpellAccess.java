package com.mofengbaizhi.tinkersnewlife.integration.irons_spellbooks;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 铁魔法法术访问层（纯反射软依赖）。
 *
 * <h2>方法定位策略</h2>
 * 先用 {@code javap} 把 ISS 的真实签名抄下来，再按<b>名字 + 精确参数类型</b>取方法：
 * <pre>
 *   castSpell(Level, int, ServerPlayer, CastSource, boolean)           ← 真正的施法入口（5 参！）
 *   onServerCastComplete(Level, int, LivingEntity, MagicData, boolean) ← 读条结束 → 执行效果
 *   MagicData.resetCastingState() / getMana():float / setMana(float)
 *   AbstractSpell.onServerCastTick(Level, int, LivingEntity, MagicData)
 * </pre>
 *
 * <p>⚠ 踩过的坑：`(Level,int,LivingEntity,MagicData)` 这个 4 参签名在 {@code AbstractSpell} 里有
 * 好几个（`onServerCastTick` 等内部方法），<b>不是施法入口</b> —— 之前按签名扫描取到它，
 * 结果日志显示"释放成功"但游戏里毫无效果（它只是被调用了一下、什么也不做）。
 * 另外 `MagicData.setMana` 的参数是 <b>float</b>，按 int 反射会静默失败（法力其实没垫上）。
 */
public final class IronSpellsSpellAccess {

    private IronSpellsSpellAccess() {
    }

    private static boolean ready = false;
    private static boolean failed = false;

    private static Class<?> cSpell;
    private static Class<?> cContainer;
    private static Class<?> cSlot;
    private static Class<?> cMagicData;
    private static Class<?> cSpellRegistry;
    private static Class<?> cCastSource;

    private static Method mContainerGet;    // static ItemStack -> ISpellContainer
    private static Method mAllSpells;       // SpellSlot[] / List<SpellSlot>
    private static Method mSlotSpell;       // SpellSlot -> AbstractSpell
    private static Method mSpellResource;   // AbstractSpell -> ResourceLocation
    private static Method mSpellById;       // static ResourceLocation -> AbstractSpell
    private static Method mCastSpell;       // castSpell(Level,int,ServerPlayer,CastSource,boolean)
    private static Method mCastComplete;    // onServerCastComplete(Level,int,LivingEntity,MagicData,boolean)
    private static Method mMagicDataGet;    // static LivingEntity -> MagicData
    private static Object defaultCastSource;

    private static synchronized void init() {
        if (ready || failed) return;
        try {
            cSpell = Class.forName("io.redspace.ironsspellbooks.api.spells.AbstractSpell");
            cContainer = Class.forName("io.redspace.ironsspellbooks.api.spells.ISpellContainer");
            cSlot = Class.forName("io.redspace.ironsspellbooks.api.spells.SpellSlot");
            cMagicData = Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData");
            cSpellRegistry = Class.forName("io.redspace.ironsspellbooks.api.registry.SpellRegistry");
            cCastSource = Class.forName("io.redspace.ironsspellbooks.api.spells.CastSource");

            for (Method m : cContainer.getMethods()) {
                if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == ItemStack.class
                        && cContainer.isAssignableFrom(m.getReturnType())) {
                    mContainerGet = m;
                    break;
                }
            }
            for (Method m : cContainer.getMethods()) {
                if (m.getParameterCount() == 0
                        && (m.getReturnType() == cSlot.arrayType()
                            || java.util.List.class.isAssignableFrom(m.getReturnType()))) {
                    mAllSpells = m;
                    break;
                }
            }
            for (Method m : cSlot.getMethods()) {
                if (m.getParameterCount() == 0 && cSpell.isAssignableFrom(m.getReturnType())) {
                    mSlotSpell = m;
                    break;
                }
            }
            for (Method m : cSpell.getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType() == ResourceLocation.class) {
                    mSpellResource = m;
                    break;
                }
            }
            for (Method m : cSpellRegistry.getMethods()) {
                if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == ResourceLocation.class
                        && cSpell.isAssignableFrom(m.getReturnType())) {
                    mSpellById = m;
                    break;
                }
            }
            // ⭐ 真正的施法入口：5 参（Level,int,ServerPlayer,CastSource,boolean）
            for (Method m : cSpell.getMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 5 && p[0] == Level.class && p[1] == int.class
                        && ServerPlayer.class.isAssignableFrom(p[2]) && p[3] == cCastSource
                        && p[4] == boolean.class) {
                    mCastSpell = m;
                    break;
                }
            }
            mCastComplete = find(cSpell, "onServerCastComplete",
                    Level.class, int.class, LivingEntity.class, cMagicData, boolean.class);
            for (Method m : cMagicData.getMethods()) {
                if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == LivingEntity.class
                        && m.getReturnType() == cMagicData) {
                    mMagicDataGet = m;
                    break;
                }
            }
            // 施法来源：优先 SPELLBOOK，退回 SCROLL，再退回枚举第一个
            Object[] constants = cCastSource.getEnumConstants();
            if (constants != null && constants.length > 0) {
                defaultCastSource = constants[0];
                for (Object c : constants) {
                    String n = String.valueOf(c);
                    if ("SPELLBOOK".equals(n)) {
                        defaultCastSource = c;
                        break;
                    }
                    if ("SCROLL".equals(n)) defaultCastSource = c;
                }
            }
            ready = true;
        } catch (Throwable t) {
            failed = true;
            TinkersNewlife.LOGGER.warn("[联动] 铁魔法法术访问层初始化失败", t);
        }
    }

    /** 铁魔法法术可用？ */
    public static boolean available() {
        init();
        return ready && mContainerGet != null && mSpellById != null && mCastSpell != null;
    }

    // ============================================================
    //  容器读取（魔导用）
    // ============================================================

    /** 该物品内刻印了哪些法术（返回法术 id 字符串） */
    public static Set<String> inscribedSpellIds(ItemStack stack) {
        init();
        if (!ready || stack == null || stack.isEmpty() || mContainerGet == null || mAllSpells == null
                || mSlotSpell == null || mSpellResource == null) {
            return Collections.emptySet();
        }
        try {
            Object container = mContainerGet.invoke(null, stack);
            if (container == null) return Collections.emptySet();
            Object raw = mAllSpells.invoke(container);
            Set<String> out = new LinkedHashSet<>();
            if (raw instanceof Object[] arr) {
                for (Object slot : arr) addSlot(out, slot);
            } else if (raw instanceof Iterable<?> it) {
                for (Object slot : it) addSlot(out, slot);
            }
            return out;
        } catch (Throwable t) {
            return Collections.emptySet();
        }
    }

    private static void addSlot(Set<String> out, Object slot) {
        try {
            Object spell = mSlotSpell.invoke(slot);
            Object id = mSpellResource.invoke(spell);
            if (id != null) out.add(id.toString());
        } catch (Throwable ignored) {
        }
    }

    /** 按 id 取法术对象（如 {@code irons_spellbooks:raise_hell}） */
    public static Object spellById(String id) {
        init();
        if (!ready || mSpellById == null) return null;
        try {
            return mSpellById.invoke(null, new ResourceLocation(id));
        } catch (Throwable t) {
            return null;
        }
    }

    /** 该法术的 id 字符串（取不到返回 ""） */
    public static String spellId(Object spell) {
        init();
        if (!ready || spell == null || mSpellResource == null) return "";
        try {
            Object id = mSpellResource.invoke(spell);
            return id == null ? "" : id.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    // ============================================================
    //  施法
    // ============================================================

    /**
     * <b>无吟唱</b>施放指定法术（对 {@code caster} 生效）。
     *
     * <p>为什么不能只调一次入口方法：`raise_hell` 是<b>读条法术</b>，正规流程是
     * `castSpell`（发起 + 设置施法状态）→ 服务端每 tick `onServerCastTick` →
     * 读条结束 `onServerCastComplete`（<b>这里才真正执行效果</b>）。
     * 只调发起那一步 → 日志"成功"但游戏里什么都没有。
     * 这里"立刻补完读条"：发起 → 直接调 `onServerCastComplete` → 清掉施法状态（防重复执行）。
     */
    public static boolean cast(LivingEntity caster, Object spell, int level) {
        init();
        if (!ready || spell == null || !(caster instanceof ServerPlayer player)) return false;
        if (mCastSpell == null || mMagicDataGet == null) return false;
        try {
            Object magicData = mMagicDataGet.invoke(null, caster);
            if (magicData == null) return false;

            // ① 正规入口：发动画/音效包 + 设置读条状态
            mCastSpell.invoke(spell, caster.level(), level, player, defaultCastSource, true);

            // ② 无吟唱：立刻走"读条完成"，真正执行法术效果
            boolean completed = false;
            if (mCastComplete != null) {
                mCastComplete.invoke(spell, caster.level(), level, caster, magicData, true);
                completed = true;
            }
            // ③ 清掉施法状态：避免 ISS 的 tick 再补一次（双份效果）
            try {
                Method reset = find(cMagicData, "resetCastingState");
                if (reset != null) reset.invoke(magicData);
            } catch (Throwable ignored) {
            }
            if (!completed) {
                TinkersNewlife.LOGGER.warn("[联动] 铁魔法施法完成入口缺失（onServerCastComplete）");
            }
            return completed;
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[联动] 铁魔法施法失败: {}", t.toString());
            return false;
        }
    }

    // ============================================================
    //  法力（⚠ setMana 参数是 float，不是 int）
    // ============================================================

    /** 玩家当前法力（读不到返回 -1） */
    public static int manaOf(LivingEntity entity) {
        Object md = magicDataOf(entity);
        if (md == null) return -1;
        try {
            Method m = find(md.getClass(), "getMana");
            return m == null ? -1 : (int) ((Number) m.invoke(md)).floatValue();
        } catch (Throwable t) {
            return -1;
        }
    }

    /** 写玩家法力（float 版；失败静默） */
    public static void setMana(LivingEntity entity, int mana) {
        Object md = magicDataOf(entity);
        if (md == null || mana < 0) return;
        try {
            Method m = find(md.getClass(), "setMana", float.class);
            if (m != null) m.invoke(md, (float) mana);
        } catch (Throwable ignored) {
        }
    }

    /** 该法术在指定等级下的法力消耗（读不到返回 -1） */
    public static int manaCostOf(Object spell, int level) {
        init();
        if (!ready || spell == null) return -1;
        try {
            Method m = find(spell.getClass(), "getManaCost", int.class);
            if (m == null) m = find(cSpell, "getManaCost", int.class);
            return m == null ? -1 : (int) ((Number) m.invoke(spell, level)).floatValue();
        } catch (Throwable t) {
            return -1;
        }
    }

    private static Object magicDataOf(LivingEntity entity) {
        init();
        if (!ready || entity == null || mMagicDataGet == null) return null;
        try {
            return mMagicDataGet.invoke(null, entity);
        } catch (Throwable t) {
            return null;
        }
    }

    // ============================================================
    //  反射小工具
    // ============================================================

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

    /** 法术类型类（事件监听要用 Class 做原始 addListener） */
    public static Class<?> spellClass() {
        init();
        return cSpell;
    }

    /** 施法来源枚举类 */
    public static Class<?> castSourceClass() {
        init();
        return cCastSource;
    }
}
