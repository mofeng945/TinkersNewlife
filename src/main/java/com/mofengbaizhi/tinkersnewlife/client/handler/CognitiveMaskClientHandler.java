package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.CognitiveMaskItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 双向认知阻碍面具·客户端：<b>其他玩家看不到你的名字</b>。
 *
 * <p>用 {@link RenderNameTagEvent} 的 {@code Result.DENY} 直接把名牌整块不画
 * （比"把内容改成空串"干净：连背景板都不会留 ✗）。
 *
 * <p>判断依据是<b>本客户端</b>那份 curios 数据（Curios 会同步装备），所以：
 * 只要观察者也装了本模组，就看不见你的名牌 ✓（整合包里所有人都有）。
 * 自己看自己不受影响（第三人称下不至于连自己叫什么都看不见）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CognitiveMaskClientHandler {

    private CognitiveMaskClientHandler() {}

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Player self = Minecraft.getInstance().player;
        if (player == self) return;
        if (CognitiveMaskItem.isWorn(player)) {
            event.setResult(Event.Result.DENY);
        }
    }
}
