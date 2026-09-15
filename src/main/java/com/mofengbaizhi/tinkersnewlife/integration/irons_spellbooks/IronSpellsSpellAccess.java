package com.mofengbaizhi.tinkersnewlife.integration.irons_spellbooks;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 铁魔法法术访问层（<b>按"参数/返回类型"反射扫描，不依赖方法名</b>）。
 *
 * <p>为什么这么写：本模组对铁魔法是**纯反射软依赖**（build.gradle 不引用它），
 * 而它的方法名虽然有规律、却没有稳定契约 —— 之前 akaishi 的 `removeBar`、原版
 * `BossHealthOverlay#events` 都吃过"按名字反射"的亏（名字变了/字段是 SRG 名就失效）。
 * 这里统一按<b>签名形状</b>找方法：静态 + 参数 `ItemStack` + 返回 `ISpellContainer` 就是"取容器"，
 * 无参 + 返回 `AbstractSpell` 就是"取槽位里的法术"…… 改名不影响。
 *
 * <p>只在铁魔法在场时被 {@link IronSpellsArcaneHandler} 触达；任何一步失败都只是返回空值，
 * 绝不抛出（法术联动属于增强内容，坏了也不能影响本体）。
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

    /** 静态 ItemStack → ISpellContainer */
    private static Method mContainerGet;
    /** SpellSlot[] getAllSpells() */
    private static Method mAllSpells;
    /** SpellSlot → AbstractSpell */
    private static Method mSlotSpell;
    /** AbstractSpell → ResourceLocation */
    private static Method mSpellResource;
    /** 静态 ResourceLocation → AbstractSpell（SpellRegistry） */
    private static Method mSpellById;
    /** (Level,int,LivingEntity,MagicData)bool */
    private static Method mCastSpell;
    /** 静态 LivingEntity → MagicData */
    private static Method mMagicDataGet;

    private static synchronized void init() {
        if (ready || failed) return;
        try {
            cSpell = Class.forName("io.redspace.ironsspellbooks.api.spells.AbstractSpell");
            cContainer = Class.forName("io.redspace.ironsspellbooks.api.spells.ISpellContainer");
            cSlot = Class.forName("io.redspace.ironsspellbooks.api.spells.SpellSlot");
            cMagicData = Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData");
            cSpellRegistry = Class.forName("io.redspace.ironsspellbooks.api.registry.SpellRegistry");
            cCastSource = Class.forName("io.redspace.ironsspellbooks.api.spells.CastSource");

            // 静态 ItemStack -> ISpellContainer
            for (Method m : cContainer.getMethods()) {
                if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == ItemStack.class
                        && cContainer.isAssignableFrom(m.getReturnType())) {
                    mContainerGet = m;
                    break;
                }
            }
            // SpellSlot[] / List<SpellSlot> 无参
            for (Method m : cContainer.getMethods()) {
                if (m.getParameterCount() == 0
                        && (m.getReturnType() == cSlot.arrayType() || java.util.List.class.isAssignableFrom(m.getReturnType()))) {
                    mAllSpells = m;
                    break;
                }
            }
            // SpellSlot -> AbstractSpell
            for (Method m : cSlot.getMethods()) {
                if (m.getParameterCount() == 0 && cSpell.isAssignableFrom(m.getReturnType())) {
                    mSlotSpell = m;
                    break;
                }
            }
            // AbstractSpell -> ResourceLocation
            for (Method m : cSpell.getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType() == ResourceLocation.class) {
                    mSpellResource = m;
                    break;
                }
            }
            // 静态 ResourceLocation -> AbstractSpell
            for (Method m : cSpellRegistry.getMethods()) {
                if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == ResourceLocation.class
                        && cSpell.isAssignableFrom(m.getReturnType())) {
                    mSpellById = m;
                    break;
                }
            }
            // (Level,int,LivingEntity,MagicData) -> bool
            for (Method m : cSpell.getMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 4 && p[0] == net.minecraft.world.level.Level.class && p[1] == int.class
                        && p[2] == LivingEntity.class && p[3] == cMagicData) {
                    mCastSpell = m;
                    break;
                }
            }
            // 静态 LivingEntity -> MagicData
            for (Method m : cMagicData.getMethods()) {
                if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == LivingEntity.class
                        && m.getReturnType() == cMagicData) {
                    mMagicDataGet = m;
                    break;
                }
            }
            ready = true;
        } catch (Throwable t) {
            failed = true;
        }
    }

    /** 铁魔法法术可用？（任一步失败即视为不可用，调用方直接跳过） */
    public static boolean available() {
        init();
        return ready && mContainerGet != null && mSpellById != null;
    }

    /** 该物品内刻印了哪些法术（返回法术 id 字符串；没容器/没刻印就是空集） */
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

    /**
     * 无吟唱施放指定法术（对 {@code caster} 生效）。
     *
     * @param spell 由 {@link #spellById} 取到的法术对象
     * @param level 法术等级
     * @return 是否成功发起
     */
    public static boolean cast(LivingEntity caster, Object spell, int level) {
        init();
        if (!ready || spell == null || caster == null || mCastSpell == null || mMagicDataGet == null) return false;
        try {
            Object magicData = mMagicDataGet.invoke(null, caster);
            if (magicData == null) return false;
            Object r = mCastSpell.invoke(spell, caster.level(), level, caster, magicData);
            return !(r instanceof Boolean b) || b;
        } catch (Throwable t) {
            return false;
        }
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
