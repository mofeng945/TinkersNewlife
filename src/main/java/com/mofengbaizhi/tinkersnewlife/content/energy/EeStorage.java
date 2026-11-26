package com.mofengbaizhi.tinkersnewlife.content.energy;

/**
 * <b>EE（晶能 / Elder Energy）的存取能力接口</b> —— 本模组自己的"能量容器"契约。
 *
 * <h2>为什么要有它（§557）</h2>
 * 在它之前，EE 只存在于三处<b>互相不知道对方</b>的地方 ✗：
 * <ul>
 *   <li>{@code ElderCrystalBlockEntity} 的 {@code ee} 字段（有 §519 留的
 *       {@code getEe()/absorb(int)} 两个专用入口 ✓）；</li>
 *   <li>水晶物品的 NBT（{@link ElderCrystalStorage} ✓）；</li>
 *   <li>台座 §553 的 {@code cache}（只有台座自己会碰 ✓）。</li>
 * </ul>
 * ⇒ 想让"抽取方块"和"转化器"与它们对话，就只能逐个 {@code instanceof} 各写一套 ✗。
 * 现在统一成一个接口 ✓（形状<b>照着 Forge 的 {@code IEnergyStorage} 抄</b> ✓
 * 换单位的思路一样，改一个单位名而已 ✓）。
 *
 * <h2>⚠ 有意与 {@code IEnergyStorage} 不同的两点</h2>
 * <ol>
 *   <li><b>不注册成 Forge {@code Capability}</b> ✗ —— 它只是本模组内部四个方块（台座 / 水晶方块 /
 *       抽取方块 / 转化器）之间的约定 ✓。跨模组的能量一律走 FE（{@code ForgeCapabilities.ENERGY}）✓
 *       —— 这正是"EE 是内部货币、FE 是对外接口"的口径 ✓（见 §557 换算表）。</li>
 *   <li>单位是 <b>EE 的整数点</b>（不是 FE 的"能量单位"）✓ —— 与 {@link EnergyUnits} 的
 *       整数口径一致 ✓ 也免得"0.5 EE"在接口层来回取整 ✗。</li>
 * </ol>
 *
 * <h2>simulate 语义（照抄 Forge）</h2>
 * {@code simulate == true} ⇒ <b>只问不改</b>（返回"如果真做会是多少" ✓ 世界/字段一动不动 ✓）；
 * {@code simulate == false} ⇒ 真做 ✓。所有实现都必须遵守这一条 ✗ 否则台座的充能会被"查一下"就抽干 ✗。
 */
public interface EeStorage {

    /** 当前存量（EE；永远 ≥ 0 ✓） */
    int getEe();

    /** 容量（EE；永远 ≥ 0 ✓） */
    int getCapacity();

    /**
     * 存进去（"别人给我 EE"）。
     *
     * @param amount   想存多少（≤ 0 ⇒ 返回 0 ✓）
     * @param simulate true = 只算不改 ✓
     * @return <b>实际接受</b>的量（装不下的部分要如实退回 ✓ 不许吞掉 ✗）
     */
    int insertEe(int amount, boolean simulate);

    /**
     * 取出来（"别人从我这里拿 EE"）。
     *
     * @param amount   想取多少（≤ 0 ⇒ 返回 0 ✓）
     * @param simulate true = 只算不改 ✓
     * @return <b>实际取出</b>的量（不够就有多少给多少 ✓）
     */
    int extractEe(int amount, boolean simulate);

    /** 剩余可存（默认由存量与容量推出 ✓ 有特殊语义的实现可以覆写 ✓） */
    default int getSpace() {
        return Math.max(0, getCapacity() - getEe());
    }

    /** 空不空（默认由存量推出 ✓） */
    default boolean isEmpty() {
        return getEe() <= 0;
    }

    /**
     * 一次性查询（tooltip / Jade / 调试用 ✓ 免得分别调两个方法时中间被改 ✗）。
     * <p>默认实现就是"读两次" ✓（EE 的读写都在服务端主线程，不存在并发中间态 ✓）。
     */
    default EeSnapshot snapshot() {
        return new EeSnapshot(getEe(), getCapacity());
    }

    /** 查询结果（不可变 ✓ 单位就是 EE 的整数点 ✓） */
    record EeSnapshot(int ee, int capacity) {
        public int space() {
            return Math.max(0, capacity - ee);
        }

        public float ratio() {
            return capacity <= 0 ? 0.0F : (float) ee / (float) capacity;
        }

        public boolean full() {
            return ee >= capacity;
        }
    }
}
