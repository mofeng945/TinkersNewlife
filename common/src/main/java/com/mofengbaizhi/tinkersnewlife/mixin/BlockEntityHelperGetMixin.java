package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * \u00a7817 \u2605\u2605\u2605\u76f4\u63a5\u63d2\u5728 Mantle **\u771f\u6b63\u8c03\u7528\u7684\u90a3\u4e2a\u65b9\u6cd5**\u4e0a\uff08\u2605\u8bfb\u6e90\u7801 + \u65e5\u5fd7\u5b9e\u8bc1 \u2713\uff09\u3002
 *
 * <h2>\u4e3a\u4ec0\u4e48 \u00a7816 \u4e0d\u591f\uff08\u2605\u65e5\u5fd7\u8bc1\u636e \u2713\uff09</h2>
 * <pre>
 *   \u65e5\u5fd7\uff1a"No block entity at BlockPos{x=0, y=-1024, \u2026}"  \u2605\u51fa\u73b0 167 \u6b21
 *         "World is not loaded at \u2026"                              \u2605\u51fa\u73b0 0 \u6b21
 *   \u21d2 \u2605isBlockLoaded \u5df2\u7ecf\u653e\u884c\u4e86\uff08\u00a7816 \u751f\u6548 \u2713\uff09\uff0c
 *     \u4f46\u7d27\u63a5\u7740\u7684 {@code world.getBlockEntity(pos)} **\u8fd4\u56de null** \u2717
 *   \u21d2 \u2605\u6211\u4eec\u7684 {@code VirtualBlockEntityMixin}\uff08\u63d2\u5728 {@code Level#getBlockEntity} \u2717\uff09**\u6ca1\u547d\u4e2d** \u2717
 *     \uff08\u2605\u800c\u4e14 {@code lookupVirtual} \u7684\u65e5\u5fd7\u4e00\u6761\u90fd\u6ca1\u6709 \u21d2 \u4ece\u672a\u88ab\u8c03\u7528 \u2717\uff09
 * </pre>
 *
 * <h2>\u4fee\u6cd5</h2>
 * \u76f4\u63a5\u63d2 Mantle \u81ea\u5df1\u7684 {@code BlockEntityHelper.getBlockEntity(BlockGetter, BlockPos, Object)} \u2713
 * \uff08\u2605\u5c31\u662f\u4e22\u5305\u524d\u7684\u6700\u540e\u4e00\u6b65 \u2713\uff09\uff1a\u539f\u7248\u62ff\u4e0d\u5230\u65f6 \u2717
 * \u6539\u4ece {@code PortableDeviceSupport.lookupVirtual(pos)} \u53d6\u5e7d\u7075 \u2713\uff0c\u5e76\u6253\u4e00\u884c\u65e5\u5fd7\u544a\u8bc9\u6211\u4eec\u5230\u5e95\u6709\u6ca1\u6709 \u2713\u3002
 *
 * <p>\u26a0 \u76ee\u6807\u662f Mantle \u21d2 {@code remap = false} \u2713\u3002
 */
@Mixin(targets = "slimeknights.mantle.util.BlockEntityHelper", remap = false)
public class BlockEntityHelperGetMixin {

    @Inject(method = "getBlockEntity", at = @At("RETURN"), cancellable = true, remap = false)
    private static void tnl$virtualFallback(BlockGetter world, BlockPos pos, Object packet,
                                            CallbackInfoReturnable<BlockEntity> cir) {
        try {
            if (cir.getReturnValue() != null || pos == null) {
                return;   // \u2605\u539f\u7248\u62ff\u5230\u4e86 \u21d2 \u4e0d\u63d2\u624b \u2713
            }
            BlockEntity ghost = PortableDeviceSupport.lookupVirtual(pos);
            if (ghost != null) {
                cir.setReturnValue(ghost);
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info(
                        "[\u4fbf\u643a\u8bbe\u5907] \u00a7817 \u5305\u91cc\u7684\u65b9\u5757\u5b9e\u4f53\u6539\u4ece\u865a\u62df\u8868\u53d6\u5230\uff1a{} \u21d2 {}", pos,
                        ghost.getClass().getSimpleName());
            } else {
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                        "[\u4fbf\u643a\u8bbe\u5907] \u00a7817 \u865a\u62df\u8868\u91cc\u4e5f\u6ca1\u6709 {} \uff08\u8868\u5927\u5c0f={}\uff09\u21d2 \u5ba2\u6237\u7aef\u6ca1\u6ce8\u518c\u5e7d\u7075 \u2717", pos,
                        PortableDeviceSupport.VIRTUAL.size());
            }
        } catch (Throwable ignored) {
        }
    }
}
