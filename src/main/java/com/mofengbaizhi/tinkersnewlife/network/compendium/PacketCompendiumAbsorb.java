package com.mofengbaizhi.tinkersnewlife.network.compendium;

import com.mofengbaizhi.tinkersnewlife.content.item.CompendiumItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * 客户端 → 服务端：<b>百宝书吞噬一本书</b>（§910 / §910c / §910d / §910e）。
 *
 * <h3>三处坑（全是实测日志逼出来的 ✓）</h3>
 * <ol>
 *   <li><b>§910c 不能传槽位下标</b> ✗：创造模式客户端菜单与服务端 {@code InventoryMenu}
 *       **编号对不上**（日志：客户端槽 49 / 服务端 46 个槽 ✗）⇒ 改传**书 id**；</li>
 *   <li><b>§910d 创造模式"光标那叠"服务端看不见</b> ✗：创造模式放/取走的是
 *       {@code ServerboundSetCreativeModeSlotPacket}（按背包绝对槽号直接写 ✓），
 *       **从不同步光标内容** ⇒ 服务端读到的 carried 永远是空气 ✗
 *       ⇒ 创造 + 光标改为**客户端本地登记**（{@link #SOURCE_CLIENT_LOCAL} ✓）＋ 服务端只消费书 ✓；</li>
 *   <li><b>§910e 不能再靠 {@code patchouli:book} 找那本书</b> ✗：有些帕秋莉书
 *       （例：神秘遗物「启示之证」= {@code ItemBase} ＋ **私有 BOOK_ID** ✓）
 *       物品上**根本没有这个 NBT** ✗ ⇒ 服务端改成按**物品 id** 匹配那本书 ✓
 *       （书 id 由客户端解析好带过来 ✓ 见 {@code CompendiumClient#resolveBookId} ✓）。</li>
 * </ol>
 *
 * <h3>来源（{@code source}）</h3>
 * 0 = 光标（生存 ✓ 服务端权威写入）；1 = 主手；2 = 背包；
 * 3 = {@link #SOURCE_CLIENT_LOCAL} 创造模式光标（客户端已本地登记 ⇒ 服务端只消费那本书 ✓）。
 */
public class PacketCompendiumAbsorb {

    private static final Logger LOG = LoggerFactory.getLogger("TinkersNewlife/Compendium");

    public static final int SOURCE_CARRIED = 0;
    public static final int SOURCE_MAIN_HAND = 1;
    public static final int SOURCE_INVENTORY = 2;
    /** §910d 创造模式光标：客户端已本地登记 ✓ 服务端只把书吃掉 ✓ */
    public static final int SOURCE_CLIENT_LOCAL = 3;

    /** 那本书的帕秋莉书 id（客户端解析 ✓ 存进百宝书 NBT ✓ 也是以后开界面的钥匙 ✓） */
    private final String bookId;
    /** 那本书的**物品 id**（服务端凭它定位"要吃掉哪一格" ✓ §910e ✓） */
    private final String itemId;
    private final int source;

    public PacketCompendiumAbsorb(String bookId, String itemId, int source) {
        this.bookId = bookId;
        this.itemId = itemId;
        this.source = source;
    }

    public PacketCompendiumAbsorb(FriendlyByteBuf buf) {
        this.bookId = buf.readUtf();
        this.itemId = buf.readUtf();
        this.source = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(this.bookId);
        buf.writeUtf(this.itemId);
        buf.writeVarInt(this.source);
    }

    private static boolean matchesItem(ItemStack stack, @Nullable ResourceLocation want) {
        return want != null && want.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    public static void handle(PacketCompendiumAbsorb packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            LOG.info("[百宝书] 收到吞噬请求：书id={} 物品={} 来源={}（0光标/1主手/2背包/3创造光标·客户端已登记）",
                    packet.bookId, packet.itemId, packet.source);

            // ① 先找那本书（当前菜单 → 玩家背包 ✓ §910e 按物品 id 找 ✓ 覆盖没有 NBT 的帕秋莉书 ✓）
            ResourceLocation wantItem = ResourceLocation.tryParse(packet.itemId);
            AbstractContainerMenu menu = player.containerMenu;
            ItemStack book = ItemStack.EMPTY;
            Slot foundSlot = null;
            for (Slot slot : menu.slots) {
                if (slot.hasItem() && matchesItem(slot.getItem(), wantItem)) {
                    book = slot.getItem();
                    foundSlot = slot;
                    break;
                }
            }
            if (book.isEmpty()) {
                var inv = player.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack s = inv.getItem(i);
                    if (!s.isEmpty() && matchesItem(s, wantItem)) {
                        book = s;
                        break;
                    }
                }
            }
            if (book.isEmpty()) {
                LOG.info("[百宝书] ✗ 菜单与背包里都没有物品 {} 的那本书，放弃", packet.itemId);
                return;
            }

            // §910k 七咒门禁：带七咒限制的书（ICursed）只有"承受七咒之人"才吞得下 ✓
            //   条件与原文见 CompendiumItem#canAbsorb ✓（反射查 ✓ 没装神秘遗物时自动不拦 ✓）
            if (!CompendiumItem.canAbsorb(player, book)) {
                LOG.info("[百宝书] ✗ {} 带七咒限制，而玩家不是受咒者 ⇒ 拒绝吞噬", packet.itemId);
                player.displayClientMessage(
                        Component.translatable("message.tinkersnewlife.compendium.cursed_only"), true);
                return;
            }

            String displayName = book.getHoverName().getString();
            boolean first = true;
            int total = -1;   // 来源 3 时由客户端自己记，服务端不报总数 ✓

            if (packet.source == SOURCE_CLIENT_LOCAL) {
                // 创造模式光标：客户端已经自己登记好了 ✓ 服务端只负责把书吃掉 ✓
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
                first = CompendiumItem.absorb(compendium, packet.bookId, displayName, packet.itemId);
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

            LOG.info("[百宝书] ✓ 已吞噬书 id={}（物品={} 显示名={}）第一次={} 服务端侧总数={}",
                    packet.bookId, packet.itemId, displayName, first, total);

            player.displayClientMessage(Component.translatable(first
                            ? "message.tinkersnewlife.compendium.absorbed"
                            : "message.tinkersnewlife.compendium.already",
                    displayName), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
