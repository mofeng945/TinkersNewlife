package com.mofengbaizhi.tinkersnewlife.content.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;

/**
 * <b>帕秋莉的百宝书</b>（§910 用户口径）——
 * 把"帕秋莉手册书"吞噬进来，之后一本书里查阅所有已吞噬的书 ✓。
 *
 * <ul>
 *   <li><b>吞噬</b>：在物品栏里**用手拖着百宝书**、对着一本帕秋莉书**右键** ⇒ 那本书被吞掉 ✓
 *       （客户端检测手势 {@code client/handler/CompendiumClient} ⇒ 发包 ⇒ 服务端校验后写 NBT ✓）；</li>
 *   <li><b>查阅</b>：手持百宝书**右键** ⇒ 打开自己的界面，**按书归类**列出已吞噬的书 ✓，
 *       点某一条就直接打开那本帕秋莉书 ✓；</li>
 *   <li><b>继承效果</b>：百宝书"等于"已吞噬的那些书 ✓（本模组自己的判定已经接上 ⇒ 例如
 *       呪术新生的「拿到编年史」判定，吞了编年史也算 ✓ 见 {@code AchievementHandler#hasOurGuideBook} ✓）。</li>
 * </ul>
 *
 * <p>⚠ <b>判据是 NBT 里的 {@code patchouli:book}，不是物品 id</b> ✓ —— 与 §632 / §910 本仓既有口径一致：
 * 帕秋莉的书物品都是同一个类（{@code ItemModBook}），"是哪本书"的身份**全在 NBT** 里 ✓
 * ⇒ 用户说的"某书类物品内有帕秋莉书的 id"就是这个键 ✓ 任何模组的帕秋莉书都认 ✓。
 *
 * <p>⚠ 按 §812：**不加任何 tooltip** ✗（用户没点名要 ✓）。要看内容就右键打开界面 ✓。
 */
public class CompendiumItem extends Item {

    /** 吞噬记录所在键：{@code AbsorbedBooks} = ListTag，每项 {@code {id:"<书 id>", name:"<显示名>"}} ✓ */
    public static final String KEY_BOOKS = "AbsorbedBooks";
    public static final String KEY_ID = "id";
    public static final String KEY_NAME = "name";

    /** 帕秋莉书的身份键（**本仓既有口径** ✓ 见 {@code ModCreativeTabs} / {@code AchievementHandler} ✓） */
    public static final String KEY_PATCHOULI_BOOK = "patchouli:book";

    public CompendiumItem(Properties properties) {
        super(properties);
    }

    /** 是帕秋莉书就返回它的书 id（{@code patchouli:book} 的值 ✓），不是则 {@code null} */
    @Nullable
    public static String bookIdOf(ItemStack stack) {
        if (stack.isEmpty()) return null;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(KEY_PATCHOULI_BOOK, Tag.TAG_STRING)) return null;
        String id = tag.getString(KEY_PATCHOULI_BOOK);
        return id.isEmpty() ? null : id;
    }

    /** 已吞噬的书（读出来是**原 NBT 里的那个 ListTag** ✓ 要改请先 {@code copy()} ✓） */
    public static ListTag absorbedList(ItemStack compendium) {
        CompoundTag tag = compendium.getTag();
        if (tag == null || !tag.contains(KEY_BOOKS, Tag.TAG_LIST)) return new ListTag();
        return tag.getList(KEY_BOOKS, Tag.TAG_COMPOUND);
    }

    public static int absorbedCount(ItemStack compendium) {
        return absorbedList(compendium).size();
    }

    public static boolean hasAbsorbed(ItemStack compendium, String bookId) {
        ListTag list = absorbedList(compendium);
        for (int i = 0; i < list.size(); i++) {
            if (bookId.equals(list.getCompound(i).getString(KEY_ID))) return true;
        }
        return false;
    }

    /**
     * 把一本书记进百宝书 ✓（同一本书**只记一次** ✓ 记的是"书 id + 当时的显示名" ✓
     * 显示名在吞噬时抓下来 ⇒ 界面里不必依赖帕秋莉的数据也能显示 ✓）。
     *
     * @return 是不是**第一次**吞这本（用来决定提示语 ✓）
     */
    public static boolean absorb(ItemStack compendium, String bookId, String displayName) {
        if (hasAbsorbed(compendium, bookId)) return false;
        ListTag list = absorbedList(compendium).copy();   // ⚠ 必须 copy：直接改原 tag 里的 list 有时不生效 ✓
        CompoundTag entry = new CompoundTag();
        entry.putString(KEY_ID, bookId);
        entry.putString(KEY_NAME, displayName);
        list.add(entry);
        compendium.getOrCreateTag().put(KEY_BOOKS, list);
        return true;
    }

    /**
     * 在玩家背包里找一叠百宝书（**不含光标上拖着的那叠** ✓ 那叠让调用方自己看 `getCarried()` ✓）。
     * <p>§910b 加的兜底：用户报"拖动右键没法吞书" ⇒ 除了"拖着/主手"，**背包里有**也认 ✓
     * （三种来源在包里用 {@code source} 区分 ✓ 服务端再各自校验 ✓）。
     *
     * @return 找到的那叠（就是背包里的**真身** ✓ 改它即生效 ✓）；找不到返回 {@code null} ✓
     */
    @Nullable
    public static ItemStack findInInventory(Player player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof CompendiumItem) return stack;
        }
        return null;
    }

    /** 手持右键 ⇒ 打开"查阅"界面 ✓（纯客户端 ✓ 数据就在手上这叠的 NBT 里 ⇒ 不用发包 ✓） */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            // ⚠ 客户端类**不能**在公共代码里直接引用（§801/§813 的坑 ✗）⇒ 走 DistExecutor 甩过去 ✓
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.mofengbaizhi.tinkersnewlife.client.handler.CompendiumClient.openScreen(stack));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
