package com.mofengbaizhi.tinkersnewlife.content.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * <b>魔力台座的「环境能量来源」——一条可插拔的充能支路</b>
 * （用户口径 §545：从"只选一条"改成"<b>多条并行叠加</b>"✓）。
 *
 * <h2>它是什么</h2>
 * 台座自己不"会"发电 ✗ —— 它只是每 20 tick（= 1 秒）问一次<b>所有启用的来源</b>：
 * 「在 {@code pos} 这一格，你这一秒能给我多少 EE？」⇒ 把各条加起来灌进水晶（详见
 * {@code block.ElderManaPedestalBlockEntity}）✓
 * <p>所以"能量从哪来"这件事<b>完全</b>由本接口的实现决定 ✓，
 * 台座方块 / 方块实体里<b>没有任何</b>关于光照、植物、流体的判定 ✗ —— 加新来源不用动它们 ✓。
 *
 * <h2>当前登记在册的来源（{@link AmbientEnergySources}）</h2>
 * <table border="1">
 *   <tr><th>id</th><th>实现</th><th>含义</th></tr>
 *   <tr><td>{@code light_level}</td><td>{@link LightLevelEnergySource}</td>
 *       <td>亮度越低越快（§545 前就有的默认来源；默认上限 0.5 EE/秒）</td></tr>
 *   <tr><td>{@code plant}</td><td>{@link PlantEnergySource}</td>
 *       <td>半径 5 内每株植物 0.5 EE/秒，同时给植物攒"凋灵度"，攒满它消失</td></tr>
 *   <tr><td>{@code tcon_fuel}</td><td>{@link TConFuelEnergySource}</td>
 *       <td>半径 5 内的匠魂流体容器：烧掉燃料，每个物品份给 0.5 EE</td></tr>
 *   <tr><td>{@code soul_death}</td><td>{@link SoulDeathEnergySource}</td>
 *       <td>半径 5 内有生物死亡 ⇒ {@code 最大生命 / 40} EE（任何生物，玩家也算）</td></tr>
 *   <tr><td>{@code demigod_player}</td><td>{@link DemigodPlayerEnergySource}</td>
 *       <td>半径 5 内的每名玩家（"半神之力"）0.5 EE/秒，多人叠加、无代价</td></tr>
 * </table>
 * <p>五条<b>同时生效、直接相加</b>✓ 没有总量闸门 ✓（用户口径：不设总上限 ✓）。
 * 每条各自有"开关 + 数值"配置 ✓（见 {@code ModConfig} 的 {@code [elder_crystal]} 段）。
 *
 * <h2>⚠ 关于 {@code simulate}（§545 新增，很重要）</h2>
 * 后四条来源<b>真的会改造世界</b>（烧燃料 / 让植物消失 / 清空待结算灵魂）⇒
 * 必须区分"只是问问速率"和"真的要结算一次"：
 * <ul>
 *   <li>{@code simulate == true} —— <b>只许读、绝不许改</b>：燃料不许扣、植物不许涨凋灵度、
 *       灵魂池不许清 ✓（给 tooltip / 玉 / 手册 / 调试用 ✓）；</li>
 *   <li>{@code simulate == false} —— 台座每秒结算那<b>唯一一次</b>调用 ✓ 允许扣燃料、涨凋灵度 ✓。</li>
 * </ul>
 * ⚠ 实现方纪律：<b>扣东西必须发生在 {@code simulate == false} 分支里</b> ✗，
 * 否则"看一眼速率"就会白白烧掉玩家的燃料 ✗。
 *
 * <p>⚠ 另一条约定：本接口只<b>产出</b> EE（正数），不负责扣任何人的账 ✓
 * （它扣的是<b>它自己的</b>资源：燃料 / 植物 / 灵魂 ✓）。
 * 台座再自己决定往哪灌（台座上的水晶物品优先，其次紧邻的水晶方块）✓。
 * 灌不进去的部分台座会直接丢掉 ⇒ 实现方<b>不必</b>关心容量 ✓（也就不会有"凭空蒸发别人的账"✗）。
 */
public interface AmbientEnergySource {

    /**
     * 来源 id（写进配置 {@code elder_crystal.pedestal_source} 允许清单、日志与手册 ✓ 建议小写下划线 ✓）。
     * <p>同一个 id 重复注册会覆盖前一个 ✓（便于数据包/附属模组替换默认来源 ✓）。
     */
    String id();

    /**
     * 该来源在 {@code pos} 这一格、<b>每秒</b>能提供多少 EE。
     *
     * @param level    台座所在的维度（服务端调用；实现里可以随便读光照/天气/时间/方块实体 ✓）
     * @param pos      台座自身所在的方块坐标 ✓（要判定"水晶所在的那一格"请自己 {@code pos.above()} ✓
     *                 —— 台座自己脚下的那格亮度会被台座自身影响，而水晶是浮在台座<b>上方</b>的 ✓）
     * @param simulate {@code true} = <b>只问不改</b>（查速率用 ✓ 绝不许扣燃料/改方块 ✗）；
     *                 {@code false} = 台座每秒那一次真结算 ✓ 可以扣燃料、涨凋灵度 ✓
     * @return 每秒产出的 EE；<b>0 = 当前不工作</b>（台座会把它当成"这条没贡献"✓）；不接受负数 ✗
     */
    double eePerSecond(Level level, BlockPos pos, boolean simulate);

    /** 旧的"只读"入口：等价于 {@code eePerSecond(level, pos, true)} ✓（查速率专用 ✓ 绝不改世界 ✓） */
    default double eePerSecond(Level level, BlockPos pos) {
        return eePerSecond(level, pos, true);
    }

    /** 当前是否"在工作窗口内"（= {@link #eePerSecond(Level, BlockPos)} &gt; 0）；供 tooltip / 玉 / 手册一类的查询方复用 ✓ */
    default boolean activeAt(Level level, BlockPos pos) {
        return eePerSecond(level, pos, true) > 0.0D;
    }

    /** 手册/调试用的短名（可翻译键；默认按 id 拼一个键 ✓） */
    default String nameKey() {
        return "energy_source.tinkersnewlife." + id();
    }

    /**
     * 配置里给玩家看的名字（写死在 config 注释与手册里 ✓）。
     * <p>⚠ 刻意做成 {@code default} 且有兜底：附属模组新写的来源不实现它也能编译 ✓。
     */
    default String shortName() {
        return id();
    }

    /**
     * 配置里那份"人话说明"（一行；写进 config 注释与手册 ✓）。
     * <p>同样是 {@code default} ⇒ 新来源不实现也能用 ✓。
     */
    default String summary() {
        return "";
    }
}
