package com.mofengbaizhi.tinkersnewlife.client;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.StarfallLamentItem;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * TNL990 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u628a\u8010\u4e45\u5ea6\u90a3\u4e00\u884c\u63d0\u5230\u5de5\u5177\u63d0\u793a\u6846\u7269\u54c1\u540d\u79f0\u4e0b\u9762\u4e00\u884c" \u2713
 *
 * <p>\u2605\u4e3a\u4ec0\u4e48\u8981\u5728\u8fd9\u91cc\u52a8\uff1a\u2605\u90a3\u884c\u662f**\u539f\u7248\u81ea\u5df1\u52a0\u7684**
 * \uff08{@code ItemStack#getTooltipLines} \u91cc \u2713\uff09\uff0c\u2605\u800c\u5b83\u52a0\u7684\u4f4d\u7f6e\u5728**\u9644\u9b54\u4e4b\u540e** \u2717\u3002
 *
 * <p>\u2605\u4e0a\u4e00\u8f6e\u6211\u81ea\u5df1\u5199\u4e00\u884c\u7684\u505a\u6cd5\u5df2\u7ecf\u88ab\u8bc1\u660e\u4f1a**\u91cd\u590d**
 * \uff08\u2605\u56e0\u4e3a\u6211\u5199\u7684\u65f6\u5019\u539f\u7248\u90a3\u884c\u8fd8\u6ca1\u8fdb\u6765 \u2717\uff09\u3002
 *
 * <p>\u2605\u6b63\u89e3 \u2713\uff1a\u2605\u7528 Forge \u7684 {@code RenderTooltipEvent.GatherComponents}
 * \u2605\u5728"\u6253\u5305\u524d"\u628a\u539f\u7248\u7684\u90a3\u4e2a\u7ec4\u4ef6**\u79fb\u5230\u7b2c 2 \u4f4d**
 * \uff08\u2605\u7d27\u8ddf\u540d\u5b57 \u2713\uff09\uff0c\u2605\u4e0d\u65b0\u589e\u4e5f\u4e0d\u5220\u9664 \u2713 \u21d2 \u2605\u6c38\u8fdc\u53ea\u6709\u4e00\u884c \u2713\u3002
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT)
public final class StarfallTooltipFix {

    private StarfallTooltipFix() {
    }

    @SubscribeEvent
    public static void onGather(RenderTooltipEvent.GatherComponents event) {
        try {
            if (!event.getItemStack().getItem().getClass().equals(StarfallLamentItem.class)
                    && !(event.getItemStack().getItem() instanceof StarfallLamentItem)) {
                return;
            }
            var elements = event.getTooltipElements();
            int durIndex = -1;
            for (int i = 0; i < elements.size(); i++) {
                var e = elements.get(i);
                if (e.left().isPresent()) {
                    // TNL990 \u2605\u539f\u7248\u8fd9\u91cc\u662f FormattedText \u2717 \u21d2 \u2605\u5148\u5224\u5b83\u662f\u4e0d\u662f Component \u2713
                    if (e.left().get() instanceof Component c
                            && c.getContents() instanceof TranslatableContents tc
                            && "item.durability".equals(tc.getKey())) {
                        durIndex = i;
                        break;
                    }
                }
            }
            if (durIndex > 1) {
                var dur = elements.remove(durIndex);
                elements.add(1, dur);            // \u2605\u7b2c 2 \u4f4d = \u7d27\u8ddf\u540d\u5b57 \u2713
            }
        } catch (Throwable ignored) {
        }
    }
}
