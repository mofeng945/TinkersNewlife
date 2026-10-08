package com.mofengbaizhi.tinkersnewlife.network.tools;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * ⭐ §1135 <b>「交替挥动 —— 副手那一挥」的 S2C 包</b>（用户口径四轮实测逼出来的 ✓）。
 *
 * <h2>⚠⚠ 为什么**不能用物品 NBT** 当信号（实测确认 ✗）</h2>
 * 上一版把"该挥哪只手"写进物品 NBT（{@code lnb_swing} ✓）⇒ 客户端探针实测：
 * <pre>
 * 客户端 counter=41 → 42 → 46 → 50 → 57 → 62 → …（⭐ 大约**每秒**才跳一次 ✗）
 * 服务端 命中 27 行（fever 25→34 递增 ✓ ⇒ 服务端每击都在写 ✓）
 * </pre>
 * ⇒ ⭐⭐ **物品 NBT 的同步不是"改一次推一次"** ✗（大约 1 秒一批 ✓）⇒ 客户端**每秒才看到一次变化** ✗
 * ⇒ 副手那一挥**每秒才播一次** ✗ ＝ ⭐ 用户说的「**左手极少挥动**」✓
 * （⭐ 而且跳变时一次跨好几拍 ⇒ 奇偶随机 ⇒ 左手更少 ✓）。
 * <p>⇒ ⭐ **正解：服务端每击直接给"该玩家自己"发一个包** ✓ ——
 * 一拍的延迟都没有 ✓ 也不依赖 NBT 的推送时机 ✓。
 *
 * <h2>⚠ 为什么包体是空的 ✗</h2>
 * 用 {@code PacketDistributor.PLAYER} **只发给本人** ✓ ⇒ ⭐ 客户端只要"收到"就知道该挥左臂 ✓
 * ⇒ 不需要任何字段 ✓（⚠ 也就不用担心编解码 ✓）。
 *
 * <h2>⚠ 为什么必须过 {@code DistExecutor}（本仓 §801 的硬规矩 ✓）</h2>
 * 本类的 {@code handle} 在**两端**都会被类加载 ✓ ⇒ ⚠ 若直接引用客户端类 ✗
 * **专服会 `NoClassDefFoundError`** ✗ ⇒ ⭐ 一律经 {@code unsafeRunWhenOn(Dist.CLIENT, …)} 甩给客户端 ✓。
 */
public class PacketSwingOffhand {

    public PacketSwingOffhand() {
    }

    /** ⭐ 空包体 ✓（只是为了"通知一声"✓） */
    public PacketSwingOffhand(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public static void handle(PacketSwingOffhand packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                // ⚠ 绝不能在专服直接碰客户端类 ✗（§801 的教训 ✓）
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        com.mofengbaizhi.tinkersnewlife.client.renderer.LongShortBladeSpinHandler
                                .clientStartOffhandSwing()));
        ctx.get().setPacketHandled(true);
    }

    /** ⭐ 服务端调用：告诉某个玩家"该挥副手了" ✓（⭐ 只发给他本人 ✓） */
    public static void sendTo(net.minecraft.server.level.ServerPlayer player) {
        if (player == null) {
            return;
        }
        try {
            TinkersNewlife.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new PacketSwingOffhand());
        } catch (Throwable ignored) {
            // ⭐ 发不出去只是那一挥没播 ✓ 绝不能连累玩法 ✗
        }
    }
}
