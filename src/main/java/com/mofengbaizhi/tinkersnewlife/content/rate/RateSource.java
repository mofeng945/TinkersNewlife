package com.mofengbaizhi.tinkersnewlife.content.rate;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;

import java.util.function.LongConsumer;
import java.util.function.ObjLongConsumer;

/**
 * 「容器产率统计」的 <b>统一来源接口</b>（§735 建 · §737 起<b>物品 / 流体 / 能量三种都能报</b> ✓）
 *
 * <h2>口径（用户拍板 ✓）</h2>
 * <ul>
 *   <li>统计三类：<b>物品</b>（按 {@code Item} 归并 ⇒ 忽略 NBT ✓）、
 *       <b>流体</b>（按 {@code Fluid} 归并 ✓ 单位 <b>mB</b> ✓）、<b>能量</b>（FE ✓）；</li>
 *   <li>速率 = <b>整维度净差 ÷ 统计时间</b> ✓ ⇒ 维度内"箱子 A → 箱子 B"这类搬运会<b>自动抵消</b> ✓；</li>
 *   <li>采样周期 = <b>10 分钟</b> ✓（{@link ContainerRateManager#INTERVAL_TICKS} ✓），展示 <b>个/时</b>、<b>mB/时</b>、<b>FE/时</b> ✓；</li>
 *   <li>本轮只交付<b>方法接口</b> ✓（无命令/界面/导出 ✗）。</li>
 * </ul>
 *
 * <h2>⚠ 实现方只许读 ✗</h2>
 * 三个回调里<b>只能</b>调只读方法 ✓（{@code getStackInSlot} / {@code getFluidInTank} / {@code getEnergyStored} /
 * {@code getAvailableStacks} …）—— 绝不能 {@code insert}/{@code extract}/{@code setStackInSlot}/
 * {@code receiveEnergy}/{@code drain} ✗（统计功能不许改变任何容器 ✓）。
 */
public interface RateSource {

    /**
     * <b>会话内稳定</b>的身份 ✓ —— 用来判"这个来源上一次采样时在不在" ✓
     * （<b>两次采样都观测到</b>的来源才允许参与差分 ✓ §735 基线规则 ✓）。
     */
    String id();

    /** 所属维度 ✓（QIO 频率可能跨维度 ✗ 见规划书 §7 坑 2 ✓） */
    ResourceKey<Level> dimension();

    /**
     * 只读上报<b>物品</b> ✓：把 {@code (物品, 数量)} 交给 consumer ✓
     * （同一 {@code Item} 出现多次要<b>相加</b> ✓ 由实现方合并 ✓）。
     */
    void forEachStored(ObjLongConsumer<ItemStack> consumer);

    /**
     * 只读上报<b>流体</b> ✓：{@code (流体栈, 数量 mB)} ✓ —— 默认空实现 ✓
     * （大多数来源没有流体 ✓；有流体的覆盖：储罐 / 冶炼炉 / 通用机械的流体箱…… ✓）。
     */
    default void forEachFluid(ObjLongConsumer<FluidStack> consumer) {
    }

    /**
     * 只读上报<b>能量</b>（FE ✓）—— 默认 0 ✓。
     * <p>⚠ 语义是"这个来源<b>当前存着多少 FE</b>"✓（不是"它的发电速率"✗）——
     * 速率仍然靠"两次采样求差 ÷ 时间"得到 ✓（与物品/流体同一套口径 ✓）。
     */
    default long energyStored() {
        return 0L;
    }

    /**
     * <b>一次性</b>把三类都报出来 ✓ —— 管理器<b>只调这一个</b> ✓。
     *
     * <p>默认实现就是依次调三个方法 ✓；<b>能一次读全的实现方请覆盖它</b> ✓
     * —— 典型是 AE2 的磁盘：读一次 {@code getAvailableStacks} 就能同时产出物品与流体 ✓
     * 覆盖之后能省掉第二次全量读取 ✓（§737 ✓）。
     */
    default void forEachAll(ObjLongConsumer<ItemStack> items,
                            ObjLongConsumer<FluidStack> fluids,
                            LongConsumer energy) {
        forEachStored(items);
        forEachFluid(fluids);
        energy.accept(energyStored());
    }

    /**
     * <b>首次被观测到时，把里面的东西算作"流入"</b>吗？（§748 ✓ 默认 false ✓）
     *
     * <p>背景（用户实测）：往一个<b>刚放下的</b>箱子里塞一把剑 ⇒ 产率显示 0 ✗。
     * 原因是 §735 的基线规则：<b>新来源第一次只立基线、不算变化</b> ✓
     * —— 那条规则的初衷是"别让"往地下放一箱满满的"刷出巨量产出 ✗"，
     * 但它把"玩家自己新做的箱子"也一并当成了基线 ✗ 于是人为放进的东西全都不算 ✗。
     *
     * <p>⇒ 只有<b>玩家亲手放下的</b>容器（{@code BlockEvent.EntityPlaceEvent} ✓）才返回 true ✓：
     * 它里面此刻的东西算一次流入 ✓；而<b>世界生成的</b>箱子照旧只立基线 ✓
     * （结构宝箱本来也被 §745 的 LootTable 规则挡了一层 ✓）。
     */
    default boolean countFirstObservationAsInflow() {
        return false;
    }
    /** 排查/日志用的一句话描述 ✓（**不展示给玩家** ✓ 本轮没有展示层 ✓） */
    String describe();
}
