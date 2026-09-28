package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.CognitiveMaskItem;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
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
 *
 * <h2>⭐ §796 为什么必须用 {@code EventPriority.LOWEST}（NL 包实测 ✗）</h2>
 * 用户报：「NL 整合包带了认知阻碍面具**还是显示了名字**」✗ —— 查到两个硬事实：
 * <ol>
 *   <li><b>神秘遗物（EnigmaticLegacy 2.30.1）</b>的 {@code EnigmaticEventHandler#renderNameplate}
 *       会对<b>本地玩家自己</b>的名牌设 {@code Result.ALLOW} ✗ ——
 *       条件是"戴了它家的<b>徽章（INSIGNIA）</b>且那件饰品的 {@code tagDisplayEnabled} 为真（默认真）"✓
 *       （字节码实核：{@code getEntity() == Minecraft.player} ⇒ 取 INSIGNIA curio ⇒ {@code setResult(ALLOW)} ✓；
 *        它的注解是**默认优先级** {@code @SubscribeEvent} ✓ —— 同一次 javap -v 里那个 {@code LOWEST} 是
 *        {@code renderCape} 的 ✓ 不是这个 ✓）；</li>
 *   <li><b>Forge 事件总线不会"已经有人设过 result 就不再派发"</b> ✗ ——
 *       eventbus <b>6.2.33</b> 的 {@code EventBus#post(Event, IEventBusInvokeDispatcher)} 字节码实核：
 *       循环里只有"取监听器 ⇒ invoke"，<b>没有</b> {@code getResult()} 之类的短路判断 ✓
 *       ⇒ <b>所有监听器都会跑，最后设的那个赢</b> ✓。</li>
 * </ol>
 * ⇒ 两条合起来就是：神秘遗物先把 result 设成 {@code ALLOW} ✗，我们若还在**默认优先级**跑，
 * 就被后来的它盖掉 ✗（NL 包里就是这个现象 ✓）。
 * <p>⇒ 本处理器改用 {@link EventPriority#LOWEST} ✓：<b>比默认优先级晚跑</b> ⇒
 * 我们的 {@code DENY} 是最后一个写进去的 ✓ ⇒ 面具的意思就是"没有名字" ✓（徽章也压不过它 ✓）。
 * <p>⚠ 同优先级内按注册顺序 ✗，所以这里不跟任何人抢 LOWEST：
 * NL 包里另一个会设 {@code DENY} 的（终末图书馆）方向一致 ✓，给怪物画名牌的（L2Hostility 等）
 * 不碰玩家 ✓ ⇒ 不会互相打架 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CognitiveMaskClientHandler {

    private CognitiveMaskClientHandler() {}

    /**
     * ⚠ §796：<b>必须是 {@code LOWEST}</b> ✗ —— 见类注释：Forge 总线"最后设的赢"，
     * 而神秘遗物的徽章会用默认优先级设 {@code ALLOW} ⇒ 我们要比它晚 ✓。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        // ⚠ 不排除自己：黑鸟操术 / 第三人称下相机不是自己，原版会画本地玩家的名牌 ✗
        if (CognitiveMaskItem.isWorn(player)) {
            event.setResult(Event.Result.DENY);
        }
    }
}
