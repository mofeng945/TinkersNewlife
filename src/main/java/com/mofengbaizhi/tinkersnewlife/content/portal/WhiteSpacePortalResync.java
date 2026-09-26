package com.mofengbaizhi.tinkersnewlife.content.portal;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>§682 落点重申</b>：跨维度传送落地后，<b>在接下来若干 tick 里把落点再发给客户端几遍</b>。
 *
 * <h2>为什么需要它（用户实测：F3 显示 253、人在往下掉，服务端却在 (277,2,-64)）</h2>
 * 跨维度传送必然要让服务端发一个 {@code ClientboundRespawnPacket}（客户端重建关卡 ✓），
 * 而客户端处理它的 {@code ClientPacketListener#handleRespawn} 会：
 * <ol>
 *   <li>{@code createPlayer(...)} <b>新建一个 LocalPlayer</b>；</li>
 *   <li>{@code updateSyncFields(旧玩家)} —— Forge 的 MC-10657 修复，
 *       <b>只拷 {@code xLast/yLast1/zLast}、朝向、onGround 这些"上一 tick 值"，不拷坐标</b> ✓；</li>
 *   <li>{@code resetPos()} —— 从"当前 Y"<b>向下</b>扫一个能站的位置：
 *       <pre>for (double d0 = getY(); d0 &gt; minBuildHeight &amp;&amp; d0 &lt; maxBuildHeight; ++d0)</pre>
 *       ⚠ 如果新建玩家的 Y 是 <b>目标维度高度之外</b>的值，这个循环一开始就不成立
 *       ⇒ <b>循环体一次都不执行 ⇒ 客户端就停在那个人为的高度上</b> ✗。
 *       <p>（§691 起伟大白色空间的高度已从 <b>0..15</b> 拓到 <b>0..399</b> ⇒
 *       "停在旧高度（例如采矿维度的 253 层）"这类情况<b>落在世界内</b>了，
 *       不再往虚空里无限下坠 ✓ —— 但"客户端自己爬起来"这件事仍然不能指望，
 *       所以本类的重申依旧必要 ✓。）</li>
 * </ol>
 * 服务端随后发的 {@code ClientboundPlayerPositionPacket} 本该把它摆正 ✓，但实测
 * <b>客户端会短暂（甚至持续）停在旧高度往虚空里掉</b> ✗ ⇒ 用户看到的就是
 * 「F3 还是 253、一直自由落体」，而服务端其实一切都对 ✓。
 *
 * <h2>做法</h2>
 * 只做一件事：落地后在<b>接下来 {@link #RESEND_TICKS} 个 tick 内，<b>每 tick</b> 重发一次位置包</b> ✓
 * （<b>必须每 tick</b> —— 见 §688：稀疏发包会让位置不同步立刻回来 ✗），
 * 发满窗口就结束，<b>不设早停</b> ✓。
 * <ul>
 *   <li>用的就是 vanilla 自己在"位置包没被确认"时用的那招
 *       （{@code ServerGamePacketListenerImpl#handleMovePlayer} 里 {@code awaitingPositionFromClient != null}
 *       时会重发，见 1.20.1 源码 L852-L855 ✓），只是我们更主动、覆盖到关卡重建那几帧 ✓；</li>
 *   <li><b>不带任何"传送"语义</b>：只是把服务端<b>已经在那儿的坐标</b>再说一遍 ✓
 *       ⇒ 不会造成二次传送、不会动背包/进度 ✓；</li>
 *   <li>玩家不在目标维度（例如又走掉了）就<b>直接丢弃</b>这条重申 ✓，绝不把人拽回来 ✗。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class WhiteSpacePortalResync {

    /** 重申窗口（tick）——2 秒，覆盖客户端重建关卡（本整合包约 1~2 秒）✓ */
    private static final int RESEND_TICKS = 40;

    /**
     * ⚠⚠ <b>§688 用户实测结论：必须<b>每 tick</b> 发，不能稀疏 ✗</b>
     *
     * <p>§686 我曾把它改成"每 4 tick 发一次"想减轻玩家被钉住的感觉 ✗ ——
     * 用户实测立刻反馈：<b>「稀疏发包后，位置不同步问题又恢复了」</b> ✗✓
     *
     * <p>原因（复盘）：客户端重建关卡那 1~2 秒里，它的<b>下落物理每 tick 都会把位置覆盖掉</b> ✓，
     * 只有<b>持续压回去</b>才能撑到它加载完 ✓。一旦留出空隙：
     * <ul>
     *   <li>空隙里服务端会接受客户端上报的下坠位置（`ServerGamePacketListenerImpl`
     *       的 `moved wrongly` 分支会 `absMoveTo(客户端位置)` ✗）⇒ 服务端自己也开始往下漂 ✗；</li>
     *   <li>于是"越同步越差"，重申等于白做 ✗。</li>
     * </ul>
     * ⇒ <b>本常量固定为 1（每 tick），不要再优化成稀疏发包</b> ✗。
     */
    private static final int RESEND_EVERY = 1;

    private WhiteSpacePortalResync() {
    }

    /** 一条待重申的落点 */
    private record Pending(ResourceKey<Level> dimension, double x, double y, double z,
                           float yRot, float xRot, int ticksLeft) {
    }

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    /**
     * 安排"这个玩家落到这里了，接下来几 tick 再说几遍"。
     * <p>⚠ 由 {@code WhiteSpacePortalBlock#entityInside} 在<b>传送成功之后</b>调用，
     * 坐标取<b>服务端此刻真实所在</b>（即 {@code landing} ✓）。
     */
    public static void schedule(ServerPlayer player, ServerLevel target, BlockPos landing) {
        PENDING.put(player.getUUID(), new Pending(
                target.dimension(),
                landing.getX() + 0.5D, landing.getY(), landing.getZ() + 0.5D,
                player.getYRot(), player.getXRot(),
                RESEND_TICKS));
    }

    /**
     * 每 tick 重发一次（服务端 tick 末尾）。
     * <p>⚠ 只"重说坐标"，不做别的 ✓；玩家换了维度／掉线就丢弃 ✓。
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (PENDING.isEmpty()) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;

        Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pending> entry = it.next();
            Pending p = entry.getValue();
            if (p.ticksLeft() <= 0) {
                it.remove();
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();            // 掉线了：丢弃
                continue;
            }
            if (!player.serverLevel().dimension().equals(p.dimension())) {
                it.remove();            // 人已经不在目标维度了：绝不把他拽回来 ✗
                continue;
            }
            /*
             * ⚠ §688：**不要**在这里加"玩家离开落点就停止"之类的早停判据 ✗ ——
             * §686 加过，结果被"服务端朝客户端下坠位置漂移"误触发，
             * 直接把重申关掉 ⇒ 用户实测「位置不同步又恢复了」✗。
             * 现在的口径很干脆：**窗口内每 tick 都发，发满 40 tick 就结束** ✓。
             */
            int left = p.ticksLeft() - 1;
            entry.setValue(new Pending(p.dimension(), p.x(), p.y(), p.z(),
                    p.yRot(), p.xRot(), left));
            if (left % RESEND_EVERY != 0) continue;
            /*
             * ⚠ 只发位置包（ServerGamePacketListenerImpl#teleport 的 5 参版本 ⇒ 绝对坐标 ✓），
             * 不碰维度、不碰背包、不碰进度 ✓ —— 等价于"把服务端的坐标再说一遍" ✓。
             */
            player.connection.teleport(p.x(), p.y(), p.z(), p.yRot(), p.xRot());
        }
    }
}
