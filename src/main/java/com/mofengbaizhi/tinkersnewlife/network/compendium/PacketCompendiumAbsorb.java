package com.mofengbaizhi.tinkersnewlife.network.compendium;

import com.mofengbaizhi.tinkersnewlife.content.item.CompendiumItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：<b>百宝书吞噬一本书</b>（§910）✓。
 *
 * <p>流程：客户端在物品栏里检测到"手里拖着/手持百宝书 + 右键一本帕秋莉书"就发这个包 ✓
 * （见 {@code client/handler/CompendiumClient} ✓）⇒ 服务端**再校验一遍**才动手 ✓：
 * <ol>
 *   <li>槽位下标必须在当前打开的菜单范围内 ✓（防伪造越界 ✗）；</li>
 *   <li>那格确实是**帕秋莉书**（NBT {@code patchouli:book} ✓，本仓既有口径 ✓）；</li>
 *   <li>百宝书确实在玩家手里（拖着的 carried ✓ 或主手 ✓）。</li>
 * </ol>
 * 三条都过才：把书 {@code shrink(1)} ✓ 把 `书 id + 显示名` 写进百宝书 NBT ✓ 并发一条 actionbar 提示 ✓。
 * <p>⚠ 客户端那侧只是"拦下原版的放置动作" ✓，**真正的改动一律在服务端做** ✓（不信任客户端 ✓）。
 */
public class PacketCompendiumAbsorb {

    /** 被吞噬的那本书所在的槽位下标（当前打开的菜单里 ✓） */
    private final int slotIndex;
    /** 百宝书是"手拖着"（true）还是"握在主手"（false）✓ */
    private final boolean fromCarried;

    public PacketCompendiumAbsorb(int slotIndex, boolean fromCarried) {
        this.slotIndex = slotIndex;
        this.fromCarried = fromCarried;
    }

    public PacketCompendiumAbsorb(FriendlyByteBuf buf) {
        this.slotIndex = buf.readVarInt();
        this.fromCarried = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(this.slotIndex);
        buf.writeBoolean(this.fromCarried);
    }

    public static void handle(PacketCompendiumAbsorb packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            AbstractContainerMenu menu = player.containerMenu;
            if (packet.slotIndex < 0 || packet.slotIndex >= menu.slots.size()) return;
            Slot slot = menu.getSlot(packet.slotIndex);
            if (!slot.hasItem()) return;

            ItemStack book = slot.getItem();
            String bookId = CompendiumItem.bookIdOf(book);
            if (bookId == null) return;                                   // 不是帕秋莉书 ⇒ 什么都不做 ✓

            ItemStack compendium = packet.fromCarried ? menu.getCarried() : player.getMainHandItem();
            if (!(compendium.getItem() instanceof CompendiumItem)) return;

            // 吞噬：记下"书 id + 显示名"✓ 扣掉这本书 ✓
            String displayName = book.getHoverName().getString();
            boolean first = CompendiumItem.absorb(compendium, bookId, displayName);
            book.shrink(1);
            slot.setChanged();
            if (packet.fromCarried) menu.setCarried(compendium);
            menu.broadcastChanges();

            player.displayClientMessage(Component.translatable(first
                            ? "message.tinkersnewlife.compendium.absorbed"
                            : "message.tinkersnewlife.compendium.already",
                    displayName), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
