package com.mofengbaizhi.tinkersnewlife.util;

/**
 * 伤害管线状态：<b>"当前正在结算混沌之流自己重发的那一发嵌套伤害"</b> 的标记。
 *
 * <h2>为什么需要它（用户报告：改判类型后重发，数值被放大两次 ⇒ 爆炸）</h2>
 *
 * 「混沌之流」（{@code content/modifier/events/ChaosFlowHandler}）现在的规则是
 * <b>每次攻击随机二选一、只结算一次</b>：抽到"物理"就放行原始那一次 ✗；
 * 抽到"法术"就<b>在 {@code LivingHurtEvent} 里取消原始那一次、换一个学派的伤害源、
 * 用同样的总数值自己调一次 {@code target.hurt(...)}</b> ✓。
 *
 * <p>问题出在"法术"那一路：那一发会**把整条伤害管线重跑一遍** ⇒ 全模组所有
 * "在伤害事件里改数值"的处理器（放大类 {@code setAmount(原值 × k)} / 附加类 {@code setAmount(原值 + bonus)}）
 * 会**对同一发命中各算两次** ✗✗ —— 上游已经改过一次（这次事件），我们重发时它们又改一次 ✗
 * ⇒ "黑闪 ^2.5"会被幂两次、"群星之子 ×2^级"会被乘两次 ⇒ <b>数值爆炸</b> ✗。
 *
 * <p>（2026-09-19 之前的老实现更糟：它是"1 段物理 + 每学派 1 段"逐段 {@code hurt()} ✗，
 * 于是"段数 × 增幅 = 指数级膨胀" ✗。现在没有"段"了 ⇒ 只剩"重发一次"这一个必须堵的口子 ✓。）
 *
 * <h2>做法：同线程深度标记（最可靠、零同步开销）</h2>
 *
 * 混沌之流重发的那一发是<b>同线程同步</b>发生的（Forge 事件派发是同步的：
 * {@code hurt → LivingHurtEvent → 我们的监听器 → hurt → …}）✓ ——
 * 所以一个 {@link ThreadLocal} 布尔量就足够精确，不需要任何锁、也不会误伤别的线程 ✗。
 *
 * <p>语义约定（写在这里防止以后跑偏）：
 * <ul>
 *   <li>{@link #enter()}/{@link #exit()} 由混沌之流在<b>重发那一发的前后</b>包起来 ✓
 *       （{@code exit} 必须在 {@code finally} 里 ✓）；</li>
 *   <li>标期内，**我们自己的**放大类 / 附加类处理器一律 {@link #skipNested()} 早退 ✓
 *       ⇒ "一次命中只被放大一次、附加伤害只算一次" ✓；</li>
 *   <li><b>只保护我们自己</b> ✓：外部模组在 {@code LivingHurtEvent}/{@code LivingDamageEvent} 里的
 *       增幅**看不到这个标记** ✗（它们是别的 classloader 里的代码，不可能读我们的 ThreadLocal）——
 *       这一条在备忘录里如实记录 ✗，见 {@code ChaosFlowHandler} 的类注释。</li>
 * </ul>
 */
public final class DamagePipeline {

    private DamagePipeline() {
    }

    /**
     * 是否正在结算"混沌之流造出来的嵌套伤害"。
     * <p>用 {@code Boolean} 而不是 {@code boolean}：{@link ThreadLocal} 的初始值语义更省事，
     * 但比较时一律用 {@code == Boolean.TRUE}（避免拆箱 + null 风险 ✓）。
     */
    private static final ThreadLocal<Boolean> NESTED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** 进入"嵌套结算"（由混沌之流在重发那一发 {@code hurt()} 前调用） */
    public static void enter() {
        NESTED.set(Boolean.TRUE);
    }

    /** 退出"嵌套结算"（必须在 {@code finally} 里调用 ✓，否则标记会留在服务端主线程上 ✗） */
    public static void exit() {
        NESTED.set(Boolean.FALSE);
    }

    /**
     * 当前这一发伤害是不是"混沌之流改判后重发的内层那一发"？
     * <p>给所有"会改数值"的处理器在<b>方法最开头</b>用：
     * {@code if (DamagePipeline.skipNested()) return;}
     */
    public static boolean isNested() {
        return NESTED.get() == Boolean.TRUE;
    }

    /**
     * 给"会改数值"的处理器用的早退判断 —— 与 {@link #isNested()} 同义，
     * 但名字把意图写在调用处（"内层重发的那一发跳过"），
     * 免得以后有人把 {@code isNested()} 当成"要不要记日志"之类的东西用 ✗。
     */
    public static boolean skipNested() {
        return isNested();
    }
}
