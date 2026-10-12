package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * \u00a7818 \u2605\u2605\u2605\u5ba2\u6237\u7aef\u53d6\u5e7d\u7075\uff08\u2605\u65e5\u5fd7\u5b9e\u8bc1\u540e\u7684\u7cbe\u786e\u76ee\u6807 \u2713\uff09\u3002
 *
 * <h2>\u8bc1\u636e\u94fe\uff08\u2605\u5168\u90e8\u6765\u81ea\u6e90\u7801 + \u65e5\u5fd7 \u2713\uff09</h2>
 * <pre>
 *   \u65e5\u5fd7\uff1aNo block entity at BlockPos{x=0, y=-1024, z=0}   \u2605\u51fa\u73b0 141+ \u6b21
 *         World is not loaded at \u2026                            \u2605\u51fa\u73b0 0 \u6b21\uff08\u21d2 \u00a7816 isBlockLoaded \u5df2\u653e\u884c \u2713\uff09
 *         \u00a7817 \u63a2\u9488\uff08\u6ce8\u5165 BlockEntityHelper.getBlockEntity \u2717\uff09 \u2605\u4e00\u6761\u90fd\u6ca1\u6709
 *           \u21d2 \u2605**\u8be5\u65b9\u6cd5\u5728 BlockEntityHelper \u91cc\u4e0d\u5b58\u5728** \u2717 \u21d2 Mixin \u9759\u9ed8\u4e0d\u6ce8\u5165 \u2717
 *   \u21d2 \u5269\u4e0b\u7684\u53ea\u6709\uff1a{@code world.getBlockEntity(pos)}\uff08\u5728 BlockEntityPacket \u91cc\uff09\u8fd4\u56de null \u2717
 *   \u800c\u6211\u4eec\u65e2\u6709\u7684 {@code VirtualBlockEntityMixin} \u63d2\u5728 {@code Level#getBlockEntity} \u2717
 *   \u63d2\u5165**\u6210\u529f**\uff08\u65e5\u5fd7\u91cc\u6ca1\u6709 target not found \u2713\uff09\u5374\u4ecd\u8fd4\u56de null
 *   \u21d2 \u2605\u53ea\u80fd\u662f {@code ClientLevel} **\u8986\u5199\u4e86** {@code getBlockEntity} \u2717 \u21d2 \u8c03\u7528\u7ed5\u8fc7\u4e86 {@code Level} \u2717\u2717
 * </pre>
 *
 * <h2>\u4fee\u6cd5</h2>
 * \u76f4\u63a5\u5728 {@code ClientLevel} \u4e0a\u518d\u63d2\u4e00\u4efd \u2713\uff1a\u539f\u7248\u62ff\u4e0d\u5230\u5c31\u53bb
 * {@code PortableDeviceSupport.lookupVirtual(pos)} \u53d6 \u2713\uff0c\u5e76\u6253\u4e24\u6761\u63a2\u9488\u65e5\u5fd7\u628a\u7ed3\u679c\u8bf4\u6b7b \u2713\u3002
 *
 * <p>\u26a0 \u539f\u7248\u7c7b \u21d2 **\u4e0d\u80fd**\u5199 {@code remap = false} \u2713\uff08\u2605\u8be6\u89c1 \u00a71352 \u2713\uff09\u3002
 */
@Mixin(net.minecraft.client.multiplayer.ClientLevel.class)
public class ClientLevelGetBlockEntityMixin {

    @Inject(method = "m_7702_", at = @At("HEAD"), cancellable = true, remap = false)   // §819 ★SRG 名
    private void tnl$virtualBlockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        try {
            if (pos == null || PortableDeviceSupport.VIRTUAL.isEmpty()) {
                return;   // \u2605\u6ca1\u5f00\u4fbf\u643a\u8bbe\u5907\u65f6\u96f6\u5f00\u9500 \u2713
            }
            BlockEntity hit = PortableDeviceSupport.lookupVirtual(pos);
            if (hit == null) {
                return;   // \u2605\u4e0d\u662f\u6211\u4eec\u7684\u5750\u6807 \u21d2 \u4ea4\u7ed9\u539f\u7248 \u2713
            }
            cir.setReturnValue(hit);
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info(
                    "[\u4fbf\u643a\u8bbe\u5907] \u00a7818 ClientLevel \u53d6\u5230\u5e7d\u7075\uff1a{} \u21d2 {}", pos, hit.getClass().getSimpleName());
        } catch (Throwable ignored) {
        }
    }
}
