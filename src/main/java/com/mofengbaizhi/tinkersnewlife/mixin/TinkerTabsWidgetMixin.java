package com.mofengbaizhi.tinkersnewlife.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import java.util.List;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

    @Shadow
    private slimeknights.mantle.client.screen.TabsWidget tabs;

    @Shadow
    private java.util.List<net.minecraft.core.BlockPos> tabData;

    @Shadow
    private slimeknights.tconstruct.tables.client.inventory.BaseTabbedScreen<?, ?> parent;


    @Inject(method = "onNewTabSelection", at = @At("HEAD"), cancellable = true, remap = false)
    private void tnl$allowVirtualTab(BlockPos pos, CallbackInfo ci) {   // §1359 ★目标方法返回 void ⇒ 必须用 CallbackInfo ✓
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
            ci.cancel();
        } catch (Throwable t) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("[便携设备] 标签发包失败: {}", t.toString());
        }
    }

    /**
     * §1363 悬停提示：原版用 {@code world.getBlockEntity(pos)} 取名字 ✗
     * 我们那些坐标是虚拟的 ⇒ te 为 null ⇒ 退回方块名 ⇒
     * {@code getBlockState} 又是空气 ⇒ **显示“空气”** ✗✗
     * ⇒ 这里自己算：在我们的标签上就用那个位置对应的方块名 ✓
     */
    @Inject(method = "renterTooltip", at = @At("HEAD"), cancellable = true, remap = false)
    private void tnl$ourTooltip(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY,
                                CallbackInfo ci) {
        try {
            if (com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.VIRTUAL.isEmpty()) {
                return;
            }
            int hi = -1;
            try {
                Object tw = this.tabs;
                java.lang.reflect.Field f = tw.getClass().getDeclaredField("highlighted");
                f.setAccessible(true);
                hi = f.getInt(tw);
            } catch (Throwable ignored) {
            }
            if (hi < 0) {
                return;
            }
            java.util.List<?> data = this.tabData;
            if (hi >= data.size()) {
                return;
            }
            net.minecraft.core.BlockPos pos = (net.minecraft.core.BlockPos) data.get(hi);
            net.minecraft.core.BlockPos base = com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.VIRTUAL_BASE;
            int dx = pos.getX() - base.getX();
            int dy = pos.getY() - base.getY();
            int dz = pos.getZ() - base.getZ();
            if (dy != 0 || dz != 0 || dx < 0 || dx > 6) {
                return;   // ★不是我们的标签 ✓
            }
            net.minecraft.network.chat.Component title = null;
            net.minecraft.world.level.block.state.BlockState st =
                    blockStateFor(dx);
            if (st != null) {
                title = st.getBlock().getName();
            }
            if (title == null || title.getString().isEmpty()) {
                title = net.minecraft.network.chat.Component.translatable("item.tinkersnewlife.portable_toolkit");
            }
            graphics.renderTooltip(this.parent.getMinecraft().font, title, mouseX, mouseY);   // §1363 ★单 Component 重载 ✓
            ci.cancel();
        } catch (Throwable ignored) {
        }
    }

    /** ★dx：0 = 工匠台，1..n = DEVICES 顺序 ✓ */
    private static net.minecraft.world.level.block.state.BlockState blockStateFor(int dx) {
        String id = (dx <= 0) ? "tconstruct:crafting_station"
                : com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.DEVICES[
                        Math.min(dx, com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.DEVICES.length) - 1][1];
        net.minecraft.world.level.block.Block b =
                net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation(id));
        return (b == null) ? null : b.defaultBlockState();
    }

}
