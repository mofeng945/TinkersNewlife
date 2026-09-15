package com.mofengbaizhi.tinkersnewlife.network.curse;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.handler.ServantBossBarCleaner;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：请把"监守者血条"清掉（无为转变/咒灵操术的守护体被收回或战死时发）。
 *
 * <p><b>为什么需要</b>：给监守者加血条的是 **akaishi** 这个第三方 mod
 * （{@code com.example.akaishi.forge.life.WardenBossHandler}：每只监守者进世界就新建一个
 * {@code ServerBossEvent}，其 UUID 是随机的）。它在实体死亡时的清理没有把玩家从血条上摘掉，
 * 于是条会留在屏幕上；我们既不是那条血条的主人、也无法从服务端按 UUID 删掉它
 * （{@link com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedSpiritTechnique#clearEntityBossBar}
 * 只能删"用实体 UUID 当 id"的条）。
 *
 * <p>所以改成客户端兜底：客户端持有全部血条（名字 + UUID），收到本包后，
 * 在**场上已无其它监守者**的前提下，把名字为监守者的条移掉（见 {@link ServantBossBarCleaner}）。
 */
public class PacketDropWardenBars {

    public PacketDropWardenBars() {
    }

    public PacketDropWardenBars(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public static void handle(PacketDropWardenBars packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> ServantBossBarCleaner::request));
        ctx.get().setPacketHandled(true);
    }

    /** 便捷发送：广播给所有玩家（收到的一方自己判断该不该清） */
    public static void broadcast() {
        TinkersNewlife.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.ALL.noArg(),
                new PacketDropWardenBars());
    }
}
