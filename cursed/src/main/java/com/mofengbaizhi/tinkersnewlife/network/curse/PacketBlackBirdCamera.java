package com.mofengbaizhi.tinkersnewlife.network.curse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端→客户端：黑鸟操术 视角切换
 * 携带目标实体 id；mode=true 绑定相机到蝙蝠（玩家本体留在原地），mode=false 恢复玩家视角。
 */
public class PacketBlackBirdCamera {

    private final int entityId;
    private final boolean attach;

    public PacketBlackBirdCamera(int entityId, boolean attach) {
        this.entityId = entityId;
        this.attach = attach;
    }

    public PacketBlackBirdCamera(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.attach = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeBoolean(attach);
    }

    public static void handle(PacketBlackBirdCamera packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            Minecraft mc = Minecraft.getInstance();
            if (packet.attach) {
                ClientLevel level = mc.level;
                if (level != null) {
                    Entity target = level.getEntity(packet.entityId);
                    if (target != null) {
                        mc.setCameraEntity(target);
                    }
                }
            } else {                // 恢复玩家视角
                if (mc.player != null) {
                    mc.setCameraEntity(mc.player);
                }
            }
        }));
        ctx.get().setPacketHandled(true);
    }
}
