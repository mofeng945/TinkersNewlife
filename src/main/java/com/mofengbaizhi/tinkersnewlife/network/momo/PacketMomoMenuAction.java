package com.mofengbaizhi.tinkersnewlife.network.momo;

import com.mofengbaizhi.tinkersnewlife.content.entity.MomoMerchant;
import com.mofengbaizhi.tinkersnewlife.content.handler.MomoFavor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：墨默菜单的三个选项（用户口径 §455 A ✓）。
 *
 * <p>⚠ 为什么**不用**原版"容器按钮包"（`handleInventoryButtonClick` / `clickMenuButton`）✗：
 * 我们的 `MomoMenu` 是个**没有槽位**的容器菜单 ✓ 实机里那套按钮包没有生效（用户报"按钮全都点不动"✓）
 * ⇒ 改成**自己发这个包** ✓ 完全绕开容器按钮机制 ✓ 行为可预期 ✓。
 *
 * <p>action：**0 = 对话**（首次送「新生神秘学编年史」✓ 对话界面由客户端自己打开 ✓）
 * / **1 = 交易** / **2 = 雇佣**（两者都先关掉菜单再打开交易界面 ✓ 天数在交易界面的雇佣栏里选 ✓）
 * / **3 = 回退**（客户端自己关 ✓ 走不到这里 ✓）。
 */
public class PacketMomoMenuAction {

    private final int momoId;
    private final int action;

    public PacketMomoMenuAction(int momoId, int action) {
        this.momoId = momoId;
        this.action = action;
    }

    public PacketMomoMenuAction(FriendlyByteBuf buf) {
        this.momoId = buf.readInt();
        this.action = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(momoId);
        buf.writeVarInt(action);
    }

    public static void handle(PacketMomoMenuAction packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!(player.level().getEntity(packet.momoId) instanceof MomoMerchant momo)) {
                player.displayClientMessage(
                        Component.translatable("message.tinkersnewlife.momo.gone"), true);
                return;
            }
            switch (packet.action) {
                case 0 -> {
                    // 对话：首次送编年史 ✓（对话界面是纯客户端的 ✓ 文案静态 ✓ 不需要服务端参与 ✓）
                    if (MomoFavor.canTalk(player)
                            && !player.getPersistentData().getBoolean("tn_momo_chronicle_given")) {
                        player.getPersistentData().putBoolean("tn_momo_chronicle_given", true);
                        ItemStack book = new ItemStack(
                                com.mofengbaizhi.tinkersnewlife.content.ModItems.GUIDE_BOOK.get());
                        if (!player.getInventory().add(book)) player.drop(book, false);
                        player.displayClientMessage(
                                Component.translatable("menu.tinkersnewlife.momo.chronicle"), false);
                    }
                }
                case 1 -> {
                    // 交易：关掉我们的菜单 ✓ 再让他原有的交易界面登场 ✓
                    player.closeContainer();
                    PacketMomoOpen.sendTo(player, momo);
                }
                case 2 -> {
                    // 雇佣：同样先进交易界面（左边的雇佣栏就是雇佣入口 ✓ 天数在那里选 ✓）
                    player.closeContainer();
                    PacketMomoOpen.sendTo(player, momo);
                }
                default -> {
                    // 回退 / 未知：什么都不做
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
