package com.mofengbaizhi.tinkersnewlife.client.data;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 构筑术式拟造进度缓存 + HUD 进度条渲染（屏幕中央偏上）。
 * <p>
 * 服务端推送 剩余tick + 总tick；客户端以 {@link System#currentTimeMillis()} 毫秒插值
 * 本地平滑推进（1 tick = 50ms），不依赖客户端 gameTime 同步。
 */
public class ClientForgeData {

    /** 总耗时 tick（0 = 未在拟造） */
    private static long total = 0;
    /** 最近一次同步时的剩余 tick */
    private static long remaining = 0;
    /** 最近一次同步的毫秒时间戳 */
    private static long lastSyncMs = 0;
    /** 目标物品注册名 */
    private static String itemId = "";

    /** 收到进度同步（remaining<=0 且 total<=0 → 清除） */
    public static void update(long remainingIn, long totalIn, String itemIdIn) {
        if (totalIn <= 0) {
            total = 0;
            remaining = 0;
            itemId = "";
            return;
        }
        total = totalIn;
        remaining = Math.max(0, remainingIn);
        itemId = itemIdIn == null ? "" : itemIdIn;
        lastSyncMs = System.currentTimeMillis();
    }

    /** 当前剩余 tick（毫秒插值本地推进；已结束返回 <=0） */
    public static long remainingTicks() {
        if (total <= 0) return 0;
        long elapsedMs = System.currentTimeMillis() - lastSyncMs;
        long left = remaining - elapsedMs / 50L;
        return Math.max(0, left);
    }

    public static boolean isForging() {
        return total > 0 && remainingTicks() > 0;
    }

    /** Forge GUI Overlay 渲染入口（registerAboveAll） */
    public static void render(Gui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (total <= 0) return;
        long left = remainingTicks();
        if (left <= 0) return; // 已结束（清除包或自然结束）

        Font font = mc.font;
        int barW = 170;
        int barH = 7;
        int cx = screenWidth / 2;
        int barX = cx - barW / 2;
        // 屏幕中央偏上（快捷栏与经验条之间明显位置）
        int barY = screenHeight / 2 - 40;

        // 标题：构筑拟造中 · 目标名
        String name = itemName();
        Component title = Component.translatable("hud.tinkersnewlife.forge", name);
        graphics.drawString(font, title, cx - font.width(title) / 2, barY - 13, 0xFFD4924B);

        // 背景槽 + 边框
        graphics.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xFF000000);
        graphics.fill(barX, barY, barX + barW, barY + barH, 0x66000000);
        // 进度填充（浅金）
        double progress = 1.0 - (double) left / (double) Math.max(1, total);
        progress = Math.max(0.0, Math.min(1.0, progress));
        int fill = (int) Math.round(barW * progress);
        if (fill > 0) {
            graphics.fill(barX, barY, barX + fill, barY + barH, 0xFFE8B84B);
        }
        // 剩余秒数（右侧）
        Component remain = Component.literal("§f" + Math.max(1, (left + 19) / 20) + "s");
        graphics.drawString(font, remain, cx + barW / 2 + 7, barY - 1, 0xFFFFFF);
    }

    private static String itemName() {
        if (itemId.isEmpty()) return "";
        ResourceLocation loc = ResourceLocation.tryParse(itemId);
        if (loc == null) return itemId;
        var item = ForgeRegistries.ITEMS.getValue(loc);
        if (item == null) return itemId;
        return new ItemStack(item).getHoverName().getString();
    }
}
