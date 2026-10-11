package com.mofengbaizhi.tinkersnewlife.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slimeknights.tconstruct.tables.menu.TabbedContainerMenu;

/**
 * §1349 探针：看匠魂自己扫到了几个工作站（★顶栏 TinkerTabsWidget 的数据源就是它 ✓）。
 *
 * <p>★用户口径：**走匠魂原版链路**（stationBlocks ⇒ TinkerTabsWidget → 点标签切换）✗
 * ⭐不自己画侧栏 ✓ ⇒ 所以这里只打印，不做任何改动。
 */
@Mixin(value = TabbedContainerMenu.class, remap = false)
public class ToolkitSideInventoryMixin {

    @Inject(method = "detectStationParts", at = @At("RETURN"), remap = false)
    private void tnl$probeStationBlocks(Level world, BlockPos start, CallbackInfo ci) {
        try {
            TabbedContainerMenu<?> self = (TabbedContainerMenu<?>) (Object) this;
            java.util.List<?> blocks = self.stationBlocks;
            StringBuilder sb = new StringBuilder();
            for (Object o : blocks) {
                try {
                    var pair = (com.mojang.datafixers.util.Pair<?, ?>) o;
                    sb.append(pair.getFirst()).append(" ");
                } catch (Throwable ignored) {
                }
            }
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info(
                    "[探针·顶栏] 扫到工作站 {} 个：{}", blocks.size(), sb.toString());
        } catch (Throwable t) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("[探针·顶栏] 失败: {}", t.toString());
        }
    }
}
