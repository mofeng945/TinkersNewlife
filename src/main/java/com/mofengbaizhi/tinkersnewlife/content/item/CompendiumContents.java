package com.mofengbaizhi.tinkersnewlife.content.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * <b>百宝书内容物的公开查询入口</b>（§910t，用户选的方案 B）——
 *
 * <p>用户口径：「**那就b**」✓ —— 即：**不**往「古旧书袋」里写 ✗（那条路会让玩家能在书袋界面里
 * 看见/取出 ✗），改成**自建一套公开查询接口** ✓，形状**对齐生态里那一份** ✓：
 * 诡厄遗物的 {@code EnigmaticAddonsBookBagHelper} 就是
 * {@code getAllItems(player)} / {@code getAllItemIds(player)} / {@code hasItem(player, …)} /
 * {@code collectItems(player, predicate)} 这四件套 ✓ ⇒ 我们按同样形状提供 ✓，
 * 别的 mod（或以后的我们）想"把百宝书当成书袋"看，调这里就行 ✓。
 *
 * <h3>语义（与用户口径一致 ✓）</h3>
 * <ul>
 *   <li><b>只读</b> ✓ —— 这里**没有任何写入/取出/回吐**的接口 ✗
 *       （吞进去的内容物永远只存在于百宝书自己的记录里 ✓ 取不出来 ✓）；</li>
 *   <li>返回的都是**新造的 ItemStack 副本** ✓ ⇒ 调用方改它**不会**影响百宝书 ✓；</li>
 *   <li>没有任何副作用 ✓ 客户端/服务端都能调 ✓（纯读玩家背包 ✓）。</li>
 * </ul>
 *
 * <p>⚠ 如实说明：这套接口**只能被"愿意来调"的 mod 用到** ✓ ——
 * 已经写死只查自己那套判定的 mod ✗ 不会自动看见百宝书 ✗（那种仍然靠 §910n/§910r 这类**逐条镜像** ✓）。
 * 换句话说：B 方案解决的是"**以后**要不要集成" ✓，不是"让存量 mod 自动认账" ✗。
 */
public final class CompendiumContents {

    private CompendiumContents() {}

    /** 玩家身上所有百宝书（背包 + 主副手 ✓ 只读 ✓） */
    public static List<ItemStack> getCompendiums(Player player) {
        List<ItemStack> out = new ArrayList<>();
        if (player == null) return out;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.getItem() instanceof CompendiumItem) out.add(s);
        }
        return out;
    }

    /** 玩家（所有百宝书里）已吞噬的全部内容物 ✓ 每件都是**新造的副本** ✓ */
    public static List<ItemStack> getAllItems(Player player) {
        return collectItems(player, stack -> true);
    }

    /** 已吞噬内容物的**物品 id** 列表 ✓（与生态那份同名同义 ✓） */
    public static List<ResourceLocation> getAllItemIds(Player player) {
        List<ResourceLocation> out = new ArrayList<>();
        for (ItemStack s : getAllItems(player)) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
            if (id != null) out.add(id);
        }
        return out;
    }

    /** 按条件筛已吞噬的内容物 ✓ */
    public static List<ItemStack> collectItems(Player player, Predicate<ItemStack> filter) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack compendium : getCompendiums(player)) {
            for (String id : CompendiumItem.absorbedItemIds(compendium)) {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl == null) continue;
                Item item = ForgeRegistries.ITEMS.getValue(rl);
                if (item == null) continue;
                ItemStack stack = new ItemStack(item);
                if (filter == null || filter.test(stack)) out.add(stack);
            }
        }
        return out;
    }

    /** 玩家身上有没有吞过某件物品 ✓（按物品 id ✓） */
    public static boolean hasItem(Player player, ResourceLocation itemId) {
        return itemId != null && hasItem(player, stack -> itemId.equals(ForgeRegistries.ITEMS.getKey(stack.getItem())));
    }

    /** 玩家身上有没有吞过满足条件的物品 ✓ */
    public static boolean hasItem(Player player, Predicate<ItemStack> filter) {
        return !collectItems(player, filter).isEmpty();
    }
}
