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
 * 客户端 → 服务端：<b>百宝书吞噬一本书</b>（§910 / §910c / §910d）。
 *
 * <h3>踩过的两个坑（都有实测日志 ✓）</h3>
 * <ol>
 *   <li><b>§910c 别传槽位下标</b> ✗：创造模式 {@code CreativeModeInventoryScreen} 的客户端菜单
 *       与服务端 {@code InventoryMenu} **编号对不上**（日志：客户端槽 49 / 服务端只有 46 个槽 ✗）
 *       ⇒ 现在传**书 id**，服务端自己在"当前菜单 → 玩家背包"里找那本书 ✓。</li>
 *   <li><b>§910d 创造模式下"光标上那叠"服务端看不见</b> ✗：创造模式放/取走的是
 *       {@code ServerboundSetCreativeModeSlotPacket}（按背包绝对槽号直接写 ✓），
 *       **从不把光标内容同步给服务端** ⇒ 服务端 {@code menu.getCarried()} 永远是空气
 *       （日志：`来源 0 上没找到百宝书（拿到的=block.minecraft.air）` ✓）。
 *       ⇒ 光标上那叠改由**客户端本地登记** ✓（{@link #SOURCE_CLIENT_LOCAL} ✓），
 *       只请服务端"把那本书吃掉" ✓。</li>
 * </ol>
 *
 * <h3>来源（{@code source}）</h3>
 * <ul>
 *   <li>{@link #SOURCE_CARRIED}（0）：百宝书在光标上 —— **生存模式** ✓ 服务端看得见 ✓ 权威写入 ✓；</li>
 *   <li>{@link #SOURCE_MAIN_HAND}（1）：主手 ✓；</li>
 *   <li>{@link #SOURCE_INVENTORY}（2）：背包里 ✓；</li>
 *   <li>{@link #SOURCE_CLIENT_LOCAL}（3）：**创造模式光标** ✓ 客户端已自行登记 ⇒
 *       服务端**只消费那本书** ✓（找不到百宝书也不算错 ✓）。</li>
 * </ul>
 * <p>⚠ 服务端校验（源 0/1/2）：找到百宝书 ⇒ 找到那本书 ⇒ 才扣书 + 写 NBT + 提示 ✓；
 * 源 3：只要求"确实有那本书" ✓（就是删掉玩家自己一本书 ✓ 害不到别人 ✓）。
 */
public class PacketCompendiumAbsorb {

    private static final Logger LOG = LoggerFactory.getLogger("TinkersNewlife/Compendium");

    public static final int SOURCE_CARRIED = 0;
    public static final int SOURCE_MAIN_HAND = 1;
    public static final int SOURCE_INVENTORY = 2;
    /** §910d 创造模式光标：客户端已本地登记 ✓ 服务端只把书吃掉 ✓ */
    public static final int SOURCE_CLIENT_LOCAL = 3;

    /** 被吞噬那本书的帕秋莉书 id（{@code patchouli:book} 的值 ✓） */
    private final String bookId;
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

            LOG.info("[百宝书] 收到吞噬请求：书id={} 来源={}（0光标/1主手/2背包/3创造光标·客户端已登记）",
                    packet.bookId, packet.source);

            // ① 先找那本书（当前菜单 → 玩家背包 ✓ 覆盖箱子等容器 ✓）
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
                LOG.info("[百宝书] ✗ 菜单与背包里都没有书 id={} 的那本书，放弃", packet.bookId);
                return;
            }

            String displayName = book.getHoverName().getString();
            boolean first = true;
            int total = -1;   // 来源 3 时客户端自己记的，服务端这边不报总数 ✓

            if (packet.source == SOURCE_CLIENT_LOCAL) {
                // ③' 创造模式光标：客户端已经自己登记好了 ✓ 服务端只负责把书吃掉 ✓
                LOG.info("[百宝书] 来源 3（创造光标）⇒ 只消费这本书 ✓（登记已在客户端完成 ✓）");
            } else {
                // ② 找到百宝书（光标 / 主手 / 背包 ✓）
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
                first = CompendiumItem.absorb(compendium, packet.bookId, displayName);
                total = CompendiumItem.absorbedCount(compendium);
                if (packet.source == SOURCE_CARRIED) menu.setCarried(compendium);
            }

            // ③ 扣掉这本书 ✓
            book.shrink(1);
            if (foundSlot != null) {
                foundSlot.setChanged();
            } else {
                player.getInventory().setChanged();
            }
            menu.broadcastChanges();

            LOG.info("[百宝书] ✓ 已吞噬书 id={}（显示名={}）第一次={} 服务端侧总数={}",
                    packet.bookId, displayName, first, total);

            player.displayClientMessage(Component.translatable(first
                            ? "message.tinkersnewlife.compendium.absorbed"
                            : "message.tinkersnewlife.compendium.already",
                    displayName), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
