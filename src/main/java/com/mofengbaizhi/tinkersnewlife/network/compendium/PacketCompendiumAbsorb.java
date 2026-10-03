package com.mofengbaizhi.tinkersnewlife.network.compendium;

import com.mofengbaizhi.tinkersnewlife.content.item.CompendiumItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：<b>百宝书吞噬一本书</b>（§910）✓。
 *
 * <p>流程：客户端在物品栏里检测到"手上有百宝书 + 右键一本帕秋莉书"就发这个包 ✓
 * （见 {@code client/handler/CompendiumClient} ✓）⇒ 服务端**再校验一遍**才动手 ✓：
 * <ol>
 *   <li>槽位下标必须在当前打开的菜单范围内 ✓（防伪造越界 ✗）；</li>
 *   <li>那格确实是**帕秋莉书**（NBT {@code patchouli:book} ✓，本仓既有口径 ✓）；</li>
 *   <li>百宝书确实在玩家身上（光标拖着 / 主手 / 背包里 ✓ 三者之一 ✓）。</li>
 * </ol>
 * 三条都过才：把书 {@code shrink(1)} ✓ 把「书 id + 显示名」写进百宝书 NBT ✓ 并发一条 actionbar 提示 ✓。
 *
 * <p>⚠ 客户端那侧只是"拦下原版的放置动作" ✓，**真正的改动一律在服务端做** ✓（不信任客户端 ✓）。
 * <p>⚠ §910b 起每一处判定失败都打一行日志 ✓ —— 上一版"右键没反应"时**完全没有线索** ✗，
 * 这次把判定链摊开，日志里直接能看出卡在哪一步 ✓。
 */
public class PacketCompendiumAbsorb {

    private static final Logger LOG = LoggerFactory.getLogger("TinkersNewlife/Compendium");

    /** 百宝书在哪：0 = 光标拖着（carried）✓ 1 = 主手 ✓ 2 = 背包里 ✓ */
    public static final int SOURCE_CARRIED = 0;
    public static final int SOURCE_MAIN_HAND = 1;
    public static final int SOURCE_INVENTORY = 2;

    /** 被吞噬的那本书所在的槽位下标（当前打开的菜单里 ✓） */
    private final int slotIndex;
    /** 百宝书的来源（见上面三个常量 ✓） */
    private final int source;

    public PacketCompendiumAbsorb(int slotIndex, int source) {
        this.slotIndex = slotIndex;
        this.source = source;
    }

    public PacketCompendiumAbsorb(FriendlyByteBuf buf) {
        this.slotIndex = buf.readVarInt();
        this.source = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(this.slotIndex);
        buf.writeVarInt(this.source);
    }

    public static void handle(PacketCompendiumAbsorb packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            AbstractContainerMenu menu = player.containerMenu;
            LOG.info("[百宝书] 收到吞噬请求：槽位={} 来源={}（0光标/1主手/2背包）菜单槽数={}",
                    packet.slotIndex, packet.source, menu.slots.size());
            if (packet.slotIndex < 0 || packet.slotIndex >= menu.slots.size()) {
                LOG.info("[百宝书] ✗ 槽位越界，放弃");
                return;
            }
            Slot slot = menu.getSlot(packet.slotIndex);
            if (!slot.hasItem()) {
                LOG.info("[百宝书] ✗ 槽位是空的，放弃");
                return;
            }

            ItemStack book = slot.getItem();
            String bookId = CompendiumItem.bookIdOf(book);
            if (bookId == null) {
                LOG.info("[百宝书] ✗ 槽内 {} 没有 patchouli:book 这个 NBT，不算帕秋莉书，放弃",
                        book.getDescriptionId());
                return;
            }

            ItemStack compendium = switch (packet.source) {
                case SOURCE_CARRIED -> menu.getCarried();
                case SOURCE_MAIN_HAND -> player.getMainHandItem();
                default -> CompendiumItem.findInInventory(player);
            };
            if (compendium == null || !(compendium.getItem() instanceof CompendiumItem)) {
                LOG.info("[百宝书] ✗ 来源 {} 上没找到百宝书（拿到的={}），放弃",
                        packet.source, compendium == null ? "null" : compendium.getDescriptionId());
                return;
            }

            // 吞噬：记下「书 id + 显示名」✓ 扣掉这本书 ✓
            String displayName = book.getHoverName().getString();
            boolean first = CompendiumItem.absorb(compendium, bookId, displayName);
            book.shrink(1);
            slot.setChanged();
            if (packet.source == SOURCE_CARRIED) menu.setCarried(compendium);
            menu.broadcastChanges();

            LOG.info("[百宝书] ✓ 已吞噬书 id={}（显示名={}）第一次={} 现在共 {} 本",
                    bookId, displayName, first, CompendiumItem.absorbedCount(compendium));

            player.displayClientMessage(Component.translatable(first
                            ? "message.tinkersnewlife.compendium.absorbed"
                            : "message.tinkersnewlife.compendium.already",
                    displayName), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
