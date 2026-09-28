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
 * <b>物品空标签归一化</b>（§763 建 ✓ ／ <b>§787 按原版纠正方向</b> ✓）——
 * 把**空的 CompoundTag {@code {}} 剥成"无标签"** ✓（而不是给所有物品补 {@code {}} ✗）
 *
 * <h2>要解决什么</h2>
 * 「同款物品却叠不到一起」✓ —— 存档实证（§762 ✓）：
 * <pre>
 * goety:cursed_cage  槽12 x4   NBT = {}        ← 有一个空标签
 * goety:cursed_cage  槽13 x1   NBT = (无 tag)  ← 完全没有标签
 * </pre>
 * 原版 {@code ItemStack.isSameItemSameTags} 比的是**原始 tag**（{@code Objects.equals}）✓
 * ⇒ <b>{@code {}} 与 {@code null} 不相等</b> ✗ ⇒ 两叠永远合不到一起 ✗（拖到一起也不行 ✗）。
 * 而 AE2 的键是「物品 ＋ {@code getTag()} ＋ 能力序列化」✓（`AEItemKey` 字节码实核 ✓）
 * ⇒ 同一件物品在 AE2 里也必然**分成两格存** ✓，取出来还是原样 ✓ —— 所以"背包里也叠不上" ✓。
 *
 * <p>根因举例：诡厄的 {@code CursedCageBlock#setPlacedBy} 用 {@code ItemStack#getOrCreateTag()} 去**读**
 * {@code BlockEntityTag} ✗ —— 这个 API 的语义是"没有就建一个空的"✗ ⇒ 走过这条路就留下 `{}` ✓，
 * 没走过的保持无标签 ✓ ⇒ 从此两种同款物品不能合并 ✓。
 *
 * <h2>哪一边才叫"原版"（§787：用户追问 ⇒ 已核反编译源码 ✓）</h2>
 * 用户 2026-09-28 原话：「<b>原版是不是本来应该是无标签？如果是就按原版来</b>」✓ —— 结论：**是** ✓。
 * 依据（{@code build/tmp-mcsrc/mcfull/.../world/item/ItemStack.java} ＋
 * {@code .../minecraftforge/common/extensions/IForgeItem.java} 实读 ✓）：
 * <ul>
 *   <li>{@code ItemStack#hasTag()}（L507）＝ {@code !isEmpty() && tag != null && !tag.isEmpty()}
 *       ⇒ <b>原版自己就认为 {@code {}} 等于"没有标签"</b> ✓（所以补 `{}` 连 {@code hasTag()} 都改不动 ✗）；</li>
 *   <li>{@code ItemStack#isSameItemSameTags()}（L459-463）用的是**原始** {@code Objects.equals(this.tag, other.tag)}
 *       ⇒ {@code {}} ≠ {@code null} ✗ —— "叠不上"就是这一行 ✗；</li>
 *   <li>{@code ItemStack#removeTagKey()}（L539-547）与 {@code resetHoverName()}（L603-605）
 *       <b>一旦把 tag 掏空就 {@code this.tag = null}</b> ✓
 *       ⇒ 原版语义里"什么都没有"就是 <b>null</b>，不是 {@code {}} ✓。</li>
 * </ul>
 * ⇒ 所以正确的归一化方向是 <b>把 {@code {}} 剥掉</b> ✓，让物品回到**原版新物品本来的状态**（无标签 ✓）；
 * §763 初版"给所有物品补 {@code {}} ✗"的方向已按本节**纠正** ✗（= 少碰物品、更贴原版 ✓）。
 *
 * <h2>怎么修（三个时机不变 ✓）</h2>
 * <ol>
 *   <li><b>玩家背包每秒检查一次</b> ✓ —— 覆盖已经躺在背包里的老物品 ✓；</li>
 *   <li><b>拾取时</b> ✓（{@link EntityItemPickupEvent}）；</li>
 *   <li><b>合成时</b> ✓（{@link PlayerEvent.ItemCraftedEvent}）。</li>
 * </ol>
 * ⚠ <b>§786：本类只动标签，绝不替你归并</b> ✗ —— 用户口径「**有时候散开放是故意为之**」✓。
 * （§763 初版曾"补过标签就顺手合并一次" ✗，已删除 ✗；剥完标签后那两叠**可以**手动合 ✓，
 * 但要不要合、什么时候合，由玩家自己决定 ✓。）
 *
 * <h2>⚠ 边界（如实说明 ✓）</h2>
 * <ul>
 *   <li>**只动"标签存在但是空的"物品** ✓ —— 带真实数据的物品（工具耐久／背包内容／能量等）**一律不碰** ✗；</li>
 *   <li>**伤害类物品**（{@code getMaxStackSize() == 1} ✓）：{@code ItemStack#setTag(null)} 内部会调
 *       {@code setDamageValue(getDamageValue())}（ItemStack L555-557 ✓），而 Forge
 *       {@code IForgeItem#setDamage}（L472）用的是 {@code getOrCreateTag()}
 *       ⇒ 标签会被**立刻写回 {@code {Damage:0}}** ✓ —— 这是**原版把工具修满耐久后本来就有的状态** ✓，
 *       而且这类物品 maxStackSize ＝ 1 ⇒ 对"能不能叠"没有任何影响 ✓（所以不额外处理 ✗）；</li>
 *   <li>**非伤害类物品**（方块类，如 {@code goety:cursed_cage} ✓）会干净地回到 **null** ✓（原版状态 ✓）；</li>
 *   <li>**能力（ForgeCaps）不动** ✓，且原版合并还要过 {@code areCapsCompatible} ✓
 *       ⇒ 能力不同的物品**不会**因为我们剥标签而变成能叠 ✓（不会被误合 ✗）；</li>
 *   <li>**只作用于玩家自己的背包**（含盔甲/副手 ✓）＋ 拾取/合成的物品 ✓
 *       ——其它容器（箱子、别的模组机器、**AE2 已经存进去的**物品 ✗）不会被扫 ✓
 *       ⇒ AE2 里那些旧条目要**取出来一次**才会被归一化 ✓；</li>
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
     * ① 玩家背包：每秒一次，把空标签 `{}` **剥成"无标签"** ✓（§787 方向 ✓）
     *
     * <p>⚠ <b>§786 用户口径：只改标签，**绝不自动归并**</b> ✗ ——
     * 「有时候散开放是故意为之」✓。所以这里**只把空标签剥掉** ✓，
     * 合不合堆由玩家自己拖 ✓。
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
                if (stripEmptyTag(stack)) {
                    inv.setItem(i, stack);
                }
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
            stripEmptyTag(event.getItem().getItem());
        } catch (Throwable ignored) {
        }
    }

    /** ③ 刚合成出来的物品 ✓ */
    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!ModConfig.normalizeItemTag()) return;
        try {
            stripEmptyTag(event.getCrafting());
        } catch (Throwable ignored) {
        }
    }

    /**
     * 标签存在但**是空的** ⇒ 剥成"无标签"（回到原版状态 ✓）；返回是否真的动过 ✓
     *
     * <p>带真实数据的物品（工具耐久／背包内容／能量等）**一律不碰** ✗。
     */
    private static boolean stripEmptyTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.isEmpty()) return false;
        stack.setTag(null);
        return true;
    }
}
