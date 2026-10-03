package com.mofengbaizhi.tinkersnewlife.network.compendium;

import com.mofengbaizhi.tinkersnewlife.content.item.CompendiumItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：<b>百宝书吞噬一本书</b>（§910，§910c 改成传"书 id"✓）。
 *
 * <h3>⚠ §910c 为什么不再传"槽位下标"</h3>
 * 实测日志（创造模式）：
 * <pre>
 * [百宝书] 已发包：槽位=49 来源=0
 * [百宝书] 收到吞噬请求：槽位=49 来源=0 菜单槽数=46
 * [百宝书] ✗ 槽位越界，放弃
 * </pre>
 * 界面是 {@code CreativeModeInventoryScreen} —— 创造模式那套**客户端菜单**多一个"垃圾桶"槽、
 * 下标整体与服务端的 {@code InventoryMenu} **对不上** ✗（而且创造模式放物品走的是
 * {@code ServerboundSetCreativeModeSlotPacket}，根本不使用菜单槽号 ✗）
 * ⇒ 拿客户端下标去服务端取槽位**必然会错** ✗。
 * <p>现在改成：客户端把**读到的书 id** 发过来 ✓，服务端在
 * <b>当前菜单 → 玩家背包</b> 里自己找"哪一格是这本书"再吞 ✓
 * ⇒ 创造/生存、原版界面/模组界面**都通** ✓，也不怕编号对不上 ✗。
 *
 * <p>服务端校验链（每一步失败都打一行日志 ✓）：
 * ① 找到百宝书（光标 / 主手 / 背包 ✓ 由 {@code source} 指定）；
 * ② 在菜单与背包里找到**书 id 匹配**的那一本；
 * ③ 才 {@code shrink(1)} ＋ 写 NBT ＋ actionbar 提示 ✓。
 */
public class PacketCompendiumAbsorb {

    private static final Logger LOG = LoggerFactory.getLogger("TinkersNewlife/Compendium");

    /** 百宝书在哪：0 = 光标拖着（carried）✓ 1 = 主手 ✓ 2 = 背包里 ✓ */
    public static final int SOURCE_CARRIED = 0;
    public static final int SOURCE_MAIN_HAND = 1;
    public static final int SOURCE_INVENTORY = 2;

    /** 被吞噬那本书的帕秋莉书 id（{@code patchouli:book} 的值 ✓ 由客户端读出 ✓ 服务端自行定位那本书 ✓） */
    private final String bookId;
    /** 百宝书的来源（见上面三个常量 ✓） */
    private final int source;

    public PacketCompendiumAbsorb(String bookId, int source) {
        this.bookId = bookId;
        this.source = source;
    }

    public PacketCompendiumAbsorb(FriendlyByteBuf buf) {
        this.bookId = buf.readUtf();
        this.source = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(this.bookId);
        buf.writeVarInt(this.source);
    }

    public static void handle(PacketCompendiumAbsorb packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            LOG.info("[百宝书] 收到吞噬请求：书id={} 来源={}（0光标/1主手/2背包）",
                    packet.bookId, packet.source);

            ItemStack compendium = switch (packet.source) {
                case SOURCE_CARRIED -> player.containerMenu.getCarried();
                case SOURCE_MAIN_HAND -> player.getMainHandItem();
                default -> CompendiumItem.findInInventory(player);
            };
            if (compendium == null || !(compendium.getItem() instanceof CompendiumItem)) {
                LOG.info("[百宝书] ✗ 来源 {} 上没找到百宝书（拿到的={}），放弃",
                        packet.source, compendium == null ? "null" : compendium.getDescriptionId());
                return;
            }

            // ② 在**当前菜单**里找这本书（箱子之类也覆盖 ✓），找不到再去玩家背包里找 ✓
            AbstractContainerMenu menu = player.containerMenu;
            ItemStack book = ItemStack.EMPTY;
            Slot foundSlot = null;
            for (Slot slot : menu.slots) {
                if (slot.hasItem() && packet.bookId.equals(CompendiumItem.bookIdOf(slot.getItem()))) {
                    book = slot.getItem();
                    foundSlot = slot;
                    break;
                }
            }
            if (book.isEmpty()) {
                var inv = player.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack s = inv.getItem(i);
                    if (!s.isEmpty() && packet.bookId.equals(CompendiumItem.bookIdOf(s))) {
                        book = s;
                        break;
                    }
                }
            }
            if (book.isEmpty()) {
                LOG.info("[百宝书] ✗ 菜单与背包里都没找到书 id={} 的那本书，放弃", packet.bookId);
                return;
            }

            // ③ 吞噬：记下「书 id + 显示名」✓ 扣掉这本书 ✓
            String displayName = book.getHoverName().getString();
            boolean first = CompendiumItem.absorb(compendium, packet.bookId, displayName);
            book.shrink(1);
            if (foundSlot != null) {
                foundSlot.setChanged();
            } else {
                player.getInventory().setChanged();
            }
            if (packet.source == SOURCE_CARRIED) menu.setCarried(compendium);
            menu.broadcastChanges();

            LOG.info("[百宝书] ✓ 已吞噬书 id={}（显示名={}）第一次={} 现在共 {} 本",
                    packet.bookId, displayName, first, CompendiumItem.absorbedCount(compendium));

            player.displayClientMessage(Component.translatable(first
                            ? "message.tinkersnewlife.compendium.absorbed"
                            : "message.tinkersnewlife.compendium.already",
                    displayName), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
