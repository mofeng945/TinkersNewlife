package com.mofengbaizhi.tinkersnewlife.network.tools;

import com.mofengbaizhi.tinkersnewlife.client.screen.QuantumVaultScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务端 → 客户端：把 6 级量子背包的<b>按数量存储快照</b>发给正在看这个界面的玩家 ✓。
 *
 * <p>为什么不用原版槽位同步：这里的存储**没有格子**（按物品数量存放 ✓），
 * 所以客户端拿到的是一份"物品类型 + 数量"的列表 ✓；界面自己做搜索与分页 ✓，
 * 玩家的每次操作再由 {@link PacketVaultAction} 发回服务端执行 ✓。
 */
public class PacketVaultSync {

    /** 一条记录：数量 1 的物品模板 + 数量（long，可能远大于 64 ✓） */
    public record Entry(ItemStack stack, long amount) {
    }

    private final int windowId;
    private final long total;
    private final List<Entry> entries;

    public PacketVaultSync(int windowId, long total, List<Entry> entries) {
        this.windowId = windowId;
        this.total = total;
        this.entries = entries;
    }

    public PacketVaultSync(FriendlyByteBuf buf) {
        this.windowId = buf.readInt();
        this.total = buf.readLong();
        int size = buf.readVarInt();
        List<Entry> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ItemStack stack = buf.readItem();
            long amount = buf.readLong();
            list.add(new Entry(stack, amount));
        }
        this.entries = list;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(windowId);
        buf.writeLong(total);
        buf.writeVarInt(entries.size());
        for (Entry e : entries) {
            buf.writeItem(e.stack());
            buf.writeLong(e.amount());
        }
    }

    public static void handle(PacketVaultSync packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof QuantumVaultScreen screen) {
                screen.acceptSync(packet.windowId, packet.total, packet.entries);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public int windowId() {
        return windowId;
    }

    public long total() {
        return total;
    }

    public List<Entry> entries() {
        return entries;
    }
}
