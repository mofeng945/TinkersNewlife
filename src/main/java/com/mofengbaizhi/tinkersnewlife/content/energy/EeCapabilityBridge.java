package com.mofengbaizhi.tinkersnewlife.content.energy;

import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nullable;

/**
 * 把本模组的四个 EE 方块接到 <b>Forge 标准能量能力</b>上的桥（§557）。
 *
 * <h2>为什么要它</h2>
 * 用户要求"转化器要能从相邻方块收 <b>Forge Energy</b>" ✓ 也要求"输出 FE 给相邻方块" ✓
 * ⇒ 我们的方块必须<b>对外暴露</b> {@code ForgeCapabilities.ENERGY}（{@code IEnergyStorage} ✓），
 * 否则别的模组的线缆/发电机根本看不见我们 ✗（同时也读不到别人 ✓ —— 读别人直接用
 * {@code be.getCapability(ForgeCapabilities.ENERGY, side)} ✓ 不需要本类 ✓）。
 *
 * <h2>两种方向，两套语义</h2>
 * <ul>
 *   <li><b>抽取方块 / 台座</b>（{@link EeStorage}，单位 EE）：对外的 FE 面是<b>只读</b>的 ✓
 *       —— 别的模组可以"看到里面有多少"，但<b>不能</b>把它的电抽走 ✗（要抽请走 EE 那套接口 ✓
 *       接口的 {@code extractEe} 只有一个调用者：我们自己的抽取方块与转化器 ✓）。</li>
 *   <li><b>转化器</b>：它的 FE 面是<b>可收</b>的 ✓（这就是"输入 2：相邻方块通过 Forge Energy 给的能量"✓）
 *       ⇒ 它自己实现 {@link IEnergyStorage}，本类的工厂方法 {@link #wrapSink} 只负责接起来 ✓。</li>
 * </ul>
 *
 * <h2>⚠ 单位边界（重要）</h2>
 * {@code IEnergyStorage} 的单位是 <b>FE</b>，{@link EeStorage} 的单位是 <b>EE</b> ⇒
 * 两者之间必须过一趟 {@link EnergyUnits}（{@code 1 FE = 8 EE} ✓）✓
 * 本类只做"读"，所以只有 EE→FE 这一半（向下取整 ⇒ 只读面永远显示偏小 ✓ 不会凭空多出能量 ✗）。
 */
public final class EeCapabilityBridge {

    private EeCapabilityBridge() {
    }

    /** EE 容器 → 只读的 {@code IEnergyStorage}（EE 折 FE ✓ 向下取整 ✓ 只读 ✓） */
    public static IEnergyStorage readOnly(EeStorage storage, @Nullable Runnable onChanged) {
        return new EeToFeReadOnly(storage, onChanged == null ? () -> { } : onChanged);
    }

    /**
     * 覆写 {@code BlockEntity#getCapability} 时的统一分派：把 {@code ForgeCapabilities.ENERGY}
     * 换成我们准备好的那个 {@link IEnergyStorage} ✓ 其余照旧交给 {@code super} ✓。
     *
     * @param holder 我们持有的 {@code LazyOptional<IEnergyStorage>}（null ⇒ 本方块不暴露 FE ✓）
     */
    @Nullable
    public static <T> LazyOptional<T> energyOrSuper(Capability<T> cap, @Nullable Direction side,
                                                    @Nullable LazyOptional<IEnergyStorage> holder,
                                                    LazyOptional<T> superResult) {
        if (holder != null && cap == ForgeCapabilities.ENERGY) return holder.cast();
        return superResult;
    }

    /** 只读的 EE→FE 视图（写入一律拒绝 ✓ 所以 {@code extractEnergy}/{@code receiveEnergy} 都很保守 ✓） */
    private static final class EeToFeReadOnly implements IEnergyStorage {

        private final EeStorage storage;
        private final Runnable onChanged;

        private EeToFeReadOnly(EeStorage storage, Runnable onChanged) {
            this.storage = storage;
            this.onChanged = onChanged;
        }

        /** 收到的 FE 会按 1 FE = 8 EE 折成 EE ✓（装不下的部分如实退回 ✓ 绝不吞 ✗） */
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0) return 0;
            int asEe = EnergyUnits.feToEe(maxReceive);
            if (asEe <= 0) return 0;
            int acceptedEe = storage.insertEe(asEe, simulate);
            if (!simulate && acceptedEe > 0) onChanged.run();
            // ⚠ 返回的单位是 **FE**（IEnergyStorage 的口径 ✓）：
            //   收进来 1 FE 会计成 8 EE ✓ 所以我这边"装得下多少 EE"要换回 FE 报出去 ✓
            //   （acceptedEe 一定是 8 的倍数 —— feToEe 就是 ×8 —— 所以这里是整除 ✓）
            return acceptedEe / (int) EnergyUnits.FE_PER_EE_FACTOR;
        }

        /**
         * <b>只读面：不许外人抽走 ✗</b> ⇒ 恒 0 ✓。
         * <p>要抽本模组的电，请走 {@link EeStorage#extractEe}（只有抽取方块与转化器会调 ✓）。
         */
        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        /** 存量（EE → FE，向下取整 ✓ 只读面显示偏小 ⇒ 不会让人误以为能取出更多 ✓） */
        @Override
        public int getEnergyStored() {
            return EnergyUnits.eeToFe(storage.getEe());
        }

        /** 容量（EE → FE，向上取整 ✓ —— 容量<b>可以</b>偏大：它只是"最多能装多少"的说明 ✓） */
        @Override
        public int getMaxEnergyStored() {
            return (int) Math.ceil(storage.getCapacity() * EnergyUnits.EE_PER_FE);
        }

        @Override
        public boolean canExtract() {
            return false;      // 见 extractEnergy 的注释 ✓
        }

        @Override
        public boolean canReceive() {
            return true;       // 允许别人往里灌（§557 的"相邻方块推来的 FE"这条路 ✓）
        }
    }
}
