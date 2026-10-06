package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.client.search.FluidSearch;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import slimeknights.tconstruct.smeltery.block.entity.tank.SmelteryTank;

/**
 * §1116 <b>熔炼炉 / 熔铸炉：按搜索词过滤流体条</b>（用户口径 ✓）。
 *
 * <h2>为什么改这一个方法就够（实读匠魂 3.11.2.166 源码 ✓）</h2>
 * `GuiSmelteryTank` 里**没有本地列表字段** ✓ —— 画流体条 / 判悬停 / 判点击 / 出 tooltip / `getFluidUnderMouse`
 * **全部**都调同一个 {@code calcLiquidHeights(boolean)} ✓（它内部缓存到 {@code liquidHeights} ✓）。
 * ⇒ 只要在它**返回处**把"不匹配的流体高度改成 <b>0</b>" ✓：
 * <ul>
 *   <li>{@code renderFluids} 会**跳过**高度 0 的格子（画不出东西 ✓）⇒ 画面上只剩命中的流体 ✓；</li>
 *   <li>而 {@code getFluidHovered(int[] heights, int y)} 的判定是"{@code y < heights[i]} 则返回 i，然后 {@code y -= heights[i]}" ✓
 *       —— 高度 0 时既不会命中该格、**也不会消耗 y** ✓ ⇒ <b>索引顺序完全不变</b> ✓✓
 *       （这正是本功能最容易出 bug 的地方 ✗：一般"过滤掉列表项"的写法会让点击索引错位 ✗，我们绕开了 ✓）。</li>
 * </ul>
 *
 * <h2>安全口径</h2>
 * <ul>
 *   <li>{@code require = 1} ✓（仓库 §767：mixin 匹配不上要**启动就报错** ✗ 不许静默失效 ✗）；</li>
 *   <li>{@code remap = false} ✓（目标是**模组类** ✓ 不是 MC 类 ✓ 与仓库既有的 Jade mixin 同一口径 ✓）；</li>
 *   <li>整段 `try/catch` ✓：任何意外都只是"过滤器失效" ✓ **绝不影响匠魂界面本身** ✓；</li>
 *   <li>未搜索（{@link FluidSearch#isActive()} == false）⇒ **直接返回原数组** ✓ 老行为零变化 ✓。</li>
 * </ul>
 *
 * <p>⚠ 搜索框本身在 {@code HeatingStructureScreen} 的 mixin 里（下一步 ✓）；
 * 匹配与语法见 {@link FluidSearch} ✓（拼音只走「通用拼音搜索」✓ 用户口径 ✓）。
 */
@Mixin(targets = "slimeknights.tconstruct.smeltery.client.screen.module.GuiSmelteryTank")
public abstract class GuiSmelteryTankSearchMixin {

    /** 匠魂自己缓存的储罐（数据源 ✓ 与它 `calcLiquidHeights` 里读的是同一个 ✓） */
    @Shadow
    private SmelteryTank<?> tank;

    /**
     * 在 {@code calcLiquidHeights(boolean)} **返回处**改数组 ✓（描述符 `(Z)[I` ✓ 私有方法也能注入 ✓）。
     * <p>只改"高度"✓ **不删条目** ✓ ⇒ 数组长度与索引都不变 ✓。
     */
    @Inject(method = "calcLiquidHeights(Z)[I", at = @At("RETURN"), require = 1, remap = false)
    private void tnl$filterFluids(boolean refresh, CallbackInfoReturnable<int[]> cir) {
        try {
            if (!FluidSearch.isActive()) return;
            int[] heights = cir.getReturnValue();
            if (heights == null || heights.length == 0) return;
            java.util.List<FluidStack> fluids = this.tank.getFluids();
            int n = Math.min(heights.length, fluids.size());
            int[] out = heights.clone();
            for (int i = 0; i < n; i++) {
                if (!FluidSearch.matches(fluids.get(i))) {
                    out[i] = 0;     // ★ 置 0：不画、也不占位置 ✓ 索引不变 ✓
                }
            }
            cir.setReturnValue(out);
        } catch (Throwable ignored) {
            // 兜底：过滤器失效而已 ✓ 界面照常 ✓
        }
    }
}
