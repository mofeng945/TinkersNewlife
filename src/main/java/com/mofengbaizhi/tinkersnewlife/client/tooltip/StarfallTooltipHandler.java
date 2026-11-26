package com.mofengbaizhi.tinkersnewlife.client.tooltip;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.StarfallLamentItem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * TNL900 ★★ 星陨悲叹工具提示：**可动星空背景** ✓
 *
 * <p>★用户提示：★"你可以看一下诡厄遗物相关实现" ✓
 * ⇒ ★反编译该模组的 {@code TooltipStarfieldHandler} ✓
 * （★用户整合包里就有这个类 ✓），它的做法就是标准答案 ✓：
 *
 * <ul>
 *   <li>★**不需要混入** ✗；★就用 {@code RenderTooltipEvent.Color} ✓；</li>
 *   <li>★该事件的 ``x/y`` **就是提示框最终坐标** ✓
 *       （★位置器已经算完 ✓ —— ★这就是我之前一直搞错的点 ✗）；</li>
 *   <li>★宽高直接用 ``event.getComponents()`` 里的
 *       {@code ClientTooltipComponent} 量出来 ✓
 *       （★这就是**原版自己的算法** ✓）；</li>
 *   <li>★画在 ``(x-3, y-4)`` 到 ``(x+w+3, y+h+4)`` ✓（★跟参考实现一致 ✓）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StarfallTooltipHandler {

    private static boolean mine(net.minecraft.world.item.ItemStack stack) {
        return stack != null && stack.getItem() instanceof StarfallLamentItem;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTooltipColor(RenderTooltipEvent.Color event) {
        try {
            if (!mine(event.getItemStack())) {
                return;
            }
            event.setBackground(0);
            event.setBorderStart(0);
            event.setBorderEnd(0);

            Font font = event.getFont();
            List<ClientTooltipComponent> components = event.getComponents();
            int width = 0;
            int height = components.size() == 1 ? -2 : 0;      // ★参考实现的同款修正 ✓
            for (ClientTooltipComponent c : components) {
                width = Math.max(width, c.getWidth(font));
                height += c.getHeight();
            }
            if (width <= 0 || height <= 0) {
                return;
            }
            int x = event.getX();
            int y = event.getY();
            /*
             * TNL926 ★★ 用户口径：★"我没说格子，我说的是**物品渲染到我新提示框背景的上层了**" ✓
             *
             * <p>★根因 ✗：★``GuiGraphics#fill`` 默认画在 **z = 0** ✗，
             * ★而原版提示框背景是画在 **z = 400** ✓
             * （★``renderTooltipInternal`` 里传的就是 400 ✓）
             * ⇒ ★物品图标（★同屏幕后画 ✗）反而压在星空上面 ✗。
             *
             * <p>★现在 ✓：★把星空整体**沿 z 平移 +400** ✓
             * （★pose 推入/弹出 ✓）⇒ ★星空面板与原版背景同层 ✓
             * ⇒ ★压在物品上面 ✓；★而文字是在同一层之后画的 ✓
             * ⇒ ★依然在星空之上 ✓✓
             */
            com.mojang.blaze3d.vertex.PoseStack tnl$pose = event.getGraphics().pose();
            tnl$pose.pushPose();
            tnl$pose.translate(0.0F, 0.0F, 400.0F);
            StarfieldPainter.draw(event.getGraphics(), x - 3, y - 4, width + 6, height + 8);
            tnl$pose.popPose();
        } catch (Throwable ignored) {
        }
    }
}
