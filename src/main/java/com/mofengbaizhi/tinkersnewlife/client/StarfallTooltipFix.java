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
 * TNL990 ★★ 用户：★"把耐久度那一行提到工具提示框物品名称下面一行" ✓
 *
 * <p>★为什么要在这里动：★那行是**原版自己加的**
 * （{@code ItemStack#getTooltipLines} 里 ✓），★而它加的位置在**附魔之后** ✗。
 *
 * <p>★上一轮我自己写一行的做法已经被证明会**重复**
 * （★因为我写的时候原版那行还没进来 ✗）。
 *
 * <p>★正解 ✓：★用 Forge 的 {@code RenderTooltipEvent.GatherComponents}
 * ★在"打包前"把原版的那个组件**移到第 2 位**
 * （★紧跟名字 ✓），★不新增也不删除 ✓ ⇒ ★永远只有一行 ✓。
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
                    // TNL990 ★原版这里是 FormattedText ✗ ⇒ ★先判它是不是 Component ✓
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
                elements.add(1, dur);            // ★第 2 位 = 紧跟名字 ✓
            }
        } catch (Throwable ignored) {
        }
    }
}
