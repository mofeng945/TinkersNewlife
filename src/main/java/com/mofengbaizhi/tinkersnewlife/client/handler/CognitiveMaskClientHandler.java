package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.CognitiveMaskItem;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 双向认知阻碍面具·客户端：<b>看不到佩戴者的名字</b>（对所有人，包括佩戴者自己）。
 *
 * <p>用 {@link RenderNameTagEvent} 的 {@code Result.DENY} 直接把名牌整块不画
 * （比"把内容改成空串"干净：连背景板都不会留 ✓）。
 * 该事件在 {@code EntityRenderer#renderNameTag} 里触发，而原版 {@code PlayerRenderer#renderNameTag}
 * 会 {@code super} 到它 ✓ —— 所以走原版名牌路径的一切渲染器都拦得住
 * （实测确认 YSM（是，史蒂夫模型）**完全不碰**名牌这条路径：它的 jar 里
 * {@code renderNameTag} / {@code shouldShowName}（SRG {@code m_7649_} / {@code m_6516_}）0 处引用 ✓）。
 *
 * <p>判断依据是<b>本客户端</b>那份 curios 数据（Curios 会同步装备），所以：
 * 只要观察者也装了本模组，就看不见名牌 ✓（整合包里所有人都有）。
 *
 * <h2>⭐ 为什么**连佩戴者自己**也要隐藏（用户实测教训）</h2>
 * 上一版这里是"跳过自己"（想着第三人称别一脸空白），结果用户报：
 * <b>"黑鸟操术飞出去的时候还是看到了自己的名牌"</b> ✗。
 * 原因在原版 {@code LivingEntityRenderer#shouldShowName} 对<b>本地玩家</b>的分支：
 * 第一人称时根本不画（相机就是自己），但**相机不是第一人称时会照常画** ——
 * 黑鸟操术把相机切到了黑鸟身上（{@code PacketBlackBirdCamera}），于是"自己"的名牌就冒出来了 ✗。
 * 第三人称（F5）同理。
 * <p>所以现在一律 DENY：面具的语义就是"没有名字"，戴着自己也看不到 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CognitiveMaskClientHandler {

    private CognitiveMaskClientHandler() {}

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        // ⚠ 不排除自己：黑鸟操术 / 第三人称下相机不是自己，原版会画本地玩家的名牌 ✗
        if (CognitiveMaskItem.isWorn(player)) {
            event.setResult(Event.Result.DENY);
        }
    }
}
