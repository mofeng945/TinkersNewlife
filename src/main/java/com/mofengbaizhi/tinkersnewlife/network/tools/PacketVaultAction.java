package com.mofengbaizhi.tinkersnewlife.network.tools;

import com.mofengbaizhi.tinkersnewlife.content.modifier.QuantumBagModifier;
import com.mofengbaizhi.tinkersnewlife.content.storage.QuantumVault;
import com.mofengbaizhi.tinkersnewlife.content.storage.QuantumVaultManager;
import com.mofengbaizhi.tinkersnewlife.network.VaultNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 客户端 → 服务端：6 级量子背包界面里的一次操作 ✓。
 *
 * <p>动作都在<b>服务端</b>执行（客户端只负责显示快照 ✓），做完立刻回传一次
 * {@link PacketVaultSync} 让界面刷新 ✓ —— 这样客户端不可能凭空造物品 ✓。
 */
public class PacketVaultAction {

    public static final byte WITHDRAW_ONE = 0;      // 左键：取 1 个
    public static final byte WITHDRAW_STACK = 1;    // 右键：取 64 个
    public static final byte WITHDRAW_ALL = 2;      // Shift+左键：取光这种
    public static final byte DEPOSIT_ALL = 3;       // 「存入全部」按钮：把玩家背包能存的都存进去
    public static final byte DEPOSIT_CARRIED = 5;   // 把光标上拿着的物品存进去（左键拖入 ✓）  // 对某个类型：把背包里同类型物品存进去（未使用，留作扩展）

    private final UUID uuid;
    private final byte action;
    private final ItemStack template;
    private final int amount;

    public PacketVaultAction(UUID uuid, byte action, ItemStack template, int amount) {
        this.uuid = uuid;
        this.action = action;
        this.template = template == null ? ItemStack.EMPTY : template;
        this.amount = amount;
    }

    public PacketVaultAction(FriendlyByteBuf buf) {
        this.uuid = buf.readUUID();
        this.action = buf.readByte();
        this.template = buf.readItem();
        this.amount = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(uuid);
        buf.writeByte(action);
        buf.writeItem(template);
        buf.writeVarInt(amount);
    }

    public static void handle(PacketVaultAction packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // ⭐ 防伪校验：优先认"**正开着的界面**就是这只背包"（这样背包本体被存进存储里时仍能取出 ✓），
            //    否则退回"手上/副手拿着它"✓ —— 两种情况之外的请求一律拒绝 ✓。
            boolean authorized = player.containerMenu
                    instanceof com.mofengbaizhi.tinkersnewlife.content.storage.QuantumVaultMenu menu
                    && packet.uuid.equals(menu.getBagUUID());
            if (!authorized) {
                UUID real = QuantumBagModifier.getBagUUID(player.getMainHandItem());
                if (real == null) real = QuantumBagModifier.getBagUUID(player.getOffhandItem());
                authorized = real != null && real.equals(packet.uuid)
                        && (QuantumBagModifier.getBagLevel(player.getMainHandItem()) >= 6
                            || QuantumBagModifier.getBagLevel(player.getOffhandItem()) >= 6);
            }
            if (!authorized) return;

            QuantumVault vault = QuantumVaultManager.getInstance().getOrCreate(packet.uuid);
            switch (packet.action) {
                case WITHDRAW_ONE -> give(player, vault.extract(packet.template, 1));
                // 右键"取一组"= 取**这个物品自己的一整叠**（桶这种 maxStack=1 就只取 1 ✓）
                case WITHDRAW_ALL -> give(player, vault.extractAll(packet.template));
                case DEPOSIT_ALL -> depositInventory(player, vault);
                default -> {
                }
            }
            QuantumVaultManager.getInstance().markDirty(packet.uuid);
            VaultNetwork.sync(player, packet.uuid, player.containerMenu.containerId);
        });
        ctx.get().setPacketHandled(true);
    }

    /** 取出：优先塞进背包，塞不下的掉在脚下 ✓。
     *  ⚠ 必须**按物品自身的堆叠上限拆开** ✗ —— 第一版直接把 64 个（或整叠）塞给
     *  {@code Inventory#add}，桶这种 maxStack=1 的就被塞成"一叠 2 个" ✗（用户实测反馈 ✓）。 */
    private static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        int max = Math.max(1, stack.getMaxStackSize());
        while (!stack.isEmpty()) {
            int take = Math.min(stack.getCount(), max);
            ItemStack part = stack.copy();
            part.setCount(take);
            if (!player.getInventory().add(part)) {
                player.drop(part, false);
            }
            stack.shrink(take);
        }
    }

    /** 存入"光标上拿着的那一份"（左键把物品拖进界面 ✓） */
    private static void depositCarried(ServerPlayer player, QuantumVault vault) {
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty()) return;
        if (QuantumBagModifier.getBagLevel(carried) > 0) return;          // 背包本体不许存 ✗
        int inserted = vault.insert(carried);
        if (inserted <= 0) return;
        carried.shrink(inserted);
        player.containerMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        player.containerMenu.broadcastChanges();
    }

    /** 存入全部：背包（含快捷栏）里能存的全存 ✓ */
    private static void depositInventory(ServerPlayer player, QuantumVault vault) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            // ⚠ 绝不把**量子背包本体**存进去 ✗ —— 第一版就是这么"自噬"的：
            //   背包进了存储、而取出又要校验手持 → 直接锁死 ✓
            if (QuantumBagModifier.getBagLevel(stack) > 0) continue;
            int inserted = vault.insert(stack);
            if (inserted > 0) {
                stack.shrink(inserted);
                if (stack.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
            }
        }
        inv.setChanged();
    }
}
