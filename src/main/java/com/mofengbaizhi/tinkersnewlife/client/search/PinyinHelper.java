package com.mofengbaizhi.tinkersnewlife.client.search;

import java.util.Locale;

/**
 * §1118b <b>通用拼音搜索桥</b> ✓ —— 供全项目所有搜索框复用 ✓（用户口径：把其他搜索也接上拼音 ✓）。
 *
 * <h2>它接的是哪个模组 ✓</h2>
 * 「**通用拼音搜索**」＝ **Just Enough Characters**（modId `jecharacters` ✓），
 * 自带拼音库 {@code me.towdium.pinin.PinIn} ✓ —— 签名**已实测确认** ✓（不是猜的 ✗）：
 * <pre>
 * me.towdium.pinin.PinIn
 *   public PinIn()                                  // ⭐ 无参构造器 ✓
 *   public boolean contains(String s1, String s2)    // ⭐ s1（文本）里是否"拼音包含" s2（查询）✓
 * </pre>
 * ⇒ 全拼（`rongtie` ✓）与声母缩写（`rt` ✓）都能命中 ✓（用户在 NL 包实测有效 ✓）。
 *
 * <h2>⚠ 三条硬规矩（血泪换来的 ✓）</h2>
 * <ol>
 *   <li><b>没装就没有拼音</b> ✗ —— 用户口径 ✓：**不做自带拼音表** ✗ ⇒ {@link #available()} 为 false 时
 *       调用方直接当"没有拼音"处理 ✓（本类所有方法都**不会抛异常** ✓）；</li>
 *   <li><b>只调用、不复制</b> ✓ —— 那几个类不是给第三方用的公开 API ✗（它是用 ASM 改 JEI 搜索的 ✓）
 *       ⇒ 全程 {@code try/catch} ✓，哪天它改了包名/签名 ⇒ 最坏只是**拼音失效** ✓ 绝不影响搜索框本身 ✗；</li>
 *   <li><b>不做无效开销</b> ✓ —— 查询含非 ASCII（玩家直接打汉字 ✓）时**不必**走拼音 ✓
 *       （{@link #matches} 内部已短路 ✓）。</li>
 * </ol>
 */
public final class PinyinHelper {

    private PinyinHelper() {
    }

    /** 解析结果缓存（只解析一次 ✓ 失败也记着 ✓ 免得反复 Class.forName ✓） */
    private static volatile boolean resolved = false;
    private static volatile Object pinIn = null;
    private static volatile java.lang.reflect.Method contains = null;

    /** 装了「通用拼音搜索」且句柄可用 ✓ */
    public static boolean available() {
        resolve();
        return pinIn != null && contains != null;
    }

    /**
     * 这段文本是否"**拼音包含**"该查询 ✓ —— 没装模组 / 参数为空 / 查询非 ASCII 时一律返回 false ✓。
     *
     * @param text  被搜索的文本（通常是物品/流体的显示名 ✓ 可含汉字 ✓）
     * @param query 玩家输入的查询（小写 ✓）
     */
    public static boolean matches(String text, String query) {
        if (text == null || query == null || text.isEmpty() || query.isEmpty()) return false;
        if (!isAscii(query)) return false;                 // 直接打汉字时无需拼音 ✓
        if (!containsNonAscii(text)) return false;         // 文本里本来就没汉字 ⇒ 交给普通匹配 ✓
        resolve();
        java.lang.reflect.Method m = contains;
        Object ctx = pinIn;
        if (m == null || ctx == null) return false;
        try {
            Object r = m.invoke(ctx, text, query);
            return r instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;                                  // 出了任何事都只是"这次没拼音命中" ✓
        }
    }

    /** 一次性解析句柄 ✓（没装 ⇒ 两个字段保持 null ⇒ 永久返回 false ✓） */
    private static void resolve() {
        if (resolved) return;
        synchronized (PinyinHelper.class) {
            if (resolved) return;
            try {
                Class<?> cls = Class.forName("me.towdium.pinin.PinIn");
                Object ctx = cls.getConstructor().newInstance();
                java.lang.reflect.Method m = cls.getMethod("contains", String.class, String.class);
                pinIn = ctx;
                contains = m;
            } catch (Throwable ignored) {
                // 没装（或签名变了）⇒ 保持 null ⇒ 没有拼音 ✓
            }
            resolved = true;
        }
    }

    private static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 127) return false;
        }
        return true;
    }

    private static boolean containsNonAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 127) return true;
        }
        return false;
    }

    /** 便利方法 ✓：把查询规整成"小写 + 去首尾空格"的形态 ✓（各屏口径统一 ✓） */
    public static String normalize(String query) {
        return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    }
}
