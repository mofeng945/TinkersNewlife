package com.mofengbaizhi.tinkersnewlife.content.energy;

/**
 * <b>「别家模组的能量 → FE」适配器</b>的统一形状（§557）。
 *
 * <p>万用能量转化器（{@code energy_converter}）每 tick 会问每一个已接上的适配器
 * "相邻方块里能拿走多少 FE？" ✓ 适配器<b>自己去扣它的源能量</b>（原子操作 ✓ 不会出现
 * "算出有 3 FE、扣的时候却不够"这种对不上账 ✗）。
 *
 * <h2>为什么是"拉"而不是"推"</h2>
 * 这些外部能量的持有者（通用机械的线缆与机器、Create 的传动轴…）本来就没有统一的"推给我"的
 * 标准入口 ✗ ⇒ 我们按它们自己的方向来：<b>我们自己当主动方去拉</b> ✓
 * —— 这也正是通用机械自己的线缆把电送给相邻机器的做法 ✓。
 * （对外<b>输出</b> FE 那一路仍然是标准的"我们推给邻居" ✓ 见 {@code EnergyConverterBlockEntity}。）
 *
 * <h2>三条纪律（每一家都必须遵守）</h2>
 * <ol>
 *   <li><b>只反射</b>：本模组的 {@code build.gradle} 里<b>没有</b>任何可选模组的 compileOnly
 *       依赖 ✗（用户对"编译不过"零容忍 ✗）⇒ 这些类只能用 {@code Class.forName} + {@code Method}
 *       摸对方的 API ✓ 见范例 {@code integration/irons_spellbooks/IronSpellsSpellAccess}、
 *       {@code util/SoulEnergyBridge} ✓；</li>
 *   <li><b>逐步 try/catch</b>：每一个反射步骤单独包一层 ✓ 某一家版本不兼容时只让<b>这一路</b>
 *       失效 ✓ 不许连累其它三家、更不许崩 ✗；</li>
 *   <li><b>安静跳过</b>：模组不在场 / 类名或方法名对不上 ⇒ {@link #isAvailable()} 返回 false
 *       ✓ 转化器只是少一条输入路 ✓ 不报错、也不刷屏 ✗（只在启动时记一行 INFO ✓ 见
 *       {@code EnergyInputs}）。</li>
 * </ol>
 *
 * <h2>单位与汇率</h2>
 * 每一家源能量的单位都不同（J / EU / AE / RPM ✓）⇒ 适配器<b>自己</b>负责把它折成 FE ✓
 * 汇率常量一律从 {@link EnergyUnits.Fe} 取 ✓（绝不在这里写魔法数字 ✗）。
 *
 * <p>⚠ <b>RF / Tesla / μI / FF 没有适配器</b> ✗ —— 它们就是 FE 的别名（1:1 ✓ 用户已确认 ✓）
 * ⇒ 它们走的是标准 {@code ForgeCapabilities.ENERGY} 那一路 ✓ 本接口不掺和 ✓。
 */
public interface EnergyInputAdapter {

    /** 这一家是否真的接上了（类/方法都反射到位了 ✓ 且模组在场 ✓） */
    boolean isAvailable();

    /**
     * 检查并<b>取出</b>邻格里的能量，折成 FE。
     *
     * @param simulate true = <b>只算不扣</b>（用于 tooltip / 预检查 ✓ 世界一动不动 ✓）
     * @return 这一 tick 最多能拿到多少 FE（≥ 0 ✓ 0 = 没东西 / 没接上 / 参数为 0 ✓）
     */
    int drainFe(EnergyConverterContext ctx, int maxFe, boolean simulate);

    /** 启动日志用的一句话（"为什么接上/为什么没接上" ✓ 只说一次 ✓） */
    String probeNote();
}
