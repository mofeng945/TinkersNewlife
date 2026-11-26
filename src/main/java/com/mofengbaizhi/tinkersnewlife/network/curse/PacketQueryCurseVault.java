package com.mofengbaizhi.tinkersnewlife.network.curse;

import com.mofengbaizhi.tinkersnewlife.content.block.CurseVaultBlock;
import com.mofengbaizhi.tinkersnewlife.content.curse.CurseVaultData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：查询某个呪蔵的存量（十字光标对准时用）。
 *
 * <p>咒力的真值在服务端的 world data 里，客户端拿不到；这里由客户端按需询问、服务端回
 * {@link PacketSyncCurseVault}，客户端缓存几百毫秒。比"方块实体每 20 tick 广播"更直接可靠。
 */
public class PacketQueryCurseVault {

    private final BlockPos pos;

    public PacketQueryCurseVault(BlockPos pos) {
        this.pos = pos;
    }

    public PacketQueryCurseVault(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    public static void handle(PacketQueryCurseVault packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            ServerLevel level = player.serverLevel();
            BlockPos pos = packet.pos;
            // 只回自己视野附近、且确实是呪蔵的坐标（防滥用）
            if (!level.isLoaded(pos)) return;
            if (!(level.getBlockState(pos).getBlock() instanceof CurseVaultBlock)) return;
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64 * 64) return;
            double power = CurseVaultData.get(level).getPower(pos);
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new PacketSyncCurseVault(pos, power));
        });
        ctx.get().setPacketHandled(true);
    }
}
