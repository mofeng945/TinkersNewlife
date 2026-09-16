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
import java.util.List;
import java.util.ArrayList;
import net.minecraft.resources.ResourceKey;

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
    private static Method mSpellResource;   // AbstractSpell -> ResourceLocation（getSpellResource = 真 id）
    private static Method mSpellId;         // AbstractSpell -> String（getSpellId = 真 id 字符串）
    private static Method mSpellMaxLevel;   // AbstractSpell -> int（getMaxLevel = 该法术等级上限）
    private static Method mSpellSchool;     // AbstractSpell -> SchoolType
    private static Method mSchoolId;        // SchoolType -> ResourceLocation（getId）
    private static Method mSchoolDamageType;// SchoolType -> ResourceKey<DamageType>（getDamageType）
    private static Method mSchoolsGet;      // static SchoolRegistry.REGISTRY.get() -> IForgeRegistry
    private static volatile boolean loggedSchoolExtra = false;
    private static Method mSpellById;       // static ResourceLocation -> AbstractSpell
    private static Method mCastSpell;       // castSpell(Level,int,ServerPlayer,CastSource,boolean)
    private static Method mCastComplete;    // onServerCastComplete(Level,int,LivingEntity,MagicData,boolean)
    private static Method mMagicDataGet;    // static LivingEntity -> MagicData
    private static Method mContainerCreate; // static create(int,boolean,boolean)
    private static Method mContainerAdd;    // addSpell(AbstractSpell,int,boolean,ItemStack)
    private static Method mContainerSave;   // save(ItemStack)
    private static Object defaultCastSource;
    private static Object iceSpellPower;    // Attribute：铁魔法 ICE_SPELL_POWER
    private static final java.util.Map<String, Object> SPELL_POWERS = new java.util.concurrent.ConcurrentHashMap<>();

    private static synchronized void init() {
        if (ready || failed) return;
        try {
            cSpell = Class.forName("io.redspace.ironsspellbooks.api.spells.AbstractSpell");
            cContainer = Class.forName("io.redspace.ironsspellbooks.api.spells.ISpellContainer");
            cSlot = Class.forName("io.redspace.ironsspellbooks.api.spells.SpellSlot");
            cMagicData = Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData");
            cSpellRegistry = Class.forName("io.redspace.ironsspellbooks.api.registry.SpellRegistry");
            cCastSource = Class.forName("io.redspace.ironsspellbooks.api.spells.CastSource");
            // 冰霜法术强度属性（ISS 自己的字段名，不会被重映射）
            try {
                Class<?> attrReg = Class.forName("io.redspace.ironsspellbooks.api.registry.AttributeRegistry");
                for (java.lang.reflect.Field fld : attrReg.getFields()) {
                    if (!"ICE_SPELL_POWER".equals(fld.getName())) continue;
                    Object ro = fld.get(null);
                    iceSpellPower = ro.getClass().getMethod("get").invoke(ro);
                    break;
                }
            } catch (Throwable ignored) {
            }

            // ⚠ 一律**按名字**取：ISpellContainer 上 get / getOrCreate 都是「静态 + ItemStack → 容器」，
            //   靠返回类型撞可能命中 getOrCreate → "是否已有容器"永远为真 → 永远不会刻印 ✗（静默失效）。
            //   名字取不到时再退回"按类型挑"，兼容别的 ISS 版本。
            mContainerGet = find(cContainer, "get", ItemStack.class);
            if (mContainerGet == null) {
                for (Method m : cContainer.getMethods()) {
                    if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1
                            && m.getParameterTypes()[0] == ItemStack.class
                            && cContainer.isAssignableFrom(m.getReturnType())) {
                        mContainerGet = m;
                        break;
                    }
                }
            }
            // getAllSpells()（全量）而不是 getActiveSpells()（漏掉未激活的格子）✓
            mAllSpells = find(cContainer, "getAllSpells");
            if (mAllSpells == null) {
                for (Method m : cContainer.getMethods()) {
                    if (m.getParameterCount() == 0
                            && (m.getReturnType() == cSlot.arrayType()
                                || java.util.List.class.isAssignableFrom(m.getReturnType()))) {
                        mAllSpells = m;
                        break;
                    }
                }
            }
            mSlotSpell = find(cSlot, "getSpell");
            if (mSlotSpell == null) {
                for (Method m : cSlot.getMethods()) {
                    if (m.getParameterCount() == 0 && cSpell.isAssignableFrom(m.getReturnType())) {
                        mSlotSpell = m;
                        break;
                    }
                }
            }
            // ⚠ 必须**按名字**取：cSpell 上返回 ResourceLocation 的零参方法有两个
            //   getSpellResource()     → 真 id（如 irons_spellbooks:counterspell）✓
            //   getSpellIconResource() → 图标贴图（…/textures/gui/spell_icons/counterspell.png）✗
            //   之前用"第一个返回 ResourceLocation 的方法"撞上了图标那个 → 破法 tooltip 打出了贴图路径。
            mSpellResource = find(cSpell, "getSpellResource");
            mSpellId = find(cSpell, "getSpellId");
            mSpellMaxLevel = find(cSpell, "getMaxLevel");
            // 学派相关（「混沌之流」按学派拆段 + 「奥术始源」识别邪术）
            try {
                Class<?> cSchool = Class.forName("io.redspace.ironsspellbooks.api.spells.SchoolType");
                Class<?> cSchoolRegistry = Class.forName("io.redspace.ironsspellbooks.api.registry.SchoolRegistry");
                mSpellSchool = find(cSpell, "getSchoolType");
                mSchoolId = find(cSchool, "getId");
                mSchoolDamageType = find(cSchool, "getDamageType");
                for (java.lang.reflect.Field f : cSchoolRegistry.getFields()) {
                    if (f.getName().equals("REGISTRY") && java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                        Object supplier = f.get(null);
                        mSchoolsGet = find(supplier.getClass(), "get");
                        break;
                    }
                }
            } catch (Throwable t) {
                TinkersNewlife.LOGGER.debug("[联动] 学派反射初始化失败（混沌之流将只用兜底学派表）: {}", t.toString());
            }
            if (mSchoolsGet == null || mSchoolDamageType == null || mSpellSchool == null || mSchoolId == null) {
                TinkersNewlife.LOGGER.debug("[联动] 学派反射不完整：schoolsGet={} damageType={} spellSchool={} schoolId={}",
                        mSchoolsGet != null, mSchoolDamageType != null, mSpellSchool != null, mSchoolId != null);
            }
            mSpellById = find(cSpellRegistry, "getSpell", ResourceLocation.class);
            if (mSpellById == null) {
                for (Method m : cSpellRegistry.getMethods()) {
                    if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1
                            && m.getParameterTypes()[0] == ResourceLocation.class
                            && cSpell.isAssignableFrom(m.getReturnType())) {
                        mSpellById = m;
                        break;
                    }
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
            mContainerCreate = find(cContainer, "create", int.class, boolean.class, boolean.class);
            mContainerAdd = find(cContainer, "addSpell", cSpell, int.class, boolean.class, ItemStack.class);
            mContainerSave = find(cContainer, "save", ItemStack.class);
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
            // 施法来源：优先 **SCROLL** —— 反汇编 CastSource#consumesMana() 可知：
            //   只有 SPELLBOOK（以及开配置时的 SWORD）消耗法力，SCROLL/MOB/COMMAND/NONE 都不消耗 ✓
            //   （respectsCooldown() 同样是 SPELLBOOK/SWORD 才为真 → 赠送施法也不会吃 ISS 自身冷却 ✓）。
            //   "特性白送的法术"（提洛斯炼狱的地狱浮现、万法归一的回响打击/深渊庇佑）就该走这个语义 ✓，
            //   否则会真的扣蓝 ✗。退回 SPELLBOOK，再退回枚举第一个。
            Object[] constants = cCastSource.getEnumConstants();
            if (constants != null && constants.length > 0) {
                defaultCastSource = constants[0];
                for (Object c : constants) {
                    String n = String.valueOf(c);
                    if ("SCROLL".equals(n)) {
                        defaultCastSource = c;
                        break;
                    }
                    if ("SPELLBOOK".equals(n)) defaultCastSource = c;
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
                || mSlotSpell == null || (mSpellId == null && mSpellResource == null)) {
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
            String id = spellId(spell);
            if (!id.isEmpty()) out.add(id);
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

    /**
     * 该法术的<b>等级上限</b>（如深渊庇佑 3、回响打击 5、多数法术 10；取不到返回 0）。
     *
     * <p>用途：「超位魔法」把刻印法术抬到 <b>上限 ×2</b> 而不是固定 50 级 ——
     * 各法术上限差得远，固定 50 会把短时长法术（深渊庇佑只有 3 级）炸成几分钟无敌 ✗。
     */
    public static int maxLevel(Object spell) {
        init();
        if (!ready || spell == null || mSpellMaxLevel == null) return 0;
        try {
            Object v = mSpellMaxLevel.invoke(spell);
            return v instanceof Number n ? n.intValue() : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * 该法术的 id 字符串（如 {@code irons_spellbooks:counterspell}；取不到返回 ""）。
     *
     * <p>⚠ 这里必须给"真 id"，不能给图标贴图路径 —— {@code IronSpellsArcaneHandler} 拿它跟
     * 物品内刻印的法术 id 集合做包含判断（魔导加等级），给错了就永远匹配不上 ✗。
     * {@code getSpellId()} 本身就是 {@code getSpellResource().toString()} 的缓存 ✓。
     */
    public static String spellId(Object spell) {
        init();
        if (!ready || spell == null) return "";
        try {
            if (mSpellId != null) {
                Object id = mSpellId.invoke(spell);
                if (id instanceof String s && !s.isEmpty()) return s;
            }
            Object res = mSpellResource == null ? null : mSpellResource.invoke(spell);
            return idFromResource(res);
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * 退路：从资源路径反推法术 id。
     * <p>兼容两种形状：{@code irons_spellbooks:counterspell} 与
     * {@code irons_spellbooks:textures/gui/spell_icons/counterspell.png} → 都得到
     * {@code irons_spellbooks:counterspell} ✓。
     */
    private static String idFromResource(Object res) {
        if (res == null) return "";
        String s = res.toString();
        int colon = s.indexOf(':');
        if (colon <= 0) return "";
        String ns = s.substring(0, colon);
        String path = s.substring(colon + 1);
        int slash = path.lastIndexOf('/');
        String file = slash < 0 ? path : path.substring(slash + 1);
        if (file.endsWith(".png")) file = file.substring(0, file.length() - 4);
        return file.isEmpty() ? "" : ns + ":" + file;
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

    /** 铁魔法「冰霜法术强度」属性（拿不到返回 null） */
    public static net.minecraft.world.entity.ai.attributes.Attribute iceSpellPower() {
        init();
        return iceSpellPower instanceof net.minecraft.world.entity.ai.attributes.Attribute a ? a : null;
    }

    /**
     * 铁魔法某学派的法术强度属性（字段名如 {@code "FIRE_SPELL_POWER"} / {@code "ICE_SPELL_POWER"}）。
     * <p>⚠ ISS 的 AttributeRegistry 字段不被重映射，可按名字取；取不到返回 null（调用方跳过）。
     */
    public static net.minecraft.world.entity.ai.attributes.Attribute spellPower(String fieldName) {
        init();
        if (!ready || fieldName == null) return null;
        Object cached = SPELL_POWERS.get(fieldName);
        if (cached != null) return cached instanceof net.minecraft.world.entity.ai.attributes.Attribute a ? a : null;
        try {
            Class<?> attrReg = Class.forName("io.redspace.ironsspellbooks.api.registry.AttributeRegistry");
            for (java.lang.reflect.Field fld : attrReg.getFields()) {
                if (!fieldName.equals(fld.getName())) continue;
                Object ro = fld.get(null);
                Object attr = ro.getClass().getMethod("get").invoke(ro);
                SPELL_POWERS.put(fieldName, attr);
                return attr instanceof net.minecraft.world.entity.ai.attributes.Attribute a ? a : null;
            }
        } catch (Throwable ignored) {
        }
        SPELL_POWERS.put(fieldName, Boolean.FALSE);
        return null;
    }

    /** 通用属性存取（字段名如 CAST_TIME_REDUCTION / FIRE_SPELL_POWER / ICE_SPELL_POWER） */
    public static net.minecraft.world.entity.ai.attributes.Attribute attribute(String fieldName) {
        return spellPower(fieldName);
    }

    /**
     * 确保物品里刻印了指定法术（<b>仅当该物品还没有法术容器时</b>写入 ✗ 免得跟奥术铁砧的编辑打架）。
     * <p>用途：材料「秘银」的「破法」特性 —— 工具自带 1 级「法术反制」。
     */
    public static boolean ensureInscribed(ItemStack stack, String spellId, int level, int slots) {
        init();
        if (!ready || stack == null || stack.isEmpty()) return false;
        if (mContainerGet == null || mContainerCreate == null || mContainerAdd == null || mContainerSave == null) {
            TinkersNewlife.LOGGER.warn("[破法] 容器方法缺失：get={} create={} addSpell={} save={}",
                    mContainerGet != null, mContainerCreate != null, mContainerAdd != null, mContainerSave != null);
            return false;
        }
        try {
            if (mContainerGet.invoke(null, stack) != null) return true;   // 已是容器（可能已被铁砧编辑过）→ 不动
            Object spell = spellById(spellId);
            if (spell == null) return false;
            Object container = mContainerCreate.invoke(null, Math.max(1, slots), true, false);
            mContainerAdd.invoke(container, spell, level, true, stack);
            mContainerSave.invoke(container, stack);
            TinkersNewlife.LOGGER.debug("[破法] 已向 {} 刻入法术 {} Lv{}", stack.getItem(), spellId, level);
            return true;
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[破法] 刻入 {} 失败: {}", spellId, t.toString());
            return false;
        }
    }

    /**
     * 该法术是否属于<b>邪术</b>学派（{@code irons_spellbooks:eldritch}）—— 供「奥术始源」放开学习限制 ✓。
     */
    public static boolean isEldritch(Object spell) {
        init();
        if (spell == null || mSpellSchool == null || mSchoolId == null) return false;
        try {
            Object school = mSpellSchool.invoke(spell);
            Object id = mSchoolId.invoke(school);
            return id instanceof ResourceLocation rl && "eldritch".equals(rl.getPath());
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * <b>所有已注册学派</b>的法术伤害类型（用于「混沌之流」按学派拆段）✓。
     *
     * <p>从学派注册表现取 ✓ —— 每个 {@code SchoolType} 自带 {@code getDamageType()}
     * （返回 {@code ResourceKey<DamageType>}）✓，所以<b>附属模组新加的学派自动算进来</b> ✓。
     * 取不到（铁魔法不在场）返回空表 ✓。
     */
    public static List<ResourceKey<net.minecraft.world.damagesource.DamageType>> schoolDamageKeys() {
        init();
        List<ResourceKey<net.minecraft.world.damagesource.DamageType>> out = new ArrayList<>();
        // 兜底基线：铁魔法自带的 9 个学派伤害类型（数据包里的固定条目 ✓）——
        // 动态反射成功时再补上附属新增的学派 ✓；失败也不至于"整条特性失效" ✗。
        for (String school : new String[]{"fire", "ice", "lightning", "holy", "ender",
                "blood", "evocation", "nature", "eldritch"}) {
            out.add(ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                    new ResourceLocation("irons_spellbooks", school + "_magic")));
        }
        if (mSchoolsGet == null || mSchoolDamageType == null) return out;
        int dynamic = 0;
        try {
            Object registry = mSchoolsGet.invoke(null);
            if (registry == null) return out;
            Method values = find(registry.getClass(), "getValues");
            if (values == null) return out;
            Object raw = values.invoke(registry);
            if (raw instanceof Iterable<?> it) {
                for (Object school : it) {
                    try {
                        Object key = mSchoolDamageType.invoke(school);
                        if (key instanceof ResourceKey<?> rk) {
                            @SuppressWarnings("unchecked")
                            ResourceKey<net.minecraft.world.damagesource.DamageType> typed =
                                    (ResourceKey<net.minecraft.world.damagesource.DamageType>) rk;
                            if (!out.contains(typed)) { out.add(typed); dynamic++; }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** 法术类型类（事件监听要用 Class 做原始 addListener） */
    /**
     * 该生物此刻是否正在<b>读条</b>某个法术（{@code MagicData.isCasting()} + {@code getCastingSpellId()}）✓。
     * <p>用途：「超位魔法」在施法期间维持"法术强度 ×3"的属性修饰符 —— 即时法术一次性覆盖，
     * 读条法术则每 tick 续期到读完 ✓。
     */
    public static boolean isCastingSpell(LivingEntity entity, String spellId) {
        init();
        if (entity == null || spellId == null || mMagicDataGet == null || cMagicData == null) return false;
        try {
            Object md = mMagicDataGet.invoke(null, entity);
            if (md == null) return false;
            Method isCasting = find(cMagicData, "isCasting");
            if (isCasting == null || !(Boolean) isCasting.invoke(md)) return false;
            Method getSpell = find(cMagicData, "getCastingSpellId");
            if (getSpell == null) return false;
            Object id = getSpell.invoke(md);
            return id instanceof String s && s.equals(spellId);
        } catch (Throwable t) {
            return false;
        }
    }

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
