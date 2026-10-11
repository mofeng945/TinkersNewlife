package com.mofengbaizhi.tinkersnewlife.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * §1342 “贴在一起的幽灵”插桩：让 {@code Level#getBlockEntity} 也能查到我们的幽灵。
 *
 * <p>★这样匠魂自己的 {@code TabbedContainerMenu#detectStationParts}（扫相邻方块）
 * 就会把它们当成真方块捐出来 ⇒ 侧栏由匠魂自己建 ✓
 * ⇒ **两端自然一致** ✓（★不再需要我手工 addSubContainer ✗）
 *
 * <p>性能：表为空时几乎零开销（只一次 isEmpty 判断 ✓）。
 */
@Mixin(value = Level.class, remap = false)
public class VirtualBlockEntityMixin {

    @Inject(method = "getBlockEntity", at = @At("HEAD"), cancellable = true, remap = false)
    private void tnl$virtualBlockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        try {
            if (com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.VIRTUAL.isEmpty()) {
                return;
            }
            BlockEntity be = com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.lookupVirtual(pos);
            if (be != null) {
                cir.setReturnValue(be);
            }
        } catch (Throwable ignored) {
        }
    }
}
