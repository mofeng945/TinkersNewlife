package com.mofengbaizhi.tinkersnewlife.util;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>虚空金属盔甲特性诊断日志</b>（§750）—— 临时排查用 ✓ 定位完就可以关掉 ✓
 *
 * <h2>为什么要有它</h2>
 * 用户报告：穿着<b>虚空金属巫师法袍</b>时"一直在发出一些声音" ✓。
 * 我已核过：<b>我们自己的代码一行 {@code playSound} 都没有</b> ✓，
 * 而<b>诡厄</b>的「虚空之蚀」（{@code goety:void_touched}）自带
 * {@code VOID_TOUCHED_ACTIVATE} / {@code _LOOP} / {@code _DEACTIVATE} 三个音效 ✓
 * ⇒ 高度怀疑是"我们每 40 tick 清一次它 ⇒ 它每次被清都响一次" ✓。
 * <p>⇒ 这个类就是用来把"到底哪条路径在动"记到日志里的 ✓：
 * <ul>
 *   <li>{@code deny:*} —— 我们<b>拒绝施加</b>了某个效果 ✓（免疫生效 ✓）；</li>
 *   <li>{@code clean:*} —— 我们<b>真的清掉了</b>一个已经挂在身上的效果 ✓
 *       ⭐ <b>如果这条每 2 秒出现一次 ⇒ 就是它在制造声音 ✓</b>；</li>
 *   <li>{@code dodge} / {@code repair} / {@code wane} —— 另外三条特性有没有在频繁触发 ✓。</li>
 * </ul>
 *
 * <h2>怎么用</h2>
 * 日志里搜 <b>{@code [诊断·虚空金属]}</b> ✓ 一行一条 ✓；
 * 同一个 key（同一条路径）最多每 <b>5 秒</b>打一行 ✓ 被压掉多少条会写在行尾 ✓ 不会刷爆日志 ✓。
 * <p>⚠ 排查完把 {@link #ENABLED} 改成 {@code false} 即可整体静音 ✓（一行的事 ✓）。
 */
public final class VoidArmorDiag {

    /** 总开关 ✓（排查期 true ✓ 定位完改 false ✓） */
    public static final boolean ENABLED = true;

    /** 同一个 key 最多每这么多毫秒打一行 ✓ */
    private static final long WINDOW_MS = 5_000L;

    private static final String PREFIX = "[诊断·虚空金属] ";

    /** key → {上次打印时刻, 被压掉的条数} */
    private static final Map<String, long[]> STATE = new ConcurrentHashMap<>();

    private VoidArmorDiag() {
    }

    /**
     * 记一条诊断 ✓（自动按 key 限流 ✓）。
     *
     * @param key    限流用的键（同一条路径用同一个 key ✓）
     * @param format 消息模板（{@code {}} 占位 ✓）
     */
    public static void log(String key, String format, Object... args) {
        if (!ENABLED) return;
        try {
            long now = System.currentTimeMillis();
            long[] state = STATE.computeIfAbsent(key, k -> new long[]{0L, 0L});
            if (now - state[0] < WINDOW_MS) {
                state[1]++;                                   // 压掉一条 ✓ 稍后一并汇报 ✓
                return;
            }
            long suppressed = state[1];
            state[0] = now;
            state[1] = 0L;
            String message = format;
            for (Object arg : args) {
                int at = message.indexOf("{}");
                if (at < 0) break;
                message = message.substring(0, at) + arg + message.substring(at + 2);
            }
            TinkersNewlife.LOGGER.info("{}{}{}", PREFIX, message,
                    suppressed > 0 ? "（最近 5 秒还压掉了 " + suppressed + " 条同类）" : "");
        } catch (Throwable ignored) {
            // 诊断代码本身绝不许影响游戏 ✗
        }
    }

    /**
     * <b>短调用栈</b>（§757）—— 只留"可能与业务有关"的帧 ✓ 用来点名"到底是谁在干这件事" ✓。
     *
     * <p>跳过：{@code java.} / {@code jdk.} / {@code net.minecraft.} / {@code net.minecraftforge.} /
     * {@code org.spongepowered} / {@code com.mojang} / {@code cpw.mods}（都是框架噪声 ✗）；
     * 保留模组自己的帧（含我们自己的 ✓ 也含别的模组 ✓ —— 排查时正是要找它 ✓）。
     *
     * @param maxFrames 最多保留几帧
     * @return 形如 {@code " ⇐ 类#方法:行 ⇐ …"} 的字符串（取不到时返回提示串 ✓ 绝不抛异常 ✓）
     */
    public static String shortStack(int maxFrames) {
        try {
            StringBuilder sb = new StringBuilder();
            int kept = 0;
            for (StackTraceElement e : new Throwable().getStackTrace()) {
                String cn = e.getClassName();
                if (cn.startsWith("java.") || cn.startsWith("jdk.")
                        || cn.startsWith("net.minecraft.") || cn.startsWith("net.minecraftforge.")
                        || cn.startsWith("org.spongepowered") || cn.startsWith("com.mojang")
                        || cn.startsWith("cpw.mods")) {
                    continue;
                }
                sb.append(" ⇐ ").append(cn).append('#').append(e.getMethodName())
                        .append(':').append(e.getLineNumber());
                if (++kept >= maxFrames) break;
            }
            return kept == 0 ? "（只有框架帧 ✗）" : sb.toString();
        } catch (Throwable t) {
            return "（取调用栈失败：" + t.getClass().getSimpleName() + "）";
        }
    }
}
