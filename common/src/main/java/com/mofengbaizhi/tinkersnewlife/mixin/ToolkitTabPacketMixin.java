package com.mofengbaizhi.tinkersnewlife.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slimeknights.tconstruct.tables.network.StationTabPacket;

/**
 * §1355 点工具包顶栏标签 ⇒ 打开对应设备。
 *
 * <p>★原版处理是：{@code world.getBlockState(pos)} ⇒ 按坐标找真方块 ⇒ 我们那些坐标是虚拟的 ✗
 * （★原版 {@code Level} 插桩日志里始终没被调用 ✗ 所以不能指望它 ✓）
 *
 * <p>⇒ 这里在 {@code handleThreadsafe} 入口接力：若点的是我们的虚拟坐标 ✓
 * 就自己打开对应设备（★复用各菜单类 ✓）并取消原处理。
 *
 * <p>★这个混入挂的是**匠魂**的类（★不混淆 ⇒ remap=false 正确 ✓）
 */
@Mixin(value = StationTabPacket.class, remap = false)
public class ToolkitTabPacketMixin {

    @Shadow
    @Final
    private BlockPos pos;

    @Inject(method = "handleThreadsafe", at = @At("HEAD"), cancellable = true, remap = false)
    private void tnl$openToolkitDevice(NetworkEvent.Context context, CallbackInfo ci) {
        try {
            var support = com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.class;
            if (com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.VIRTUAL.isEmpty()) {
                return;
            }
            net.minecraft.core.BlockPos base = com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.VIRTUAL_BASE;
            int dx = this.pos.getX() - base.getX();
            int dy = this.pos.getY() - base.getY();
            int dz = this.pos.getZ() - base.getZ();
            if (dy != 0 || dz != 0 || dx < 0 || dx > 6) {
                return;   // ★不是我们的标签 ✓
            }
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            ci.cancel();
            com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.openToolkitDevice(sender, dx);
        } catch (Throwable t) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("[便携设备] 顶栏接力失败: {}", t.toString());
        }
    }
}
