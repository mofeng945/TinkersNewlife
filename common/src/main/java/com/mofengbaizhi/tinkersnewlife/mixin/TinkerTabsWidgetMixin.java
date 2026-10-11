package com.mofengbaizhi.tinkersnewlife.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import slimeknights.tconstruct.tables.client.inventory.widget.TinkerTabsWidget;

/**
 * §1358 工具包顶栏的点击。
 *
 * <p>★堵点在**客户端**（反编译实证 ✓）：
 * <pre>
 * TinkerTabsWidget#onNewTabSelection(pos):
 *   if (level.getBlockState(pos).getBlock() instanceof ITabbedBlock)   // ★客户端也要查方块
 *       TinkerNetwork.sendToServer(new StationTabPacket(pos));          // ★只有过了才发包
 * </pre>
 * 我们那些坐标是虚拟的 ⇒ 客户端拿到空气 ⇒ **包根本没发** ✗
 * （★原版 {@code Level} 插桩日志里始终没被调用 ✗ 不能指望它 ✓）
 *
 * <p>⇒ 这里直接把方块状态判定**接管**：是我们的坐标就返回 true ✓
 * 于是匠魂自己会把 {@code StationTabPacket} 发出去 ✓
 * （★这个混入挂的是**匠魂**类 ⇒ remap=false 正确 ✓ ★确定能应用 ✓）
 */
@Mixin(value = TinkerTabsWidget.class, remap = false)
public class TinkerTabsWidgetMixin {

    @Inject(method = "onNewTabSelection", at = @At("HEAD"), cancellable = true, remap = false)
    private void tnl$allowVirtualTab(BlockPos pos, CallbackInfoReturnable<Void> cir) {
        try {
            if (com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.VIRTUAL.isEmpty()) {
                return;
            }
            net.minecraft.core.BlockPos base = com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.VIRTUAL_BASE;
            int dx = pos.getX() - base.getX();
            int dy = pos.getY() - base.getY();
            int dz = pos.getZ() - base.getZ();
            if (dy != 0 || dz != 0 || dx < 0 || dx > 6) {
                return;   // ★不是我们的标签 ✓
            }
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info(
                    "[便携设备] 客户端点击标签 index={} ⇒ 自己发包（§1358 ✓）", dx);
            slimeknights.tconstruct.common.network.TinkerNetwork.getInstance()
                    .sendToServer(new slimeknights.tconstruct.tables.network.StationTabPacket(pos));
            cir.cancel();
        } catch (Throwable t) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("[便携设备] 标签发包失败: {}", t.toString());
        }
    }
}
