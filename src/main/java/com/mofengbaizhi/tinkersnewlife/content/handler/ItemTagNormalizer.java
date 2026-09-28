package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * <b>物品空标签归一化</b>（§763）—— 把**没有 NBT 的物品**统一补成一个**空 CompoundTag `{}`** ✓
 * （用户口径：「通用修复，统一为 `{}`」✓）
 *
 * <h2>要解决什么</h2>
 * 「同款物品却叠不到一起」✓ —— 存档实证（§762 ✓）：
 * <pre>
 * goety:cursed_cage  槽12 x4   NBT = {}        ← 有一个空标签
 * goety:cursed_cage  槽13 x1   NBT = (无 tag)  ← 完全没有标签
 * </pre>
 * 原版 {@code ItemStack.isSameItemSameTags} 比 NBT 用的是 {@code Objects.equals} ✓
 * ⇒ <b>{@code {}} 与 {@code null} 不相等</b> ✗ ⇒ 两叠永远合不到一起 ✗（拖到一起也不行 ✗）。
 * 而 AE2 的键是「物品 ＋ {@code getTag()} ＋ 能力序列化」✓（`AEItemKey` 字节码实核 ✓）
 * ⇒ 同一件物品在 AE2 里也必然**分成两格存** ✓，取出来还是原样 ✓ —— 所以"背包里也叠不上" ✓。
 *
 * <p>根因举例：诡厄的 {@code CursedCageBlock#setPlacedBy} 用 {@code ItemStack#getOrCreateTag()} 去**读**
 * {@code BlockEntityTag} ✗ —— 这个 API 的语义是"没有就建一个空的"✗ ⇒ 走过这条路就留下 `{}` ✓，
 * 没走过的保持无标签 ✓ ⇒ 从此两种同款物品不能合并 ✓。
 *
 * <h2>怎么修（本节）</h2>
 * 统一**补**成 `{}`（不是抹掉 ✗）✓，三个时机：
 * <ol>
 *   <li><b>玩家背包每秒检查一次</b> ✓ —— 覆盖已经躺在背包里的老物品 ✓；</li>
 *   <li><b>拾取时</b> ✓（{@link EntityItemPickupEvent}）；</li>
 *   <li><b>合成时</b> ✓（{@link PlayerEvent.ItemCraftedEvent}）。</li>
 * </ol>
 * ⚠ <b>§786：本类**只补标签，绝不替你归并**</b> ✗ —— 用户口径「**有时候散开放是故意为之**」✓。
 * （§763 初版曾"补过标签就顺手合并一次" ✗，已按本条**删除** ✗；补完标签后那两叠**可以**手动合 ✓，
 * 但要不要合、什么时候合，由玩家自己决定 ✓。）
 *
 * <h2>⚠ 边界（如实说明 ✓）</h2>
 * <ul>
 *   <li>**只动"没有 NBT"的物品** ✓ —— 带真实数据的物品（工具耐久／背包内容／能量等）**一律不碰** ✗；</li>
 *   <li>**只作用于玩家自己的背包**（含盔甲/副手 ✓）＋ 拾取/合成的物品 ✓
 *       ——其它容器（箱子、别的模组机器、**AE2 已经存进去的**物品 ✗）不会被扫 ✓
 *       ⇒ AE2 里那些旧条目要**取出来一次**才会被归一化 ✓（再放回去就会并成一格 ✓）；</li>
 *   <li>整段逻辑包在 try/catch 里 ✓ 出任何问题都只当次跳过 ✗ 不会影响游戏 ✓；</li>
 *   <li>配置开关：`item_tag_normalize.enabled` ✓（默认 true ✓ 关掉就完全不动物品 ✓）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ItemTagNormalizer {

    /** 背包多久检查一次（tick） */
    private static final int CHECK_INTERVAL = 20;

    private ItemTagNormalizer() {
    }

    /**
     * ① 玩家背包：每秒一次补 `{}` ✓
     *
     * <p>⚠ <b>§786 用户口径：只补标签，**绝不自动归并**</b> ✗ ——
     * 「有时候散开放是故意为之」✓。所以这里**只把空标签补上** ✓，
     * 合不合堆由玩家自己拖 ✓（补完标签后它们**可以**合并 ✓，但**不会**被我们替你合 ✗）。
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % CHECK_INTERVAL != 0) return;
        if (!ModConfig.normalizeItemTag()) return;
        try {
            Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack == null || stack.isEmpty() || stack.hasTag()) continue;
                stack.setTag(new CompoundTag());
                inv.setItem(i, stack);
                // ⚠ 这里**故意不做任何合并** ✗（§786 用户口径：散开放是刻意的 ✓）
            }
        } catch (Throwable ignored) {
            // 归一化本身绝不许影响游戏 ✗
        }
    }

    /** ② 刚捡起来的物品 ✓ */
    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent event) {
        if (!ModConfig.normalizeItemTag()) return;
        try {
            normalize(event.getItem().getItem());
        } catch (Throwable ignored) {
        }
    }

    /** ③ 刚合成出来的物品 ✓ */
    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!ModConfig.normalizeItemTag()) return;
        try {
            normalize(event.getCrafting());
        } catch (Throwable ignored) {
        }
    }

    /** 没有 NBT 就补一个空 CompoundTag ✓（有 NBT 的一律不碰 ✗） */
    private static void normalize(ItemStack stack) {
        if (stack != null && !stack.isEmpty() && !stack.hasTag()) {
            stack.setTag(new CompoundTag());
        }
    }
}
