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

    @Inject(method = "detectStationParts", at = @At("RETURN"), remap = false)
    private void tnl$fillToolkitTabs(Level world, BlockPos start, CallbackInfo ci) {
        try {
            var support = com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.class;
            java.util.List<net.minecraft.world.level.block.entity.BlockEntity> pending =
                    com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.PENDING_SIDES;
            boolean toolkit = com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.CLIENT_TOOLKIT
                    || !pending.isEmpty();
            if (!toolkit) {
                return;
            }
            TabbedContainerMenu<?> self = (TabbedContainerMenu<?>) (Object) this;
            Block tabbed = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("tconstruct:crafting_station"));
            if (tabbed == null) {
                return;
            }
            BlockState state = tabbed.defaultBlockState();
            BlockPos base = com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.VIRTUAL_BASE;
            self.stationBlocks.clear();
            for (int i = 0; i < 7; i++) {   // §1353 主实体 + 6 个设备
                // §1353 ★用 commons 的 Pair（stationBlocks 的元素类型）
                self.stationBlocks.add(org.apache.commons.lang3.tuple.Pair.of(base.offset(i, 0, 0), state));
            }
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info(
                    "[便携设备] 顶栏：自己填了 {} 个工作站@{}（§1353 ✓）",
                    self.stationBlocks.size(), base);
        } catch (Throwable t) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("[便携设备] 顶栏填充失败: {}", t.toString());
        }
    }
}
