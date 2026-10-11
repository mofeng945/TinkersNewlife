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
 * §1338 工具包的“六个幽灵侧栏”。
 *
 * <p>★挂在 {@code TabbedContainerMenu#detectStationParts} 的**返回处** ✓（日志实证的时机）：
 * <pre>
 * CraftingStationContainerMenu 构造器顺序：
 *     addChestSideInventory();   // ← 侧栏在这之前加（玩家背包槽还没加 ✓）
 *     addInventorySlots();       // ← 玩家背包槽在这里加
 * 晚了就会：IllegalStateException: BaseContainer: Player inventory has …
 * </pre>
 * 而 {@code detectStationParts} 正好在 {@code super(...)} 里、{@code addInventorySlots()} **之前**被调用 ✓
 * ⇒ 在这里把幽灵作为侧栏 {@code addSubContainer} ⇒ **两端都成立** ✓
 */
@Mixin(value = TabbedContainerMenu.class, remap = false)
public class ToolkitSideInventoryMixin {

    @Inject(method = "detectStationParts", at = @At("RETURN"), remap = false)
    private void tnl$disabled(Level world, BlockPos start, CallbackInfo ci0) {
        // §1342 已停用：侧栏改由匠魂自己扫到“贴在一起的幽灵”建立 ✓
        //（★VirtualBlockEntityMixin 让 Level#getBlockEntity 能查到幽灵 ✓）
    }

    @Inject(method = "detectStationParts", at = @At("RETURN"), remap = false)
    private void tnl$addPendingSidesDisabled(Level world, BlockPos start, CallbackInfo ci) {
        if (true) { return; }   // §1342 停用
        try {
            // §1341 客户端：只要知道“这是工具包”就自己造 6 个幽灵 ✓
            java.util.List<BlockEntity> pending =
                    com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.PENDING_SIDES;
            if (pending.isEmpty() && com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.CLIENT_TOOLKIT) {
                pending = com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.buildDeviceGhosts();
            }
            if (pending.isEmpty()) {
                return;
            }
            // ★玩家背包：客户端从自己身上取 ✗ ⭐服务端用暂存（★不摸 Mantle 私有字段 ✓）
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
            int i = 0;
            for (BlockEntity be : new java.util.ArrayList<>(pending)) {
                if (be == null) {
                    continue;
                }
                SideInventoryContainer<BlockEntity> side = new SideInventoryContainer<>(
                        TinkerTables.craftingStationContainer.get(), ((net.minecraft.world.inventory.AbstractContainerMenu) (Object) this).containerId, inv, be,
                        -40 - i * 100, 18, 9);
                self.addSubContainer(side, true);
                i++;
            }
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携设备] 侧栏已加 {} 个（§1338 ✓）", i);
            pending.clear();
        } catch (Throwable t) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("[便携设备] 侧栏添加失败: {}", t.toString());
        }
    }
}
