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

    // ============================================================
    //  §1117 搜索框的屏幕矩形
    // ============================================================
    /**
     * 由 {@code GuiSmelteryTankSearchMixin} **每帧写入** ✓（那个 mixin 手上同时有 `GuiGraphics` 与流体列坐标 x/y/width ✓），
     * 供 {@code HeatingStructureScreenSearchMixin} 做"点击/按键是否命中搜索框"的判定 ✓。
     * <p>⚠ 为什么不直接在 screen mixin 里算坐标 ✗：那需要 shadow **原版**的 `leftPos/topPos` ✗（运行期是混淆名 ✗
     * ⇒ 会抛 {@code InvalidMixinException} 把整个 mixin 丢掉 ✗ 见 §1117 ✓），
     * 而流体列的 `x/y/width` 是**匠魂自己的私有字段** ✗（**跨类**也 shadow 不到 ✗）⇒ 只能由持有它的那个 mixin 写出来 ✓。
     */
    private static volatile int boxX = 8;
    private static volatile int boxY = 2;
    private static volatile int boxW = 106;
    private static volatile int boxH = 14;
    /**
     * §1117e <b>画框那一刻的姿态平移量</b> ✓（就是 GUI 左上角 ✓ 由 `GuiGraphics.pose().last().pose()` 的 m30/m31 取 ✓）。
     *
     * <p>⚠ 为什么不用 {@code getGuiLeft()/getGuiTop()} ✗：用户实测（日志 ✓）
     * 「点击 mouse=(203,9) 框=(8,-5,106x14) 命中=false」⇒ 那套换算**没有生效** ✗
     * （要么方法没被调用到、要么那俩方法给的不是这个值 ✗）。
     * ⇒ 改成**直接读绘制姿态的平移** ✓ —— 那是**画框时真实使用**的平移 ✓ ⇒ 与眼睛看到的框位置**必然一致** ✓✓。
     */
    private static volatile float boxTx = 0F;
    private static volatile float boxTy = 0F;

    public static void setBoxRect(int x, int y, int w, int h, float tx, float ty) {
        boxX = x;
        boxY = y;
        boxW = w;
        boxH = h;
        boxTx = tx;
        boxTy = ty;
    }

    /** 画框时的姿态平移（GUI 左上角 ✓） */
    public static float boxTx() { return boxTx; }

    public static float boxTy() { return boxTy; }

    public static int boxX() { return boxX; }

    public static int boxY() { return boxY; }

    public static int boxW() { return boxW; }

    public static int boxH() { return boxH; }

    // ============================================================
    //  §1117c 搜索框聚焦状态（跨 mixin 共享 ✓）
    // ============================================================
    /**
     * 搜索框是否聚焦 ✓ —— 由 {@code HeatingStructureScreenSearchMixin} 维护 ✓（打开界面即聚焦 ✓），
     * 由 {@link ScreenKeyInputMixin}（退格/回车/Esc ✓）与
     * {@code KeyboardHandlerImeMixin}（**所有真实字符** 含输入法汉字 ✓）读取 ✓。
     *
     * <p>⚠ 血泪教训（写死 ✓）：`keyPressed`/`charTyped` **不是** `HeatingStructureScreen` 自己声明的方法 ✗
     * ⇒ 在目标类里注入会 {@code could not find any targets} ✗ ⇒ `require = 1` ⇒ **整个 mixin 被丢弃** ✗
     * ⇒ 表现为"框能画、字打不进" ✓；而字符的**真身**在 `KeyboardHandler#charTyped`（`m_90889_` ✓）。
     */
    private static volatile boolean focused;

    public static boolean isFocused() { return focused; }

    public static void setFocused(boolean f) { focused = f; }

    /** 当前打开的界面是不是"加热结构界面"（熔炼炉/熔铸炉 ✓ 两者同一个类 ✓） */
    public static boolean isHeatingStructureScreen(Object screen) {
        if (screen == null) return false;
        // 用 contains 而不是 equals ✓ —— 万一匠魂那边有子类/包装类也不会误判 ✗
        return screen.getClass().getName().contains("HeatingStructureScreen");
    }

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
    /**
     * §1118b <b>拼音改成复用共享工具</b> ✓ —— 原来这里有一份自己的反射实现 ✗，
     * 现在全项目统一走 {@link PinyinHelper} ✓（它内部就是
     * {@code me.towdium.pinin.PinIn} 的 {@code new PinIn()} ＋ {@code contains(String,String)} ✓
     * —— 签名已实测确认 ✓ 见 §1117h/§1117r ✓）。
     *
     * <p>⚠ 用户口径照旧 ✓：「**不加通用拼音搜索就不要有拼音搜索能力**」✓ ——
     * 没装 ⇒ {@link PinyinHelper#matches} 直接返回 false ✓（**故意不做自带拼音表** ✗）。
     */
    private static boolean pinyinMatches(String name, String s) {
        return PinyinHelper.matches(name, s);
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
