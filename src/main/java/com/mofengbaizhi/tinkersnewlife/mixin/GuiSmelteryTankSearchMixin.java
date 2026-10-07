package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.client.search.FluidSearch;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

    /** 匠魂缓存的"每格流体高度"（过滤后我们就是改这份 ✓ 高亮也读它 ✓） */
    @Shadow
    private int[] liquidHeights;

    /** 流体条那一列的布局（匠魂自己的字段 ✓ 高亮要用同一套坐标 ✓） */
    @Shadow @org.spongepowered.asm.mixin.Final private int x;
    @Shadow @org.spongepowered.asm.mixin.Final private int y;
    @Shadow @org.spongepowered.asm.mixin.Final private int width;

    /**
     * §1116 <b>命中项描边高亮</b>（用户口径：「并将符合的流体高亮出来」✓）。
     *
     * <p>挂点选在 {@code renderHighlight(GuiGraphics, int, int)} ✓ —— 它是**每帧都会被界面调用**的方法 ✓
     * 而且**自带 {@code GuiGraphics}** ✓（`renderFluids` 只有 `PoseStack` ✗ 画不了高亮 ✗）。
     *
     * <p>坐标算法与匠魂 `renderFluids` **逐行一致** ✓（包括它那句 `bottom = y + width` 的写法 ✓ —— 那是匠魂自己的布局 ✓
     * 照抄才对得上 ✓）；高度为 0 的格子 = 被过滤掉的 ✓ ⇒ **有高度=命中** ✓ 只给它们描边 ✓。
     * <p>未搜索时直接返回 ✓ ⇒ **平时一根描边都不会出现** ✓（老观感零变化 ✓）。
     */
    @Inject(method = "renderHighlight(Lnet/minecraft/client/gui/GuiGraphics;II)V", at = @At("HEAD"), require = 1, remap = false)
    private void tnl$highlightMatches(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY,
                                      org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        try {
            // ── §1117n 搜索框：**回退成自绘** ✓（§1117m 的 EditBox 在匠魂界面里拿不到派发 ✗ ⇒ 空壳 ✗）──
            //   位置：贴在匠魂那条流体列**正上方** ✓（用户口径「应该偏上一点」✓ 由 -15 调到 -21 ✓）
            int bx = this.x;
            int by = this.y - 21;
            int bw = Math.max(60, this.width);
            int bh = 14;
            FluidSearch.setBoxRect(bx, by, bw, bh, 0F, 0F);
            graphics.fill(bx, by, bx + bw, by + bh, 0xC0101010);
            int border = FluidSearch.isFocused() ? 0xFF7FD4FF : 0xFF505050;   // 聚焦时亮蓝 ✓
            graphics.fill(bx, by, bx + bw, by + 1, border);
            graphics.fill(bx, by + bh - 1, bx + bw, by + bh, border);
            graphics.fill(bx, by, bx + 1, by + bh, border);
            graphics.fill(bx + bw - 1, by, bx + bw, by + bh, border);
            String q = FluidSearch.getQuery();
            // §1117c 用户口径：「搜索提示只保留搜索两个字就好」✓ 空查询时只显示「搜索」✓
            graphics.drawString(net.minecraft.client.Minecraft.getInstance().font,
                    q.isEmpty() ? "搜索" : q,
                    bx + 4, by + 3, q.isEmpty() ? 0xFF707070 : 0xFFFFFFFF, false);
            // ── 命中项描边 ──────────────────────────────────────────────────────────
            if (!FluidSearch.isActive()) return;
            int[] heights = this.liquidHeights;
            if (heights == null || heights.length == 0) return;
            java.util.List<FluidStack> fluids = this.tank.getFluids();
            // §1117q ⚠ 高亮坐标必须与"重新排布后"的绘制位置一致 ✗
            //   （用户口径：筛选后留一大段空档 ⇒ 要求**重新绘制一次所有流体** ✓）
            //   ⇒ 同样只把**匹配的**条从底部紧凑往上堆 ✓（被筛掉的不占位置 ✓）
            int bottom = this.y + this.width;          // ⚠ 与匠魂 renderFluids 同一基准（它写的就是 + width ✓）
            for (int i = 0; i < heights.length && i < fluids.size(); i++) {
                if (!FluidSearch.matches(fluids.get(i))) continue;     // 被筛掉的不占位置 ✓
                int h = heights[i];
                if (h > 0) {                            // >0 ⇒ 命中 ✓
                    int top = bottom - h;
                    // 1px 琥珀色描边（四边各一条 fill ✓）
                    graphics.fill(this.x, top, this.x + this.width, top + 1, 0xFFFFD24A);
                    graphics.fill(this.x, top + h - 1, this.x + this.width, top + h, 0xFFFFD24A);
                    graphics.fill(this.x, top, this.x + 1, top + h, 0xFFFFD24A);
                    graphics.fill(this.x + this.width - 1, top, this.x + this.width, top + h, 0xFFFFD24A);
                }
                bottom -= h;
            }
        } catch (Throwable ignored) {
        }
    }

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
            // §1117l ⚠ 关键：**字段也要一起改** ✗！
            //   匠魂 `calcLiquidHeights(boolean)` 内部是 `this.liquidHeights = calcLiquidHeights(...); return this.liquidHeights;` ✓
            //   —— 只改**返回值**的话，凡是**直接读字段**的路径（高亮 ✓ tooltip ✓ 别的渲染 ✓）仍会拿到**未过滤**的数组 ✗
            //   ⇒ 用户实测「筛掉一个我没看到筛掉」✗ 就是这么来的 ✓
            this.liquidHeights = out;
            cir.setReturnValue(out);
        } catch (Throwable ignored) {
            // 兜底：过滤器失效而已 ✓ 界面照常 ✓
        }
    }

    /**
     * ⭐ §1117p <b>真正让"流体条"消失的修复</b> ✓（用户口径：「筛选了，但流体条的绘制没有任何变化，
     * 只有鼠标放上去高亮的显示变了」✗）。
     *
     * <h2>为什么"把高度置 0"看不到效果 ✗</h2>
     * 我原来只把不匹配项的**高度置 0** ✓，指望匠魂不画 ✓ —— 但日志 + 用户观察表明 ✗：
     * **高亮变了**（高亮读的是 `liquidHeights` 字段 ✓ 已过滤 ✓）而**条没变** ✗
     * ⇒ 说明 `GuiUtil.renderTiledFluid(...)` 拿到 **height = 0 照样把流体画满了** ✗（Mantle 那个实现的问题 ✓）。
     *
     * <h2>修法：直接跳过那次绘制调用 ✓</h2>
     * 用 `@Redirect` 拦下 `renderFluids` 里对
     * `GuiUtil.renderTiledFluid(...)` 的调用 ✓：**不匹配的流体直接 return 不画** ✓，
     * 匹配的照原样转发 ✓ ⇒ 与"高度/高亮/命中判定"用**同一个** `FluidSearch.matches` ✓ 保证三者一致 ✓。
     * <p>描述符**实测取自 class 常量池** ✓（不是猜的 ✗）：
     * {@code (Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;Lnet/minecraftforge/fluids/FluidStack;IIIII)V}
     */
    @org.spongepowered.asm.mixin.injection.Redirect(
            method = "renderFluids(Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At(value = "INVOKE",
                    target = "Lslimeknights/tconstruct/library/client/GuiUtil;renderTiledFluid(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;Lnet/minecraftforge/fluids/FluidStack;IIIII)V",
                    remap = false),
            require = 1, remap = false)
    private void tnl$skipNonMatchingFluid(PoseStack pose,
                                          net.minecraft.client.gui.screens.inventory.AbstractContainerScreen parent,
                                          FluidStack fluid, int fx, int fy, int fw, int fh, int extra) {
        int ny = fy;
        try {
            if (FluidSearch.isActive() && this.liquidHeights != null) {
                java.util.List<FluidStack> fluids = this.tank.getFluids();
                int idx = indexOfFluid(fluids, fluid);
                if (idx >= 0 && !FluidSearch.matches(fluid)) {
                    return;                     // ★ 不匹配 ⇒ 完全不画 ✓（画面上那一根消失 ✓）
                }
                if (idx >= 0) {
                    // ⭐ §1117q 用户口径：「**筛选出来长这样（留一大段空档），你应该重新绘制一次所有流体**」✓
                    //   ⇒ 匠魂是用**原始高度**一路累加算出每根的位置 ✗（被我跳过的那些仍占位置 ⇒ 出现空档 ✗）
                    //   ⇒ 这里**自己重算 y**：只把"匹配的"条一条条**从底部紧凑往上堆** ✓✓
                    int bottom = this.y + this.width;         // 与匠魂 renderFluids 同一套基准 ✓
                    int[] h = this.liquidHeights;
                    for (int i = 0; i < h.length && i < fluids.size(); i++) {
                        if (!FluidSearch.matches(fluids.get(i))) continue;   // 被筛掉的不占位置 ✓
                        int top = bottom - h[i];
                        if (i == idx) {
                            ny = top;
                            break;
                        }
                        bottom -= h[i];
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        slimeknights.tconstruct.library.client.GuiUtil.renderTiledFluid(pose, parent, fluid, fx, ny, fw, fh, extra);
    }

    /** 在流体表里找这个 FluidStack 的下标 ✓（先按**同一实例**比 ✓ 再按 equals 兜底 ✓） */
    private static int indexOfFluid(java.util.List<FluidStack> fluids, FluidStack target) {
        for (int i = 0; i < fluids.size(); i++) {
            if (fluids.get(i) == target) return i;
        }
        for (int i = 0; i < fluids.size(); i++) {
            if (fluids.get(i).equals(target)) return i;
        }
        return -1;
    }
}
