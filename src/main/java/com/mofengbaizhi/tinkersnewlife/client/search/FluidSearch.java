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
    //  §1117m ★ 真正的 EditBox（照抄仓库既有搜索框的做法 ✓）
    // ============================================================
    /**
     * ⚠ §1117m <b>换成 MC 原生的 `EditBox`</b> ✓ —— 用户一句话点醒 ✓：
     * 「**我构筑术式不是也写过搜索框吗，为什么不能模仿**」✓
     * ⇒ 仓库里 `ConstructSelectScreen` / `WuWeiScreen` / `QuantumVaultScreen` / `DimensionPassScreen`
     * **全都是 `new EditBox(font, x, y, w, h, …)` ＋ `setResponder(…)`** ✓ ——
     * `EditBox` **自己**处理按键 ✓ 字符 ✓ 光标 ✓ 退格 ✓ **以及输入法中文** ✓✓
     * ⇒ 我前面手搓"按键码翻译 + charTyped 注入"8 轮**全是绕远路** ✗（中文注定进不来 ✓）。
     *
     * <p>由 `HeatingStructureScreenSearchMixin` 创建并塞进界面的 `children()` ✓（`Screen#children()` 是公开方法 ✓
     * 所以**不需要** shadow 原版的 `addRenderableWidget` ✗ —— 那正是 §1117 把 mixin 搞丢的坑 ✓）；
     * 由 `GuiSmelteryTankSearchMixin` 每帧摆好位置并 `render` ✓。
     */
    private static volatile net.minecraft.client.gui.components.EditBox editBox;

    public static void setEditBox(net.minecraft.client.gui.components.EditBox box) {
        editBox = box;
    }

    public static net.minecraft.client.gui.components.EditBox getEditBox() {
        return editBox;
    }

    /**
     * §1117r <b>拼音自检</b> ✓ —— 用户要求「**接下来尝试攻克中文搜索**」✓ 的第一步 ✓：
     * 中文名的检索靠"**打拼音命中汉字**"✓（「通用拼音搜索」的 `me.towdium.pinin.PinIn` ✓）。
     * <p>⚠ 但这条链**从来没被实测过** ✗（我只确认过 `PinIn` 有无参构造器与 `contains(String,String)` ✓）：
     * `new PinIn()` 是否**自带词典** ✗ / `contains` 的语义是否为"文本里拼音包含查询" ✗ —— 都不确定 ✓
     * ⇒ 界面打开时跑一次自检 ✓ 把结果写进日志 ✓，一眼定性 ✓。
     */
    public static void selfTestPinyin() {
        try {
            if (!pinyinResolved) {
                synchronized (FluidSearch.class) {
                    if (!pinyinResolved) {
                        resolvePinyin();
                        pinyinResolved = true;
                    }
                }
            }
            if (pinyinContains == null || pinyinSearcher == null) {
                diag("拼音自检 ✗ 拿不到 me.towdium.pinin.PinIn（未装「通用拼音搜索」⇒ 按用户口径就是没有拼音 ✓）");
                return;
            }
            Object rt = pinyinContains.invoke(pinyinSearcher, "熔融铁", "rt");
            Object rongtie = pinyinContains.invoke(pinyinSearcher, "熔融铁", "rongtie");
            Object rr = pinyinContains.invoke(pinyinSearcher, "熔融铁", "rr");
            Object miss = pinyinContains.invoke(pinyinSearcher, "熔融铁", "zzz");
            diag("拼音自检 ✓ PinIn 可用：contains(熔融铁,rt)=" + rt + " (rongtie)=" + rongtie
                    + " (rr)=" + rr + " (zzz)=" + miss);
        } catch (Throwable t) {
            diag("拼音自检 ✗ 异常：" + t);
        }
    }

    // ============================================================
    //  §1117c 搜索框聚焦状态（跨 mixin 共享 ✓）
    // ============================================================
    /**
     * 搜索框是否聚焦 ✓ —— 由 {@code HeatingStructureScreenSearchMixin}（init/点击 ✓）维护 ✓，
     * 由 {@code ScreenSearchInputMixin}（挂在 **`Screen`** 层 ✓）读取 ✓。
     *
     * <p>⚠ 为什么输入要挂 `Screen` ✗：用户实测 + 日志实证 ✓
     * —— `keyPressed`/`charTyped` **不是** `HeatingStructureScreen` 自己声明的方法 ✗（声明在 `Screen` ✓）
     * ⇒ 在目标类里注入会 {@code could not find any targets} ✗ ⇒ `require = 1` ⇒ **整个 mixin 被丢弃** ✗
     * ⇒ "框能画、但完全打不进字" ✓（正是当时的现场 ✓）。
     */
    private static volatile boolean focused;

    public static boolean isFocused() { return focused; }

    public static void setFocused(boolean f) { focused = f; }

    /** 当前打开的界面是不是"加热结构界面"（熔炼炉/熔铸炉 ✓ 两者同一个类 ✓） */
    public static boolean isHeatingStructureScreen(Object screen) {
        if (screen == null) return false;
        // §1117d 放宽：用 contains 而不是 equals ✓ —— 万一匠魂那边有子类/包装类也不会误判 ✗
        return screen.getClass().getName().contains("HeatingStructureScreen");
    }

    // ============================================================
    //  §1117d 临时诊断（最多 20 条 ✓ 定位完就删 ✗）
    // ============================================================
    private static final java.util.concurrent.atomic.AtomicInteger DIAG_COUNT =
            new java.util.concurrent.atomic.AtomicInteger();
    /**
     * §1117k <b>诊断按内容去重</b> ✗（血泪 ✓）：`HeatingStructureScreen` 的 `m_181908_` 是**每 tick 都调**的方法 ✗
     * ⇒ 之前"界面打开 ⇒ 自动聚焦=true"那条消息**每秒刷 20 条** ✗ ⇒ **把 20 条的额度全部吃光** ✗
     * ⇒ 真正需要的"过滤 …"那几行**一条都没留下** ✗ ⇒ 白测一轮 ✓。
     * <p>⇒ 现在同一条消息只记一次 ✓（`DIAG_SEEN` ✓），且上限提到 60 条 **不同**消息 ✓。
     */
    private static final java.util.Set<String> DIAG_SEEN =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 临时诊断 ✓ —— 用户多次报"无效"✗，而 mixin 应用情况只能靠运行时自述 ✓。
     * <p>⚠ 同一条消息**只记一次** ✓（去重 ✓ 见 {@link #DIAG_SEEN} ✓）；上限 60 条不同消息 ✓。
     */
    /**
     * §1117t <b>诊断已关闭</b> ✓（用户口径：「**成功了，可以关日志了**」✓）。
     *
     * <p>全部诊断输出都走这一个入口 ✓（`FluidSearch` 自身 ✓ ＋ 三个 mixin 的探针 ✓：
     * `keyPressed 到达` ✓ `过滤 查询=…` ✓ `渲染 字段高度=…` ✓ `字符(IME/键盘)` ✓ `拼音自检` ✓）
     * ⇒ 把它变成**空实现** ⇒ **一条日志都不会再打** ✓✓（`[搜索诊断]` 前缀彻底静默 ✓）。
     *
     * <p>⚠ 调用点暂时保留 ✗（无副作用 ✓ 便于将来再排查 ✓）；真要彻底清干净时，
     * 连调用点与 {@code DIAG_SEEN} 一起删即可 ✓ —— 那是纯体力活 ✓ 不影响功能 ✓。
     */
    @SuppressWarnings("unused")
    public static void diag(String msg) {
        // 诊断已关闭 ✓ 什么都不做 ✓（原来这里写 TinkersNewlife.LOGGER.info）
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
        // §1117o 每次查询变化都记一条 ✓（用户口径「过滤没任何效果」✗ ⇒ 必须看清"查询里到底进了什么"✓）
        diag("查询变化 ⇒ '" + q + "'");
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

    /**
     * 解析通用拼音搜索的拼音引擎 ✓（**签名已反编译实测** ✓ 不是猜的 ✗）：
     * <pre>
     * me.towdium.pinin.PinIn
     *   public PinIn()                                  // ⭐ 无参构造器 ✓
     *   public boolean contains(String s1, String s2)    // ⭐ s1（流体名）里是否"拼音包含" s2（查询）✓
     * </pre>
     * ⇒ 直接 `new PinIn()` ＋ `contains(流体名, 查询)` ✓，比原来那套"猜静态工厂"可靠得多 ✓。
     * <p>⚙ 仍全程 try/catch ✓：没装该模组 ⇒ `Class.forName` 抛异常 ⇒ 保持 null ⇒ **没有拼音** ✓（用户口径 ✓）。
     */
    private static void resolvePinyin() {
        try {
            Class<?> pinin = Class.forName("me.towdium.pinin.PinIn");
            Object ctx = pinin.getConstructor().newInstance();
            java.lang.reflect.Method contains = pinin.getMethod("contains", String.class, String.class);
            pinyinSearcher = ctx;
            pinyinContains = contains;
        } catch (Throwable ignored) {
            // 没装（或签名变了）⇒ 保持 null ⇒ 无拼音 ✓
        }
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
