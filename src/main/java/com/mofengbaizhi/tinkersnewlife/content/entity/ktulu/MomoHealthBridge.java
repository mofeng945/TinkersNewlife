package com.mofengbaizhi.tinkersnewlife.content.entity.ktulu;

import net.minecraft.world.entity.Mob;

/**
 * ★钓鱼类（★用户口径 ✓）：看起来像“取墨默血量”的正经入口 ✗
 * 实际上**什么都不算**，只把调用转给一个**空类** {@link MomoHealthIdiot} ✓。
 *
 * <p>目的：★反向者顺着 {@code getMomoHealth} 摸下去 ⇒
 * 只会摸到空实现 ✓，而真正的内核
 * （{@link AstralVitalsKernel}）名字里**没有任何可猜的关键词** ✓。
 */
public final class MomoHealthBridge {

    private MomoHealthBridge() {
    }

    /** ★钓鱼入口 ✓（★永远返回 0 ✗ ★不影响任何逻辑 ✓） */
    public static float getMomoHealth(Mob momo) {
        return MomoHealthIdiot.getMomoHealthIdiot(momo);
    }
}
