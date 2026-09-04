package com.mofengbaizhi.tinkersnewlife.network.curse;

import com.mofengbaizhi.tinkersnewlife.content.curse.technique.PuppetTechnique;
import com.mofengbaizhi.tinkersnewlife.content.entity.PuppetGolemMob;
import com.mofengbaizhi.tinkersnewlife.content.entity.PuppetIronGolem;
import com.mofengbaizhi.tinkersnewlife.content.entity.PuppetSnowGolem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端→服务端：傀儡操术 操控输入（每 tick 相机绑定傀儡时发送）。
 * 携带：前进/侧移（zza/xxa）、跳跃、Shift（自爆）、左键攻击、右键技能、玩家视角（傀儡朝向/弹道用）。
 */
public class PacketPuppetInput {

    private final float zza;
    private final float xxa;
    private final boolean jumping;
    private final boolean shift;
    private final boolean left;
    private final boolean right;
    private final float yRot;
    private final float xRot;

    public PacketPuppetInput(float zza, float xxa, boolean jumping, boolean shift,
                             boolean left, boolean right, float yRot, float xRot) {
        this.zza = zza;
        this.xxa = xxa;
        this.jumping = jumping;
        this.shift = shift;
        this.left = left;
        this.right = right;
        this.yRot = yRot;
        this.xRot = xRot;
    }

    public PacketPuppetInput(FriendlyByteBuf buf) {
        this.zza = buf.readFloat();
        this.xxa = buf.readFloat();
        this.jumping = buf.readBoolean();
        this.shift = buf.readBoolean();
        this.left = buf.readBoolean();
        this.right = buf.readBoolean();
        this.yRot = buf.readFloat();
        this.xRot = buf.readFloat();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeFloat(zza);
        buf.writeFloat(xxa);
        buf.writeBoolean(jumping);
        buf.writeBoolean(shift);
        buf.writeBoolean(left);
        buf.writeBoolean(right);
        buf.writeFloat(yRot);
        buf.writeFloat(xRot);
    }

    public static void handle(PacketPuppetInput packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            PuppetGolemMob puppet = PuppetTechnique.findActivePuppet(player);
            if (puppet == null) return;
            if (puppet instanceof PuppetIronGolem iron) {
                iron.puppetSetInput(packet.zza, packet.xxa, packet.jumping, packet.shift,
                        packet.left, packet.right, packet.yRot, packet.xRot);
            } else if (puppet instanceof PuppetSnowGolem snow) {
                snow.puppetSetInput(packet.zza, packet.xxa, packet.jumping, packet.shift,
                        packet.left, packet.right, packet.yRot, packet.xRot);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
