package com.mofengbaizhi.tinkersnewlife.content.rate;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.function.ObjLongConsumer;

/**
 * 「容器产率统计」的 <b>统一来源接口</b>（§735）—— 一个 {@code RateSource} = 一个"能装东西的东西" ✓
 *
 * <h2>口径（用户 2026-09-28 拍板 ✓）</h2>
 * <ul>
 *   <li>只统计 <b>物品</b> ✓，且按 {@code Item} <b>归并</b>（<b>忽略 NBT</b> ✓ ⇒ 附魔/耐久不同的同名物品算同一条 ✓）；</li>
 *   <li>速率 = <b>整维度净差 ÷ 统计时间</b> ✓ ⇒ 维度内"箱子 A → 箱子 B"的搬运会<b>自动抵消</b> ✓
 *       （这正是用户要的口径 ✓ 逐容器只累加正增量那种做法会把搬运算成产出 ✗）；
 *   <li>采样周期 = <b>10 分钟</b> ✓（{@link ContainerRateManager#INTERVAL_TICKS} ✓），展示单位 = <b>个/时</b> ✓；</li>
 *   <li>本轮<b>只做方法接口</b>，不做命令/界面/导出 ✓（用户口径：「我只是要写方法接口，暂时不展示」✓）。</li>
 * </ul>
 *
 * <h2>⚠ 实现方只许读 ✗</h2>
 * {@link #forEachStored} 里<b>只能</b>调 {@code getStackInSlot} / {@code getAvailableStacks} /
 * {@code forAllStored} 这类只读方法 ✓ —— 绝不能 {@code insert} / {@code extract} /
 * {@code setStackInSlot} / {@code massExtract} ✗（统计功能不许改变任何容器内容 ✓）。
 */
public interface RateSource {

    /**
     * <b>会话内稳定</b>的身份 ✓ —— 用来判"这个来源上一次采样时在不在" ✓
     * （<b>两次采样都观测到</b>的来源才允许参与差分 ✓ §735 基线规则 ✓）。
     *
     * <p>各类来源的取法：
     * <ul>
     *   <li>普通容器：{@code "be:" + 维度 + "@" + 坐标 + "#" + 方块实体类型} ✓（天然稳定 ✓）；</li>
     *   <li>AE2 网格：网格对象没有稳定名字 ✗ ⇒ 用**会话内身份表**发号 ✓（重启后会重置 ✓ 这与"快照不落盘"一致 ✓）；</li>
     *   <li>Mekanism QIO 频率：用 {@code owner + name} ✓（跨重启也稳定 ✓）。</li>
     * </ul>
     */
    String id();

    /**
     * 所属维度 ✓。
     * <p>⚠ AE2 网格只服务同一维度 ✓；Mekanism 的 <b>QIO 频率可以跨维度</b> ✗
     * ⇒ 这里记"<b>驱动器所在的维度</b>" ✓（跨维度频率会在每个维度各报一份 ⇒ 已知偏差 ✓ 已写进备忘录 ✓）。
     */
    ResourceKey<Level> dimension();

    /**
     * 只读上报：把 {@code (物品, 数量)} 交给 consumer ✓。
     * <p>数量语义 = <b>该来源里这种物品的总个数</b> ✓（同一个 {@code Item} 出现多次要<b>相加</b> ✓
     * —— 由实现方合并，主流程只按 {@code Item} 归并 ✓）。
     * <p>⚠ 传进来的 {@link ItemStack} 只用来取 {@code getItem()} ✓（数量以第二个参数为准 ✓）。
     */
    void forEachStored(ObjLongConsumer<ItemStack> consumer);

    /** 排查/日志用的一句话描述 ✓（**不展示给玩家** ✓ 本轮没有展示层 ✓） */
    String describe();
}
