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
            // ── §1117m 搜索框：改用 MC 原生的 `EditBox`（照抄仓库既有做法 ✓ 见类注释 ✓）──────
            //   位置：贴在匠魂那条流体列**正上方** ✓（用户口径「应该偏上一点」✓ 已从 -15 调到 -21 ✓）
            int bx = this.x;
            int by = this.y - 21;
            int bw = Math.max(60, this.width);
            int bh = 14;
            FluidSearch.setBoxRect(bx, by, bw, bh, 0F, 0F);
            net.minecraft.client.gui.components.EditBox box = FluidSearch.getEditBox();
            if (box != null) {
                // ⚠ 本方法在**已被平移**的姿态里被调用 ✓ ⇒ EditBox 的 x/y 直接用面板内相对坐标即可 ✓
                box.setX(bx);
                box.setY(by);
                box.setWidth(bw);
                box.render(graphics, 0, 0, 0F);      // 输入框（含光标/中文/退格）由 MC 自己画 ✓
            }
            // ── 命中项描边 ──────────────────────────────────────────────────────────
            if (!FluidSearch.isActive()) return;
            int[] heights = this.liquidHeights;
            if (heights == null || heights.length == 0) return;
            int bottom = this.y + this.width;          // ⚠ 与匠魂 renderFluids 完全一致（它写的就是 + width ✓）
            for (int i = 0; i < heights.length; i++) {
                int h = heights[i];
                if (h > 0) {                            // >0 ⇒ 命中（0 = 被我们过滤掉 ✓）
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
            int zeroed = 0;
            for (int i = 0; i < n; i++) {
                if (!FluidSearch.matches(fluids.get(i))) {
                    out[i] = 0;     // ★ 置 0：不画、也不占位置 ✓ 索引不变 ✓
                    zeroed++;
                }
            }
            // §1117j 临时诊断 ✓：确认"过滤到底有没有跑、跑了之后置零了几个"（每 40 次记一条 ✓ 免得刷屏 ✗）
            if (refresh && TNL$FILTER_DIAG.incrementAndGet() % 40 == 1) {
                FluidSearch.diag("过滤 查询='" + FluidSearch.getQuery() + "' 流体数=" + fluids.size()
                        + " 高度数组=" + heights.length + " 置零=" + zeroed);
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

    /** 临时诊断计数（定位完删 ✗） */
    private static final java.util.concurrent.atomic.AtomicInteger TNL$FILTER_DIAG =
            new java.util.concurrent.atomic.AtomicInteger();

    /**
     * §1117l <b>绘制端探针</b> ✓ —— 用户反馈「筛掉一个我没看到筛掉」✗：
     * 过滤端日志说 `置零=1` ✓ 却看不到变化 ✗ ⇒ 必须确认"**真正画的时候**拿到的是哪份数组" ✓。
     * <p>记 `渲染 高度=[a, b, c]` ✓ —— 若这里有 0 ⇒ 画的就是过滤后的（那问题在别处 ✓）；
     * 若这里没有 0 ⇒ **绘制走的不是我改的数组** ✗ ⇒ 改从 `renderFluids` 里重定向那次调用 ✓。
     */
    @Inject(method = "renderFluids(Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At("HEAD"), require = 1, remap = false)
    private void tnl$probeRender(PoseStack matrices, CallbackInfo ci) {
        try {
            if (!FluidSearch.isActive()) return;
            if (TNL$FILTER_DIAG.incrementAndGet() % 200 != 1) return;
            int[] h = this.liquidHeights;
            FluidSearch.diag("渲染 字段高度=" + (h == null ? "null" : java.util.Arrays.toString(h))
                    + " 流体数=" + this.tank.getFluids().size()
                    + " contained=" + this.tank.getContained());
        } catch (Throwable ignored) {
        }
    }
}
