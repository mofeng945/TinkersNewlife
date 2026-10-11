package com.mofengbaizhi.tinkersnewlife.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slimeknights.tconstruct.tables.menu.TabbedContainerMenu;

/**
 * §1353 工具包的**顶栏**：直接自己填 {@code stationBlocks}。
 *
 * <p>★为什么这么做（日志实证 ✓）：
 * <ul>
 *   <li>{@code TinkerTabsWidget#collectTabs} 遍历的就是 {@code menu.stationBlocks} ✓
 *       ⇒ 填好它顶栏就有了 ✓</li>
 *   <li>挂在 {@code TabbedContainerMenu#detectStationParts}的返回处 ✓
 *       ★这个混入**已经确定会执行**（它的探针打印了 ✓）
 *       ⭐不像挂原版 {@code Level} 那个混入（日志里始终没被调用 ✗）</li>
 *   <li>两端都会走到这里（服务端自己的菜单 ✓ 客户端匠魂的菜单 ✓）⇒ 两端一致 ✓</li>
 * </ul>
 *
 * <p>★方块状态用工匠台（{@code TabbedTableBlock} → 全匠魂唯一实现 {@code ITabbedBlock} 的方块 ✓）
 * ⇒ 顶栏图标会一样，但**悬停名字**取自各自的幽灵（{@code MenuProvider#getDisplayName} ✓）
 */
@Mixin(value = TabbedContainerMenu.class, remap = false)
public class ToolkitSideInventoryMixin {

    private static BlockState stateOf(String id) {
        Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
        return (b == null) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                : b.defaultBlockState();
    }

    @Inject(method = "detectStationParts", at = @At("RETURN"), remap = false)
    private void tnl$fillToolkitTabs(Level world, BlockPos start, CallbackInfo ci) {
        try {
            var support = com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.class;
            java.util.List<net.minecraft.world.level.block.entity.BlockEntity> pending =
                    com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.PENDING_SIDES;
            // §1360 ★只要虚拟表还在就填 ✓
            //   否则切到其它页（砧/台子/箱子的菜单）后顶栏就空了 ✗
            // §1361 ★只有在“工具包会话”内才出顶栏 ✓
            //（★否则用过一次工具包后 ✗ 开任何便携设备都会带上这排标签 ✗）
            if (!com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.inToolkit()) {
                return;
            }
            com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.touchToolkit();   // ★切页时续期 ✓
            TabbedContainerMenu<?> self = (TabbedContainerMenu<?>) (Object) this;
            BlockPos base = com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.VIRTUAL_BASE;
            self.stationBlocks.clear();
            // §1354 ★每个标签用**各自真实的方块状态** ✓
            //（★我们是自己填 stationBlocks ✗ ⭐绕过了扫描的 ITabbedBlock 判定 ✓）
            //  ⇒ 图标就是各自的方块（匠魂台/匠魂站/匠魂砧/部件台/强化台/铸造箱/部件箱）✓
            self.stationBlocks.add(org.apache.commons.lang3.tuple.Pair.of(base,
                    stateOf("tconstruct:crafting_station")));
            for (int i = 0; i < com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.DEVICES.length; i++) {
                self.stationBlocks.add(org.apache.commons.lang3.tuple.Pair.of(base.offset(i + 1, 0, 0),
                        stateOf(com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.DEVICES[i][1])));
            }
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info(
                    "[便携设备] 顶栏：自己填了 {} 个工作站@{}（§1353 ✓）",
                    self.stationBlocks.size(), base);
        } catch (Throwable t) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("[便携设备] 顶栏填充失败: {}", t.toString());
        }
    }
}
