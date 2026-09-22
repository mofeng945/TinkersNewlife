package com.mofengbaizhi.tinkersnewlife.content.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * <b>魔力台座的「环境能量来源」——本轮唯一的新可插拔点</b>（用户口径：台座 + 一个可替换的默认来源 ✓）。
 *
 * <h2>它是什么</h2>
 * 台座自己不"会"发电 ✗ —— 它只是每 20 tick（= 1 秒）问一次<b>当前的来源</b>：
 * 「在 {@code pos} 这一格，你这一秒能给我多少 EE？」⇒ 拿到多少就往里灌多少（详见
 * {@code block.ElderManaPedestalBlockEntity}）✓
 * <p>所以"能量从哪来"这件事<b>完全</b>由本接口的实现决定 ✓，
 * 台座方块 / 方块实体里<b>没有任何</b>关于光照、夜晚、天气的判定 ✗ —— 将来加新来源不用动它们 ✓。
 *
 * <h2>默认实现（默认选中）</h2>
 * {@link LightLevelEnergySource}（id {@code "light_level"}）：<b>亮度越低越快</b> ✓
 * <pre>
 *     rate = ModConfig.pedestalChargeMaxPerSecond()
 *          × (ModConfig.pedestalLightCap() − 台座上方那格的亮度) / ModConfig.pedestalLightCap()
 * </pre>
 * 用户口径 2026-09-21：「亮度越低，充能速度越快」—— 因此<b>没有</b>"必须夜晚 / 必须露天"这类判断 ✓
 * （夜里露天本来就暗、白天在漆黑洞穴里也暗 ⇒ 亮度这一个量已经把两者都覆盖了 ✓）。
 *
 * <h2>以后接「能量转化系统」要改哪里（一两句）</h2>
 * 写一个新的 {@code implements AmbientEnergySource}（把"别的 mod 的魔力/能量 → EE"的换算写在那里，
 * 换算<b>必须</b>经 {@link EnergyUnits} 这张唯一换算表 ✓），在
 * {@link AmbientEnergySources} 里 {@code register(...)} 一行，然后把配置 {@code elder_crystal.pedestal_source}
 * 改成新来源的 {@link #id()} 即可 ✓ —— 台座方块/方块实体一行都不用动 ✓。
 *
 * <p>⚠ 约定：本接口只<b>产出</b> EE（正数），不负责扣任何人的账 ✓。
 * 台座再自己决定往哪灌（台座上的水晶物品优先，其次紧邻的水晶方块）✓。
 * 灌不进去的部分台座会直接丢掉 ⇒ 实现方<b>不必</b>关心容量 ✓（也就不会有"凭空蒸发别人的账"✗）。
 */
public interface AmbientEnergySource {

    /**
     * 来源 id（写进配置 {@code elder_crystal.pedestal_source}、日志与手册 ✓ 建议小写下划线 ✓）。
     * <p>同一个 id 重复注册会覆盖前一个 ✓（便于数据包/附属模组替换默认来源 ✓）。
     */
    String id();

    /**
     * 该来源在 {@code pos} 这一格、<b>每秒</b>能提供多少 EE。
     *
     * @param level 台座所在的维度（服务端调用；实现里可以随便读光照/天气/时间 ✓）
     * @param pos   台座自身所在的方块坐标 ✓（要判定"水晶所在的那一格"请自己 {@code pos.above()} ✓
     *              —— 台座自己脚下的那格亮度会被台座自身影响，而水晶是浮在台座<b>上方</b>的 ✓）
     * @return 每秒产出的 EE；<b>0 = 当前不工作</b>（台座会立刻停止蓄水并熄灭粒子 ✓）；不接受负数 ✗
     */
    double eePerSecond(Level level, BlockPos pos);

    /** 当前是否"在工作窗口内"（= {@link #eePerSecond} &gt; 0）；供 tooltip / 玉 / 手册一类的查询方复用 ✓ */
    default boolean activeAt(Level level, BlockPos pos) {
        return eePerSecond(level, pos) > 0.0D;
    }

    /** 手册/调试用的短名（可翻译键；手册里直接写死中文也行 ✓ 默认按 id 拼一个键 ✓） */
    default String nameKey() {
        return "energy_source.tinkersnewlife." + id();
    }
}
