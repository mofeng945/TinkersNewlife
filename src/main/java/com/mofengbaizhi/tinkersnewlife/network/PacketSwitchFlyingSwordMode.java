package com.mofengbaizhi.tinkersnewlife.network;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.FlyingSwordItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import slimeknights.tconstruct.library.tools.nbt.ToolDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.function.Supplier;

public class PacketSwitchFlyingSwordMode {

    private final int hand;

    public PacketSwitchFlyingSwordMode(int hand) {
        this.hand = hand;
    }

    public PacketSwitchFlyingSwordMode(FriendlyByteBuf buf) {
        this.hand = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(hand);
    }

    public static void handle(PacketSwitchFlyingSwordMode packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // ⭐ 校验 hand 参数（0=主手，1=副手），防止恶意客户端传其他值
            ItemStack stack = packet.hand == 0 ? player.getMainHandItem()
                    : packet.hand == 1 ? player.getOffhandItem()
                    : ItemStack.EMPTY;
            if (!(stack.getItem() instanceof FlyingSwordItem)) return;

            ToolStack tool = ToolStack.from(stack);
            if (tool == null) return;

            ToolDataNBT persistentData = tool.getPersistentData();
            int currentMode = persistentData.getInt(FlyingSwordItem.MODE_KEY);
            int newMode = currentMode == 0 ? 1 : 0;
            persistentData.putInt(FlyingSwordItem.MODE_KEY, newMode);
            tool.updateStack(stack);

            String modeKey = newMode == 0 ?
                    "message.tinkersnewlife.flying_sword.mode.normal" :
                    "message.tinkersnewlife.flying_sword.mode.chase";
            player.displayClientMessage(Component.translatable(modeKey), true);
        });
        ctx.get().setPacketHandled(true);
    }
}