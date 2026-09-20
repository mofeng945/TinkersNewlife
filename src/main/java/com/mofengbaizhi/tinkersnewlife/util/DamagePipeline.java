package com.mofengbaizhi.tinkersnewlife.util;

/**
 * 伤害管线状态：<b>"当前正在结算混沌之流自己造出来的那几段嵌套伤害"</b> 的标记。
 *
 * <h2>为什么需要它（用户报告：附加伤害被拆段 + 每段再吃一遍增幅 ⇒ 数值爆炸）</h2>
 *
 * 「混沌之流」（{@code content/modifier/events/ChaosFlowHandler}）的设计是
 * <b>把一次命中均分成 1 段物理 + 每学派 1 段</b> ✗ —— 它是**在 {@code LivingHurtEvent} 里
 * 取消原始伤害、再自己调 {@code target.hurt(...)} 逐段打** ✓，
 * 于是每一段都会**把整条伤害管线重跑一遍** ⇒ 全模组所有"在伤害事件里改数值"的处理器
 * （放大类 {@code setAmount(原值 × k)} / 附加类 {@code setAmount(原值 + bonus)}）
 * 会对<b>每一段各生效一次</b> ✗✗。
 *
 * <p>两个后果：
 * <ol>
 *   <li><b>附加伤害被一起拆段</b>：别人（饰品 / 其它特性）加进来的那部分被当成"基数"一起均分 ✗；</li>
 *   <li><b>每段再吃一遍增幅</b>：段数 × 增幅 ⇒ 例如"黑闪 ^2.5"或"群星之子 ×2^级"
 *       会在 10 段上各乘一次 ⇒ <b>指数级膨胀</b> ✗。</li>
 * </ol>
 *
 * <h2>做法：同线程深度标记（最可靠、零同步开销）</h2>
 *
 * 混沌之流施加的嵌套 {@code hurt()} 是<b>同线程同步</b>发生的（Forge 事件派发是同步的：
 * {@code hurt → LivingHurtEvent → 我们的监听器 → hurt → …}），
 * 而且滞后发放路径（{@code LevelTickEvent} END）也跑在**服务端主线程**上 ✓ ——
 * 所以一个 {@link ThreadLocal} 布尔量就足够精确，不需要任何锁、也不会误伤别的线程 ✗。
 *
 * <p>语义约定（写在这里防止以后跑偏）：
 * <ul>
 *   <li>{@link #enter()}/{@link #exit()} 由混沌之流在<b>施加每一段之前/之后</b>包起来 ✓；</li>
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

    /** 进入"嵌套段结算"（由混沌之流在每次 {@code hurt()} 前调用） */
    public static void enter() {
        NESTED.set(Boolean.TRUE);
    }

    /** 退出"嵌套段结算"（必须在 {@code finally} 里调用 ✓，否则标记会留在服务端主线程上 ✗） */
    public static void exit() {
        NESTED.set(Boolean.FALSE);
    }

    /**
     * 当前这一发伤害是不是"混沌之流造出来的内层段"？
     * <p>给所有"会改数值"的处理器在<b>方法最开头</b>用：
     * {@code if (DamagePipeline.skipNested()) return;}
     */
    public static boolean isNested() {
        return NESTED.get() == Boolean.TRUE;
    }

    /**
     * 给"会改数值"的处理器用的早退判断 —— 与 {@link #isNested()} 同义，
     * 但名字把意图写在调用处（"内层段跳过"），
     * 免得以后有人把 {@code isNested()} 当成"要不要记日志"之类的东西用 ✗。
     */
    public static boolean skipNested() {
        return isNested();
    }
}
