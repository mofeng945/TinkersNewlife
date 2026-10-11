package com.mofengbaizhi.tinkersnewlife.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.tables.menu.TabbedContainerMenu;
import slimeknights.tconstruct.tables.menu.module.SideInventoryContainer;

/**
 * §1345 工具包的六个侧栏。
 *
 * <p>★为什么不能靠匠魂自己扫（反编译实证 ✓）：
 * <pre>
 * TabbedContainerMenu#addChestSideInventory（工匠台真正用的那个）
 *   for (Direction dir : Direction.Plane.HORIZONTAL)      // 只扫 4 个水平方向
 *       if (已是工作站) continue;                    // 相邻的工作站会被跳过
 *       if (hasItemHandler(te, null)) { inventoryTE = te; break; }   // 只加**一个**
 *   isUsable 还要求实体类型在标签 tconstruct:side_inventories 里
 *   ★而那个标签里**只有原版箱子/木桶/漏斗** ✗ 一个匠魂方块都没有
 * </pre>
 * ⇒ 所以必须我们自己在 {@code detectStationParts} 的**返回处**把 6 个塞进去 ✓
 * （★时机：它在 {@code super(...)} 里、玩家背包槽**之前**被调用 ✓ 晚了会 IllegalStateException）
 */
@Mixin(value = TabbedContainerMenu.class, remap = false)
public class ToolkitSideInventoryMixin {

    @Inject(method = "detectStationParts", at = @At("RETURN"), remap = false)
    private void tnl$addToolkitSides(Level world, BlockPos start, CallbackInfo ci) {
        try {
            var support = com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.class;
            java.util.List<BlockEntity> pending =
                    com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.PENDING_SIDES;
            if (pending.isEmpty()
                    && com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.CLIENT_TOOLKIT) {
                // ★客户端：自己用常量表造（不等服务端数据 ✓）
                pending = com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.buildDeviceGhosts();
            }
            if (pending.isEmpty()) {
                return;
            }
            Inventory inv = null;
            try {
                inv = net.minecraft.client.Minecraft.getInstance().player.getInventory();
            } catch (Throwable ignored) {
            }
            if (inv == null) {
                inv = com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.PENDING_INV;
            }
            if (inv == null) {
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("[便携设备] 拿不到玩家背包 ⇒ 跳过侧栏");
                pending.clear();
                return;
            }
            TabbedContainerMenu<?> self = (TabbedContainerMenu<?>) (Object) this;
            int id = ((net.minecraft.world.inventory.AbstractContainerMenu) (Object) this).containerId;
            int i = 0;
            for (BlockEntity be : new java.util.ArrayList<>(pending)) {
                if (be == null) {
                    continue;
                }
                SideInventoryContainer<BlockEntity> side = new SideInventoryContainer<>(
                        TinkerTables.craftingStationContainer.get(), id, inv, be, -40 - i * 100, 18, 9);
                self.addSubContainer(side, true);
                i++;
            }
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携设备] 侧栏已加 {} 个（§1345 ✓）", i);
            pending.clear();
        } catch (Throwable t) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("[便携设备] 侧栏添加失败: {}", t.toString());
        }
    }
}
