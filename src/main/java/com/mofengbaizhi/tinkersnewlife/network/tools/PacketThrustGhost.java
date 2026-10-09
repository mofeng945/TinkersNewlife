package com.mofengbaizhi.tinkersnewlife.network.tools;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * ⭐ §1166 <b>突刺残影：服务端 → 客户端的"位置 ＋ 朝向"广播包</b>
 * （用户口径 ✓ 2026-10-09：
 * 「**服务端在突进过程中每2tick向周围能看到该实体的玩家广播一次当前的位置和朝向，
 * 然后客户端收到后存缓存（每个残影存在0.35秒），渲染时遍历该实体的残影列表、
 * 按存活进度计算alpha做淡出，并对每个残影用同一套模型再画一遍**」✓）。
 *
 * <h2>⭐ 载荷</h2>
 * <ul>
 *   <li>⭐ {@code entityId} —— ⭐ **是谁的残影** ✗（⭐ 客户端要靠它去 `level.getEntity(id)` 找到玩家实体 ✓
 *       ⭐ 因为残影要"用同一套模型再画一遍"⇒ ⭐ 必须能拿到那个玩家 ✓）；</li>
 *   <li>⭐ {@code x/y/z} —— ⭐ 广播那一刻的位置 ✓；</li>
 *   <li>⭐ {@code yaw/pitch} —— ⭐ 那一刻的朝向 ✓。</li>
 * </ul>
 *
 * <h2>⚠ 为什么用 {@code broadcastAndSend}（"能看到该实体"的语义 ✓）</h2>
 * ⭐ 用户要求"向**周围能看到该实体的玩家**广播"✗ ⇒ ⭐ 原版
 * {@code ServerChunkCache#broadcastAndSend(entity, packet)} ⭐ 的语义**正好就是**
 * ⭐ **"追踪该实体的玩家"** ✓（⭐ 即客户端已经加载了它 ✓ 否则收包也没用 ✗ ✓）
 * ⇒ ⭐ 不用自己算距离/视锥 ✓ 且不会发给看不见的人 ✓。
 *
 * <h2>⚠ 客户端侧必须过 {@code DistExecutor}（本仓 §801 铁律 ✓）</h2>
 * ⭐ 本类在**两端**都会被类加载 ✗ ⇒ ⚠ 直接引用客户端类会让**专服** `NoClassDefFoundError` ✗。
 */
public class PacketThrustGhost {

    private final int entityId;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public PacketThrustGhost(int entityId, double x, double y, double z, float yaw, float pitch) {
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public PacketThrustGhost(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.yaw = buf.readFloat();
        this.pitch = buf.readFloat();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
        buf.writeFloat(this.yaw);
        buf.writeFloat(this.pitch);
    }

    public static void handle(PacketThrustGhost packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                // ⚠ 绝不能在这里直接碰客户端类 ✗（§801）
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        com.mofengbaizhi.tinkersnewlife.client.renderer.ThrustGhostCache.add(
                                packet.entityId, packet.x, packet.y, packet.z, packet.yaw, packet.pitch)));
        ctx.get().setPacketHandled(true);
    }

    /**
     * ⭐ 服务端调用：⭐ 把"这一刻的残影"广播给**追踪该玩家的所有人** ✓
     * （⭐ 含他自己 ✗ —— ⭐ 第一人称看不到自己的模型 ✓ 但第三人称要看得到 ✓）。
     *
     * <p>⚠⚠ **不能用 `ServerChunkCache#broadcastAndSend`** ✗ —— ⭐ 它只收**原版 `Packet<?>`** ✗
     * （⭐ 实测编译报"`PacketThrustGhost` 无法转换为 `Packet<?>`" ✓）⇒ ⭐ 改用 Forge 的
     * ⭐ **`PacketDistributor.TRACKING_ENTITY`** ✓ —— ⭐ 它的语义**正好**就是"追踪该实体的玩家" ✓
     * （⭐ 即"能看到它的" ✓ ⭐ 与用户口径完全一致 ✓）。
     */
    public static void broadcast(net.minecraft.server.level.ServerLevel level,
                                 net.minecraft.world.entity.LivingEntity entity) {
        if (level == null || entity == null) {
            return;
        }
        try {
            TinkersNewlife.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> entity),
                    new PacketThrustGhost(entity.getId(), entity.getX(), entity.getY(), entity.getZ(),
                            entity.getYRot(), entity.getXRot()));
        } catch (Throwable ignored) {
            // ⭐ 发不出去只是少一个残影 ✓ 绝不能连累玩法 ✗
        }
    }

    /** ⭐ 仅供自检：本模组主通道 ✓（⭐ 漏用会发不出去 ✗） */
    @SuppressWarnings("unused")
    private static void selfCheck() {
        TinkersNewlife.class.getName();
    }
}
