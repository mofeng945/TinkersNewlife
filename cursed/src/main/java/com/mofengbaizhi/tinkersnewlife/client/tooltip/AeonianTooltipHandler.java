package com.mofengbaizhi.tinkersnewlife.client.tooltip;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.AeonianOrderItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * TNL1060 user: the Aeonian Order tooltip, following the bow format.
 *
 * - fully transparent vanilla background, we paint our own;
 * - background is a field of eyes that keep opening and closing;
 * - the name and the declaration are painted with a flowing black / grey / red colour.
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT)
public final class AeonianTooltipHandler {

    private static final ResourceLocation EYE =
            new ResourceLocation(TinkersNewlife.MOD_ID, "textures/effect/aeonian_eye.png");
    private static final Random RNG = new Random();

    private AeonianTooltipHandler() {
    }

    private static boolean mine(net.minecraft.world.item.ItemStack stack) {
        return stack != null && stack.getItem() instanceof AeonianOrderItem;
    }

    /** black -> grey -> red loop, position and line index shift the phase. */
    /** TNL1063 user: white -> grey -> bright red now (black is gone and the red is lifted). */
    private static int flow(int index, int line, float t) {
        float u = (float) ((Math.sin(t * 1.7D + index * 0.22D + line * 0.55D) + 1.0D) * 0.5D);
        int r;
        int g;
        int b;
        if (u < 0.55F) {
            float k = u / 0.55F;                // white -> grey
            r = (int) (252 - 120 * k);
            g = (int) (252 - 120 * k);
            b = (int) (255 - 122 * k);
        } else {
            float k = (u - 0.55F) / 0.45F;      // grey -> bright red
            r = (int) (132 + 123 * k);
            g = (int) (132 - 92 * k);
            b = (int) (133 - 88 * k);
        }
        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static MutableComponent paint(String text, int line) {
        float t = (float) ((System.currentTimeMillis() % 60000L) / 1000.0D);
        MutableComponent out = Component.empty();
        for (int i = 0; i < text.length(); i++) {
            out.append(Component.literal(String.valueOf(text.charAt(i)))
                    .withStyle(Style.EMPTY.withColor(flow(i, line, t)).withBold(true)));
        }
        return out;
    }

    /** transparent vanilla background; we draw the eye field ourselves. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onColor(RenderTooltipEvent.Color event) {
        if (!mine(event.getItemStack())) {
            return;
        }
        event.setBackground(0x00000000);
        event.setBorderStart(0x00000000);
        event.setBorderEnd(0x00000000);

        // measure the tooltip from its components, exactly like the bow handler does
        int width = 0;
        int height = event.getComponents().size() == 1 ? -2 : 0;
        net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
        for (var c : event.getComponents()) {
            int w = c.getWidth(font);
            int h = c.getHeight();
            if (w > width) {
                width = w;
            }
            height += h;
        }
        GuiGraphics g = event.getGraphics();
        int x = event.getX();
        int y = event.getY();
        // TNL1061 user: raise z to 400 so the eye field sits above the item icons, like the bow tooltip
        var pose = g.pose();
        pose.pushPose();
        pose.translate(0.0F, 0.0F, 400.0F);
        g.fill(x - 3, y - 4, x + width + 3, y + height + 4, 0xFF080A10);   // TNL1064 user: opaque background, no transparency
        long time = System.currentTimeMillis();
        float t = (time % 60000L) / 1000.0F;
        int cols = Math.max(2, width / 14);
        int rows = Math.max(1, height / 14);
        // TNL1062 user: keep every eye strictly inside the box (they used to spill past the border)
        for (int cx = 0; cx < cols; cx++) {
            for (int cy = 0; cy < rows; cy++) {
                if (x - 2 + cx * 14 + 11 > x + width + 3) {
                    continue;
                }
                if (y - 3 + cy * 14 + 11 > y + height + 3) {
                    continue;
                }
                float phase = cx * 2.3F + cy * 3.7F;
                float open = 0.25F + 0.75F * (float) Math.abs(Math.sin(t * 0.9D + phase));
                int eh = Math.max(2, (int) (11 * open));
                int ex = x - 2 + cx * 14;
                int ey = y - 3 + cy * 14 + (11 - eh) / 2;
                g.blit(EYE, ex, ey, 0.0F, 0.0F, 11, eh, 16, 16);
            }
        }
        g.fill(x - 3, y - 4, x + width + 3, y - 3, 0xFFC03030);
        g.fill(x - 3, y + height + 3, x + width + 3, y + height + 4, 0xFFC03030);
        pose.popPose();
    }

    /** repaint the name and the declaration with the flowing colour. */
    @SubscribeEvent
    public static void onGather(RenderTooltipEvent.GatherComponents event) {
        if (!mine(event.getItemStack())) {
            return;
        }
        var lines = event.getTooltipElements();
        List<com.mojang.datafixers.util.Either<net.minecraft.network.chat.FormattedText,
                net.minecraft.world.inventory.tooltip.TooltipComponent>> rebuilt = new ArrayList<>();
        int quoteLine = 0;
        for (var el : lines) {
            if (el.left().isEmpty()) {
                rebuilt.add(el);
                continue;
            }
            net.minecraft.network.chat.FormattedText ft = el.left().get();
            String text = ft.getString();
            boolean isName = text.equals(event.getItemStack().getHoverName().getString());
            boolean isQuote = text.startsWith("\u4eba\u7c7b\u82e5\u751f\u6765") || text.trim().startsWith("\u2014\u2014");
            if (isName || isQuote) {
                MutableComponent painted = paint(text, isName ? 0 : ++quoteLine);
                rebuilt.add(com.mojang.datafixers.util.Either.left(painted));
            } else {
                rebuilt.add(el);
            }
        }
        lines.clear();
        lines.addAll(rebuilt);
    }
}
