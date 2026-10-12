package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * \u00a7816 \u2605\u2605\u2605\u4fbf\u643a\u8bbe\u5907**\u5408\u4e0d\u4e86\u6210**\u7684\u6700\u540e\u4e00\u4e2a\u65ad\u70b9\uff08\u2605\u7ffb\u6e90\u7801\u786e\u8bc1 \u2713\uff09\u3002
 *
 * <h2>\u8bc1\u636e\uff08\u2605Mantle 1.11.117 \u6e90\u7801\uff09</h2>
 * <pre>
 *   // slimeknights.mantle.network.packet.BlockEntityPacket
 *   default void handleThreadsafe(NetworkEvent.Context ctx) {
 *       BlockEntity be = getBlockEntity(pos(), this);
 *       if (be != null) handleBlockEntity(ctx, type().cast(be));
 *       else Mantle.logger.error("\u2026 No block entity at {}", pos);
 *   }
 *   static BlockEntity getBlockEntity(BlockPos pos, Object packet) {
 *       return getBlockEntity(SafeClientAccess.getLevel(), pos, packet);
 *   }
 *   static BlockEntity getBlockEntity(BlockGetter world, BlockPos pos, Object packet) {
 *       if (BlockEntityHelper.isBlockLoaded(world, pos)) return world.getBlockEntity(pos);
 *       Mantle.logger.error("\u2026 World is not loaded at {}", pos);   // \u2605\u2605 \u6211\u4eec\u8d70\u8fd9\u91cc
 *       return null;
 *   }
 *   // slimeknights.mantle.util.BlockEntityHelper
 *   static boolean isBlockLoaded(BlockGetter world, BlockPos pos) {
 *       if (world == null) return false;
 *       if (world instanceof LevelReader) return ((LevelReader) world).hasChunkAt(pos);   // \u2605\u2605 \u53e3\u888b\u5750\u6807 false
 *       return true;
 *   }
 * </pre>
 * \u21d2 \u2605\u5320\u9b42\u7684\u914d\u65b9\u5305\uff08{@code UpdateCraftingRecipePacket}\uff09\u5728\u5ba2\u6237\u7aef**\u8fde
 * {@code world.getBlockEntity(pos)} \u90fd\u6ca1\u8d70\u5230**\u5c31\u88ab\u4e22\u4e86 \u2717
 * \u21d2 \u5ba2\u6237\u7aef {@code lastRecipe} \u6052\u4e3a null \u21d2 \u7ed3\u679c\u69fd\u7b97\u4e0d\u51fa\u6765 \u21d2 **\u80fd\u770b\u80fd\u88c5\u4f46\u5408\u4e0d\u4e86** \u2717\u2717
 * \uff08\u2605\u8fd9\u4e5f\u662f \u00a71352 \u8bb0\u7684\u300c{@code VirtualBlockEntityMixin} \u4ece\u672a\u88ab\u8c03\u7528\u300d\u7684**\u771f\u6b63\u539f\u56e0** \u2713\uff09
 *
 * <h2>\u4fee\u6cd5</h2>
 * \u5bf9**\u6211\u4eec\u81ea\u5df1\u7684\u53e3\u888b\u5750\u6807**\u653e\u884c \u2713\uff1a\u5728 {@code VIRTUAL}\u91cc\u7684\u5750\u6807\u4e00\u5f8b\u5f53\u201c\u5df2\u52a0\u8f7d\u201d \u2713\uff0c
 * \u4e8e\u662f\u540e\u9762\u7684 {@code world.getBlockEntity(pos)} \u4f1a\u547d\u4e2d\u5df2\u6709\u7684
 * {@code VirtualBlockEntityMixin}\uff08\u2605\u63d2\u5728 {@code Level#getBlockEntity} \u2713\uff09\u21d2 \u5e7d\u7075\u4ea4\u51fa \u2713
 * \u21d2 {@code updateRecipe} \u751f\u6548 \u21d2 \u5ba2\u6237\u7aef\u80fd\u5408\u6210 \u2713\u2713
 *
 * <p>\u26a0 \u76ee\u6807\u662f**\u9ed1\u6a71/Mantle**\u21d2 \u5fc5\u987b {@code remap = false} \u2713\uff08\u2605\u8be6\u89c1 \u00a7801 \u2713\uff09\u3002
 * <p>\u2605\u5feb\u901f\u8def\u5f84\uff1a{@code VIRTUAL} \u7a7a\u65f6\u76f4\u63a5\u4e0d\u63d2\u624b \u2713\uff08\u2605\u8fd9\u65b9\u6cd5\u8c03\u7528\u6781\u9891\u7e41 \u2717\uff09\u3002
 */
@Mixin(targets = "slimeknights.mantle.util.BlockEntityHelper", remap = false)
public class BlockEntityHelperLoadedMixin {

    @Inject(method = "isBlockLoaded", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tnl$allowVirtual(BlockGetter world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (world == null || pos == null || PortableDeviceSupport.VIRTUAL.isEmpty()) {
                return;   // \u2605\u6ca1\u5f00\u4fbf\u643a\u8bbe\u5907\u65f6\u96f6\u5f00\u9500 \u2713
            }
            if (PortableDeviceSupport.VIRTUAL.containsKey(pos)) {
                cir.setReturnValue(Boolean.TRUE);   // \u2605\u53e3\u888b\u5750\u6807\u5f53\u201c\u5df2\u52a0\u8f7d\u201d \u2713
            }
        } catch (Throwable ignored) {
        }
    }
}
