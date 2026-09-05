package com.mofengbaizhi.tinkersnewlife.client.data;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 构筑术式拟造进度缓存 + HUD 进度条渲染（屏幕中下方）。
 * <p>
 * 数据由服务端 {@code PacketSyncForge} 推送（拟造期间每 5 tick 一次），
 * 客户端以本地 gameTime 计算剩余时间；到期（end 已过）自动不再绘制。
 */
public class ClientForgeData {

    /** 拟造开始 gameTime */
    private static long start;
    /** 拟造结束 gameTime */
    private static long end;
    /** 目标物品注册名（空 = 未拟造） */
    private static String itemId = "";

    public static void update(long startIn, long endIn, String itemIdIn) {
        start = startIn;
        end = endIn;
        itemId = itemIdIn == null ? "" : itemIdIn;
    }

    public static boolean isForging() {
        long now = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;
        return end > 0 && now < end;
    }

    public static String getItemId() {
        return itemId;
    }

    /** Forge GUI Overlay 渲染入口（registerAboveAll） */
    public static void render(Gui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        long now = mc.level.getGameTime();
        if (end <= 0 || now >= end) return;
        long total = Math.max(1, end - start);
        long left = end - now;
        double progress = 1.0 - (double) left / (double) total;
        progress = Math.max(0.0, Math.min(1.0, progress));

        Font font = mc.font;

        int barW = 150;
        int barH = 6;
        int cx = screenWidth / 2;
        int barX = cx - barW / 2;
        int barY = screenHeight - 46; // 快捷栏上方一点

        // 标题：构筑拟造中 · 目标名
        String name = itemName();
        Component title = Component.translatable("hud.tinkersnewlife.forge", name);
        graphics.drawString(font, title, cx - font.width(title) / 2, barY - 12, 0xFFD4924B);

        // 背景槽
        graphics.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xAA000000);
        graphics.fill(barX, barY, barX + barW, barY + barH, 0x66333333);
        // 进度填充（浅金）
        int fill = (int) Math.round(barW * progress);
        if (fill > 0) {
            graphics.fill(barX, barY, barX + fill, barY + barH, 0xFFE8B84B);
        }
        // 剩余秒
        Component remain = Component.literal("§7" + (left / 20 + 1) + "s");
        graphics.drawString(font, remain, cx + barW / 2 + 6, barY, 0xFFFFFF);
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
