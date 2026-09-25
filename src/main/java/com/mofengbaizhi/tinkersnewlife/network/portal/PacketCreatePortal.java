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
 * <b>客户端 → 服务端</b>：GUI 确认后请求开门（§660）。
 *
 * <p>服务端<s>不信任</s>客户端，逐项复核：
 * <ol>
 *   <li>发件人此刻确实<b>站在伟大白色空间里</b>；</li>
 *   <li>锚点（右键的那个方块）离他<b>不超过 8 格</b>（{@code MAX_REACH_SQR}）⇒ 不能隔空开门；</li>
 *   <li>他手里<b>真的还握着</b>维度通行证（主手或副手）⇒ 开了 GUI 之后把通行证丢掉的刷子无效；</li>
 *   <li>目标维度 id 能解析、且该维度此刻确实加载着；坐标落在该维度的建筑高度内
 *       （后两项在 {@link WhiteSpaceDimensions#linkFromWhiteSpace} 里校验）。</li>
 * </ol>
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

            ServerLevel whiteSpace = player.serverLevel();
            if (!WhiteSpaceDimensions.isWhiteSpace(whiteSpace)) return;

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

            ResourceLocation id = ResourceLocation.tryParse(packet.dimensionId);
            if (id == null) return;
            ResourceKey<Level> destination = ResourceKey.create(Registries.DIMENSION, id);

            WhiteSpaceDimensions.PortalResult result = WhiteSpaceDimensions.linkFromWhiteSpace(
                    whiteSpace, packet.anchor, destination, new BlockPos(packet.x, packet.y, packet.z));
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
