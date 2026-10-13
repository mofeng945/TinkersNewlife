package com.mofengbaizhi.tinkersnewlife.client.hud;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.BossEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * TNL901 \u2605\u2605 \u514b\u56fe\u9732\u00b7\u58a8\u9ed8\u7684**\u81ea\u7ed8 Boss \u8840\u6761** \u2713
 *
 * <p>\u2605\u7528\u6237\u53e3\u5f84\uff1a\u2605"\u8840\u6761\u6750\u8d28\u653e\u684c\u9762\u4e86\uff0c\u4f60\u518d\u6700\u5de6\u4fa7\u52a0\u4e00\u4e2a\u58a8\u9ed8\u7684\u6b63\u8138\u62fc\u6210\u4e00\u4e2a boss \u6761\uff0c
 *  \u7136\u540e\u5728\u91cc\u9762\u7528\u9ed1\u767d\u7070\u8272\u5e26\u4ee3\u8868\u8840\u6761\uff1a\u8840\u6761\u7070\u5ea6\u968f\u7740\u8840\u91cf\u4e0b\u964d\u9010\u6e10\u53d8\u5316\u7531\u767d\u8272\u53d8\u4e3a\u7070\u8272\uff0c
 *  \u540c\u65f6\u7ed9\u8840\u6761\u8272\u5e26\u52a0\u4e00\u4e9b\u6d41\u52a8\u6548\u679c" \u2713
 *
 * <ul>
 *   <li>\u2605\u7528 {@code CustomizeGuiOverlayEvent.BossEventProgress}\uff1a\u2605\u53d6\u6d88\u539f\u7248\u6761 \u002b \u81ea\u5df1\u753b \u2713\uff1b</li>
 *   <li>\u2605\u6846\u4f53\uff1a\u2605\u684c\u9762\u90a3\u5f20 ``\u8840\u6761.png``\uff08256\u00d724\uff09\u2713
 *       \u2014\u2014\u2605\u62f7\u6210\u65b0\u6587\u4ef6 ``textures/gui/ktulu_bar.png`` \u2713\uff08\u2605\u672a\u8986\u76d6\u4efb\u4f55\u65e7\u8d44\u6e90 \u2713\uff09\uff1b</li>
 *   <li>\u2605\u6b63\u8138\uff1a\u2605\u76f4\u63a5\u4ece ``textures/entity/momo_common.png``\uff08\u260564\u00d764 \u76ae\u80a4 \u2713\uff09
 *       \u91cc**\u88c1\u8138\u90e8 8\u00d78** \u2713\uff08\u2605\u5750\u6807 8,8 \u2713\uff09\u21d2 \u2605\u4e0d\u9700\u8981\u989d\u5916\u753b\u8138 \u2713\uff1b</li>
 *   <li>\u2605\u8840\u5e26\uff1a\u2605\u9ed1\u767d\u7070 \u2713\uff1b\u2605\u7070\u5ea6 = 0.35 + 0.65 \u00d7 \u8840\u91cf\u6bd4 \u2713
 *       \uff08\u2605\u6ee1\u8840\u7eaf\u767d \u2192 \u2605\u6b8b\u8840\u6df1\u7070 \u2713\uff09\uff1b</li>
 *   <li>\u2605\u6d41\u52a8\uff1a\u2605\u4e00\u9053\u6a2a\u5411\u9ad8\u5149\u4e0d\u65ad\u626b\u8fc7 \u002b \u2605\u661f\u70b9\u95ea\u70c1 \u2713\u3002</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class KtuluBossBarOverlay {

    private static final ResourceLocation BAR =
            new ResourceLocation(TinkersNewlife.MOD_ID, "textures/gui/ktulu_bar.png");
    private static final ResourceLocation SKIN =
            new ResourceLocation(TinkersNewlife.MOD_ID, "textures/entity/momo_common.png");

    private static String tnl$name = null;

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        try {
            BossEvent be = event.getBossEvent();
            if (be == null) {
                return;
            }
            if (tnl$name == null) {
                tnl$name = Component.translatable("entity.tinkersnewlife.ktulu_momo.bossbar").getString();
            }
            if (!tnl$name.equals(be.getName().getString())) {
                return;
            }
            event.setCanceled(true);                 // \u2605\u5c4f\u853d\u539f\u7248\u6761 \u2713
            draw(event.getGuiGraphics(), event.getX(), event.getY(), be.getProgress());
        } catch (Throwable ignored) {
        }
    }

    private static void draw(GuiGraphics g, int x, int y, float progress) {
        float p = Math.max(0.0F, Math.min(1.0F, progress));
        // \u2605\u6846\u4f53\uff08\u2605\u6574\u5f20 256\u00d724 \u6309\u539f\u5c3a\u5bf8\u8d34 \u2713\uff09
        g.blit(BAR, x - 28, y - 6, 0, 0, 256, 24);
        // \u2605\u6b63\u8138\uff08\u2605\u76ae\u80a4\u8138\u90e8 8\u00d78 \u4f4d\u4e8e (8,8) \u2713\uff09
        g.blit(SKIN, x - 24, y - 4, 20, 20, 8.0F, 8.0F, 8, 8, 64, 64);

        int bx = x + 2;
        int by = y + 4;
        int bw = 176;
        int bh = 8;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xC0000000);      // \u2605\u69fd\u5e95 \u2713
        int fillW = (int) (bw * p);
        if (fillW > 0) {
            // \u2605\u7070\u5ea6\u968f\u8840\u91cf\uff1a\u2605\u6ee1\u8840\u7eaf\u767d \u2192 \u2605\u6b8b\u8840\u6df1\u7070 \u2713
            int gray = (int) (255.0F * (0.35F + 0.65F * p));
            if (gray > 255) {
                gray = 255;
            }
            int col = 0xFF000000 | (gray << 16) | (gray << 8) | gray;
            g.fill(bx, by, bx + fillW, by + bh, col);

            long t = System.currentTimeMillis();
            // \u2605\u6d41\u52a8\u9ad8\u5149\u626b\u8fc7 \u2713
            int span = 56;
            int off = (int) ((t / 10L) % (bw + span)) - span;
            int gx0 = Math.max(bx, bx + off);
            int gx1 = Math.min(bx + fillW, bx + off + span);
            if (gx1 > gx0) {
                g.fill(gx0, by, gx1, by + bh, 0x50FFFFFF);
            }
            // \u2605\u661f\u70b9\u95ea\u70c1 \u2713
            for (int i = 0; i < 26; i++) {
                long seed = (i + 1) * 2654435761L;
                int sx = bx + (int) Math.floorMod((seed >>> 9) % Math.max(1, fillW), Math.max(1, fillW));
                int sy = by + (int) ((seed >>> 17) % bh);
                double tw = (Math.sin(t / 460.0 + i * 1.3) + 1.0) / 2.0;
                int a = (int) (30 + 130 * tw);
                g.fill(sx, sy, sx + 1, sy + 1, (a << 24) | 0xFFFFFF);
            }
        }
        // \u2605\u4e0a\u4e0b\u767d\u7070\u7ec6\u8fb9 \u2713
        g.fill(bx, by - 1, bx + bw, by, 0x60FFFFFF);
        g.fill(bx, by + bh, bx + bw, by + bh + 1, 0x60FFFFFF);
    }
}
