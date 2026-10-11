package com.mofengbaizhi.tinkersnewlife.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
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

    /**
     * §1343 扫描的**第一道判定**是 {@code world.getBlockState(pos).getBlock() instanceof ITabbedBlock} ✗
     * 只插桩 getBlockEntity 不够（那里返回空气 ⇒ continue ✗）⇒ 这里也把幽灵的**方块状态**借出去 ✓
     */
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true, remap = false)
    private void tnl$virtualBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        try {
            if (com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.VIRTUAL.isEmpty()) {
                return;
            }
            BlockEntity be = com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.lookupVirtual(pos);
            if (be != null) {
                // §1346 ★关键：匠魂扫描要求方块**实现 ITabbedBlock**，
                //   而全匠魂只有 TabbedTableBlock 实现它（★反编译实证 ✓）
                //   ⇒ 这里统一借出工匠台的方块状态（★样子不重要，关键是能过判定 ✓）
                net.minecraft.world.level.block.Block tabbed =
                        net.minecraftforge.registries.ForgeRegistries.BLOCKS
                                .getValue(new net.minecraft.resources.ResourceLocation("tconstruct:crafting_station"));
                cir.setReturnValue(tabbed == null ? be.getBlockState() : tabbed.defaultBlockState());
            }
        } catch (Throwable ignored) {
        }
    }

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
