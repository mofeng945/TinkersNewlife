package com.mofengbaizhi.tinkersnewlife.network.portal;

import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import com.mofengbaizhi.tinkersnewlife.content.item.DimensionPassItem;
import com.mofengbaizhi.tinkersnewlife.content.portal.WhiteSpaceDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * <b>客户端 → 服务端</b>：GUI 确认后请求开门（§660／§661）。
 *
 * <h2>模式由服务端判，不看客户端脸色</h2>
 * 包体里虽然带着维度 id 与 y，但服务端只按<b>发件人此刻站在哪个维度</b>决定用哪个模式：
 * <ul>
 *   <li>站在伟大白色空间 ⇒ <b>自由模式</b>：用包里的维度 id ＋ x／y／z（并再验一次黑名单）；</li>
 *   <li>站在别处（且非黑名单）⇒ <b>锁定模式</b>：<b>只取 x／z</b>，维度强制为伟大白色空间、
 *       y 强制为 {@link WhiteSpaceDimensions#GROUND_Y} ⇒ 客户端把 y 填到天上也没用 ✓。</li>
 * </ul>
 *
 * <p>另外逐项复核：锚点离玩家 ≤ 8 格（{@code MAX_REACH_SQR}，防隔空开门）、
 * 手里<b>真的还握着</b>维度通行证（开了 GUI 之后丢掉的刷子无效）。
 * 全部通过才真正开门并<b>消耗一张通行证</b> ✓。
 */
public class PacketCreatePortal {

    private final BlockPos anchor;
    private final String dimensionId;
    private final int x;
    private final int y;
    private final int z;

    public PacketCreatePortal(BlockPos anchor, String dimensionId, int x, int y, int z) {
        this.anchor = anchor.immutable();
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public PacketCreatePortal(FriendlyByteBuf buf) {
        this.anchor = buf.readBlockPos();
        this.dimensionId = buf.readUtf();
        this.x = buf.readVarInt();
        this.y = buf.readVarInt();
        this.z = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(anchor);
        buf.writeUtf(dimensionId);
        buf.writeVarInt(x);
        buf.writeVarInt(y);
        buf.writeVarInt(z);
    }

    public static void handle(PacketCreatePortal packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ServerLevel from = player.serverLevel();
            if (WhiteSpaceDimensions.isBlacklisted(from.dimension())) {
                player.displayClientMessage(Component.translatable(
                        "message.tinkersnewlife.white_space.blacklisted",
                        WhiteSpaceDimensions.displayName(from.dimension())), true);
                return;
            }

            if (player.position().distanceToSqr(Vec3.atCenterOf(packet.anchor))
                    > WhiteSpaceDimensions.MAX_REACH_SQR) {
                player.displayClientMessage(
                        Component.translatable("message.tinkersnewlife.white_space.too_far"), true);
                return;
            }

            ItemStack pass = passInHand(player);
            if (pass.isEmpty()) {
                player.displayClientMessage(
                        Component.translatable("message.tinkersnewlife.white_space.need_pass"), true);
                return;
            }

            WhiteSpaceDimensions.PortalResult result;
            if (WhiteSpaceDimensions.isWhiteSpace(from)) {
                // 自由模式：选哪个维度就开去哪（黑名单由 linkFromWhiteSpace 再拦一道）
                ResourceLocation id = ResourceLocation.tryParse(packet.dimensionId);
                if (id == null) return;
                ResourceKey<Level> destination = ResourceKey.create(Registries.DIMENSION, id);
                result = WhiteSpaceDimensions.linkFromWhiteSpace(
                        from, packet.anchor, destination, new BlockPos(packet.x, packet.y, packet.z));
            } else {
                // 锁定模式：只信 x/z；维度固定伟大白色空间、y 固定地面表层
                result = WhiteSpaceDimensions.linkFromOutside(from, packet.anchor, packet.x, packet.z);
            }

            player.displayClientMessage(result.message(), true);
            if (result.ok()) {
                WhiteSpaceDimensions.armCooldown(player, WhiteSpaceDimensions.CREATE_COOLDOWN);
                DimensionPassItem.consume(pass, player);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    /** 主手优先，其次副手；都没有就空 */
    private static ItemStack passInHand(ServerPlayer player) {
        if (player.getMainHandItem().is(ModItems.DIMENSION_PASS.get())) return player.getMainHandItem();
        if (player.getOffhandItem().is(ModItems.DIMENSION_PASS.get())) return player.getOffhandItem();
        return ItemStack.EMPTY;
    }
}
