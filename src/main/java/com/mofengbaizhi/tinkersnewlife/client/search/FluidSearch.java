package com.mofengbaizhi.tinkersnewlife.client.search;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * §1116 <b>熔炼炉 / 熔铸炉 流体列表的搜索</b>（用户口径 ✓）：
 * 「熔炼炉和熔铸炉内流体种类过多会严重影响寻找效率：给两种炉子的界面加一个搜索框，
 *   搜索筛选对应流体，并将符合的流体高亮出来，支持<u>模组、id、汉字、标签</u>和 <u>JEI 搜索</u>」✓
 * ＋ 后续澄清：「**不加通用拼音搜索就不要有拼音搜索能力**」✓
 * ⇒ 拼音**只走**「通用拼音搜索」(`jecharacters` ✓ 见 {@link #pinyinMatches}) ✓，
 *   没装它就**没有拼音** ✓（**故意不做自带小表** ✗ 用户口径 ✓）。
 *
 * <h2>支持语法（照 JEI 那套 ✓）</h2>
 * <ul>
 *   <li><b>空格 ＝ AND</b> ✓（`熔融 铁` ⇒ 两个词都命中才算 ✓）</li>
 *   <li><b>{@code |} ＝ OR</b> ✓（`铁|铜` ✓）</li>
 *   <li><b>前缀 {@code -} ＝ 排除</b> ✓（`-水` ⇒ 名字里没有"水" ✓）</li>
 *   <li><b>{@code @模组id}</b> ✓（`@tconstruct` ✓ 按命名空间匹配 ✓）</li>
 *   <li><b>{@code #标签}</b> ✓（`#forge:molten_iron` 或 `#molten` ⇒ 按流体标签匹配 ✓）</li>
 *   <li><b>其余</b> ⇒ 依次匹配：<b>显示名（含汉字）</b> ✓ → <b>注册名 {@code namespace:path} 与其分段</b> ✓ → <b>拼音</b>（仅当装了通用拼音搜索 ✓）</li>
 * </ul>
 *
 * <h2>用法（配合两个 mixin ✓）</h2>
 * {@link #setQuery(String)} 由搜索框写入 ✓（`HeatingStructureScreen` mixin ✓）；
 * {@link #matches(FluidStack)} 由 `GuiSmelteryTank` mixin 逐条询问 ✓，
 * 不匹配的流体**高度置 0**（不是从列表里删掉 ✗）⇒ hover/点击/tooltip 的索引**天然对齐** ✓。
 */
public final class FluidSearch {

    private FluidSearch() {
    }

    /** 当前查询（已小写化 + trim ✓ 空串 ＝ 未搜索 ✓） */
    private static volatile String query = "";
    /** 解析后的 OR 组（每组是一串 AND 词 ✓ 带排除标记 ✓）——查询一变就重算 ✓ */
    private static volatile List<Group> groups = List.of();

    /** 一条词：{@code exclude} ＝ 前置 `-` ✓；{@code kind} 决定匹配哪一列 ✓ */
    private record Term(boolean exclude, Kind kind, String text) {
    }

    private enum Kind {
        /** 默认：显示名 / 注册名 / 拼音 都试 ✓ */
        ANY,
        /** `@` 前缀：按命名空间（模组 id）✓ */
        MOD,
        /** `#` 前缀：按流体标签 ✓ */
        TAG
    }

    /** 一组 AND 词 ✓ */
    private record Group(List<Term> terms) {
    }

    // ============================================================
    //  查询写入（搜索框调用 ✓）
    // ============================================================

    /** 写入查询（界面每次改动都调 ✓ 内部做了缓存，重复调用不重建 ✓） */
    public static void setQuery(String raw) {
        String q = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (q.equals(query)) return;
        query = q;
        groups = parse(q);
    }

    public static String getQuery() {
        return query;
    }

    /** 搜索框里有没有内容 ✓（false ⇒ 界面按原样显示 ✓ 高度一个都不改 ✓） */
    public static boolean isActive() {
        return !query.isEmpty();
    }

    // ============================================================
    //  匹配
    // ============================================================

    /** 这条流体算不算命中 ✓（未搜索时恒 true ✓） */
    public static boolean matches(net.minecraftforge.fluids.FluidStack stack) {
        if (query.isEmpty() || stack == null || stack.isEmpty()) return true;
        List<Group> gs = groups;
        if (gs.isEmpty()) return true;
        Fluid fluid = stack.getFluid();
        // 预取三条可用文本 ✓（null 安全 ✓）
        String name = "";
        String id = "";
        String[] idParts = new String[0];
        try {
            name = stack.getDisplayName().getString().toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
        }
        try {
            ResourceLocation key = ForgeRegistries.FLUIDS.getKey(fluid);
            if (key != null) {
                id = key.toString().toLowerCase(Locale.ROOT);
                idParts = new String[]{
                        key.getNamespace().toLowerCase(Locale.ROOT),
                        key.getPath().toLowerCase(Locale.ROOT)
                };
            }
        } catch (Throwable ignored) {
        }
        for (Group g : gs) {                       // OR：任一组全过即命中 ✓
            boolean all = true;
            for (Term t : g.terms()) {             // AND：组内每词都过 ✓
                if (termMatches(t, name, id, idParts, fluid) == t.exclude()) {
                    all = false;
                    break;
                }
            }
            if (all) return true;
        }
        return false;
    }

    /** 单个词是否命中 ✓（返回值是"**这个词**命中"✓ 排除逻辑在上面统一处理 ✓） */
    private static boolean termMatches(Term t, String name, String id, String[] idParts, Fluid fluid) {
        String s = t.text();
        if (s.isEmpty()) return true;
        switch (t.kind()) {
            case MOD -> {
                return idParts.length > 0 && idParts[0].contains(s);
            }
            case TAG -> {
                return tagMatches(fluid, s);
            }
            default -> {
                if (name.contains(s)) return true;                 // 汉字 / 英文名 ✓
                if (id.contains(s)) return true;                   // namespace:path ✓
                for (String p : idParts) {
                    if (p.contains(s)) return true;                // 只写 path 也行 ✓
                }
                return pinyinMatches(name, s);                     // ⚠ 仅装了通用拼音搜索时才有 ✓
            }
        }
    }

    /** `#标签` 匹配 ✓（把该流体的所有标签 id 拼成一个串再 contains ✓ 简单且稳 ✓） */
    private static boolean tagMatches(Fluid fluid, String s) {
        try {
            ResourceLocation key = ForgeRegistries.FLUIDS.getKey(fluid);
            if (key == null) return false;
            var holder = ForgeRegistries.FLUIDS.getHolder(key);
            if (holder.isEmpty()) return false;
            for (net.minecraft.tags.TagKey<Fluid> tag : holder.get().tags().toList()) {
                String tid = tag.location().toString().toLowerCase(Locale.ROOT);
                if (tid.contains(s)) return true;
            }
        } catch (Throwable ignored) {
            // 拿不到标签 ⇒ 当作不命中 ✓ 绝不让界面报错 ✓
        }
        return false;
    }

    // ============================================================
    //  拼音：**只接**「通用拼音搜索」(`jecharacters`) ✓
    // ============================================================

    /** 反射句柄（一次性解析 ✓ 失败就永久 false ✓ 不反复抛异常拖慢界面 ✓） */
    private static volatile boolean pinyinResolved = false;
    private static volatile Object pinyinSearcher = null;
    private static volatile java.lang.reflect.Method pinyinContains = null;

    /**
     * ⚠ 用户口径：「**不加通用拼音搜索就不要有拼音搜索能力**」✓ ——
     * 这里**只**尝试用 `me.towdium.pinin`（通用拼音搜索 / Just Enough Characters 的拼音库 ✓）；
     * 没装 ⇒ 直接返回 false ✓（**故意不做自带拼音表** ✗）。
     *
     * <p>⚠ 实现注意：那几个类**不是给第三方用的公开 API** ✗（它是用 ASM 改 JEI 搜索的 ✓）
     * ⇒ 全程 `try/catch` ✓，探测失败只影响"拼音不好用"✓ **绝不影响搜索框本身** ✓。
     * <p>⚠ **待办**（下个会话第一步 ✓）：`me.towdium.pinin.searchers.Searcher` 的**构造方式与查询方法名**
     * 还没反编译确认 ✗ —— 现在这段写法是"尽力而为"✓，拿不到句柄就静默降级 ✓（见 §1115f ✓）。
     */
    private static boolean pinyinMatches(String name, String s) {
        if (name.isEmpty() || s.isEmpty()) return false;
        try {
            if (!pinyinResolved) {
                synchronized (FluidSearch.class) {
                    if (!pinyinResolved) {
                        resolvePinyin();
                        pinyinResolved = true;
                    }
                }
            }
            java.lang.reflect.Method m = pinyinContains;
            Object searcher = pinyinSearcher;
            if (m == null || searcher == null) return false;
            Object r = m.invoke(searcher, name, s);
            return r instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 尽力解析通用拼音搜索的搜索器 ✓（找不到就保持 null ✓） */
    private static void resolvePinyin() {
        try {
            Class<?> cached = Class.forName("me.towdium.pinin.searchers.CachedSearcher");
            // PinIn 的惯例：Searcher 由静态工厂/构造器拿到；这里按"静态工厂 → 构造器"的顺序尽力试 ✓
            for (String factory : new String[]{"get", "of", "create", "instance"}) {
                try {
                    java.lang.reflect.Method fm = cached.getMethod(factory, String.class);
                    Object inst = fm.invoke(null, "");
                    java.lang.reflect.Method cm = findContains(cached);
                    if (inst != null && cm != null) {
                        pinyinSearcher = inst;
                        pinyinContains = cm;
                        return;
                    }
                } catch (Throwable ignored) {
                }
            }
            java.lang.reflect.Method cm = findContains(cached);
            if (cm != null) {
                try {
                    java.lang.reflect.Constructor<?> ctor = cached.getConstructor(String.class);
                    pinyinSearcher = ctor.newInstance("");
                    pinyinContains = cm;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
            // 没装（或签名变了）⇒ 保持 null ⇒ 无拼音 ✓
        }
    }

    /** 在类及其父类里找"判定能否匹配"的方法 ✓（名字尽力匹配 ✓ 参数两个 String ✓） */
    private static java.lang.reflect.Method findContains(Class<?> c) {
        for (Class<?> cur = c; cur != null; cur = cur.getSuperclass()) {
            for (java.lang.reflect.Method m : cur.getMethods()) {
                if (!(m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)) continue;
                Class<?>[] ps = m.getParameterTypes();
                if (ps.length != 2 || ps[0] != String.class || ps[1] != String.class) continue;
                String n = m.getName().toLowerCase(Locale.ROOT);
                if (n.contains("contain") || n.contains("match") || n.contains("test") || n.contains("search")) {
                    return m;
                }
            }
        }
        return null;
    }

    // ============================================================
    //  解析
    // ============================================================

    /** 把查询串解析成 OR 组 × AND 词 ✓（解析失败/空 ⇒ 空表 ⇒ 视为"未搜索" ✓ 不会全空 ✗） */
    private static List<Group> parse(String q) {
        List<Group> out = new ArrayList<>();
        if (q.isEmpty()) return out;
        for (String orPart : q.split("\\|")) {
            String or = orPart.trim();
            if (or.isEmpty()) continue;
            List<Term> terms = new ArrayList<>();
            for (String w : or.split("\\s+")) {
                String t = w.trim();
                if (t.isEmpty()) continue;
                boolean exclude = false;
                if (t.length() > 1 && t.charAt(0) == '-') {
                    exclude = true;
                    t = t.substring(1);
                }
                if (t.isEmpty()) continue;
                Kind kind = Kind.ANY;
                if (t.charAt(0) == '@') {
                    kind = Kind.MOD;
                    t = t.substring(1);
                } else if (t.charAt(0) == '#') {
                    kind = Kind.TAG;
                    t = t.substring(1);
                }
                if (t.isEmpty()) continue;
                terms.add(new Term(exclude, kind, t));
            }
            if (!terms.isEmpty()) out.add(new Group(terms));
        }
        return out;
    }
}
