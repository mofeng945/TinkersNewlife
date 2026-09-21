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
                        // ⭐ 帕秋莉的书物品靠 **NBT 里的 `patchouli:book`** 指向具体书
                        //    ⇒ 只给裸物品会显示「Book ID: null! / 无效的书：没有定义ID」（用户实测）
                        book.getOrCreateTag().putString("patchouli:book", "tinkersnewlife:guide");
                        if (!player.getInventory().add(book)) player.drop(book, false);
                        player.displayClientMessage(
                                Component.translatable("menu.tinkersnewlife.momo.chronicle"), false);
                    }
                }
                case 1 -> {
                    // 交易：关掉我们的菜单 ✓ 再让他原有的交易界面登场 ✓
                    player.closeContainer();
                    PacketMomoOpen.sendTo(player, momo);
                    momo.playTradeVoice();      // §501 打开交易界面 → 播那句"闲置"的交易语音 ✓
                }
                case 2 -> {
                    // 雇佣：打开**独立的雇佣界面**（用户口径：两个界面分开 ✓ 天数 + 五种等价物在那边 ✓）
                    player.closeContainer();
                    PacketMomoMenuOpen.sendHire(player, momo.getId());
                }
                case 4 -> {
                    // §497 **回菜单**（用户口径：三个子界面回退应当回上一级菜单，而不是直接关 GUI ✗）
                    // 由服务端重发菜单包 ⇒ 顺带把**最新好感**带上（交易/雇佣后好感会变 ✓ 本地那份是旧的 ✗）
                    PacketMomoMenuOpen.sendMenu(player, momo.getId());
                }
                default -> {
                    // 回退 / 未知：什么都不做
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
