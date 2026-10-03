package com.mofengbaizhi.tinkersnewlife.content.item;

import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import javax.annotation.Nullable;

/**
 * <b>帕秋莉的百宝书</b>（§910 用户口径）——
 * 把"帕秋莉手册书"吞噬进来，之后一本里查阅所有已吞噬的书 ✓。
 *
 * <ul>
 *   <li><b>吞噬</b>：在物品栏里**手上有百宝书** + 对着一本帕秋莉书**右键** ⇒ 那本书被吞掉 ✓
 *       （客户端检测手势 ⇒ 发包 ⇒ 服务端校验后写 NBT ✓）；</li>
 *   <li><b>查阅</b>：手持百宝书**右键** ⇒ 打开自己的界面，**按书归类**列出已吞噬的书 ✓，
 *       点某一条就直接打开那本帕秋莉书 ✓；</li>
 *   <li><b>唤醒（§910g 新加）</b>：**潜行右键** ⇒ 依次调用每本已吞噬之书**自己的 {@code use(...)}** ✓
 *       ⇒ "效果写在物品自己的 use 里"的书就能被继承 ✓（详见 {@link #wakeAbsorbedBooks} 的说明与限制 ✓）。</li>
 * </ul>
 *
 * <p>⚠ <b>判据是 NBT 里的 {@code patchouli:book}，不是物品 id</b> ✓ —— 与 §632 / §910 本仓既有口径一致：
 * 帕秋莉的书物品都是同一个类（{@code ItemModBook}），"是哪本书"的身份**全在 NBT** 里 ✓
 * ⇒ 用户说的"某书类物品内有帕秋莉书的id"就是这个键 ✓ 任何模组的帕秋莉书都认 ✓。
 * <p>§910e 起客户端侧还有两条更宽的规则（`ItemModBook` 自身 / 直接问帕秋莉书表 ✓）见
 * {@code client/handler/CompendiumClient#resolveBookId} ✓。
 *
 * <p>⚠ 按 §812：**不加任何 tooltip** ✗（用户没点名要 ✓）。要看内容就右键打开界面 ✓。
 */
public class CompendiumItem extends Item {

    private static final Logger LOG = LogUtils.getLogger();

    /** 吞噬记录所在键：{@code AbsorbedBooks} = ListTag，每项 {@code {id, name, item}} ✓ */
    public static final String KEY_BOOKS = "AbsorbedBooks";
    /** 帕秋莉书 id（开界面用 ✓） */
    public static final String KEY_ID = "id";
    /** 吞噬时的显示名（界面里显示用 ✓） */
    public static final String KEY_NAME = "name";
    /** §910g 物品 id（唤醒效果时用来"造一个那本书" ✓） */
    public static final String KEY_ITEM = "item";

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
     * 在玩家背包里找一叠百宝书（**不含光标上拖着的那叠** ✓ 那叠让调用方自己看 `getCarried()` ✓）。
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

    /** 记一条（同一本书**只记一次** ✓）；返回"是不是第一次"✓ */
    public static boolean absorb(ItemStack compendium, String bookId, String displayName, String itemId) {
        if (hasAbsorbed(compendium, bookId)) return false;
        ListTag list = absorbedList(compendium).copy();   // ⚠ 必须 copy：直接改原 tag 里的 list 有时不生效 ✓
        CompoundTag entry = new CompoundTag();
        entry.putString(KEY_ID, bookId);
        entry.putString(KEY_NAME, displayName);
        entry.putString(KEY_ITEM, itemId == null ? "" : itemId);   // §910g 唤醒要用 ✓
        list.add(entry);
        compendium.getOrCreateTag().put(KEY_BOOKS, list);
        return true;
    }

    /** 已吞噬记录里的**物品**（造一个 ItemStack ✓；没有记物品 id 的旧记录跳过 ✗） */
    private static java.util.List<ItemStack> absorbedStacks(ItemStack compendium) {
        ListTag list = absorbedList(compendium);
        java.util.List<ItemStack> out = new java.util.ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.contains(KEY_ITEM, Tag.TAG_STRING)) continue;
            ResourceLocation id = ResourceLocation.tryParse(entry.getString(KEY_ITEM));
            if (id == null) continue;
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item == null) continue;
            out.add(new ItemStack(item));
        }
        return out;
    }

    /**
     * §910j <b>攻击属性继承</b>（用户口径：「让启示之证 / 倒转之启 / 无止之言的攻击效果和伤害效果也起作用」✓）——
     * 把"已吞噬的书里**最强的那件武器**"的整套属性修饰符借过来 ✓（伤害 / 攻速 / 击退 … ✓ 都是从物品本身取的 ✓
     * 不硬编码任何数值 ✓）。
     *
     * <p>口径说明：<b>取最强的一件，不是把多件叠起来</b> ✗ ——
     * 一是叠加会把伤害直接堆到失衡 ✗；二是原版基础攻击力用的是**固定 UUID** ✓
     * 两件一起给会互相覆盖 / 报错 ✗。想改成"叠加"或"只看某一件"都只需改这一段 ✓。
     */
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot == EquipmentSlot.MAINHAND) {
            Multimap<Attribute, AttributeModifier> best = null;
            double bestDamage = 0.0D;
            for (ItemStack fake : absorbedStacks(stack)) {
                Multimap<Attribute, AttributeModifier> mods = fake.getAttributeModifiers(slot);
                if (mods.isEmpty()) continue;
                double damage = 0.0D;
                for (var e : mods.entries()) {
                    if (e.getKey() == Attributes.ATTACK_DAMAGE) damage += e.getValue().getAmount();
                }
                if (best == null || damage > bestDamage) {
                    best = mods;
                    bestDamage = damage;
                }
            }
            if (best != null) return best;
        }
        return super.getAttributeModifiers(slot, stack);
    }

    /**
     * §910j <b>命中效果继承</b>：对每一本已吞噬的书都跑一遍它自己的 {@code hurtEnemy} ✓。
     * <p>⚠ 1.20.1 的 {@code Item} **没有** {@code postHurtEnemy} ✗（已核对源码：只有
     * {@code hurtEnemy(ItemStack, LivingEntity, LivingEntity)} ✓，`Player#attack` 也只调这一处 ✓）
     * ⇒ 命中后那一半本来就在各模组自己的 {@code hurtEnemy} 里 ✓ 无需额外转发 ✓。
     */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean any = false;
        for (ItemStack fake : absorbedStacks(stack)) {
            try {
                any |= fake.getItem().hurtEnemy(fake, target, attacker);
            } catch (Throwable t) {
                LOG.warn("[百宝书] 命中效果 {} 出错（已跳过）：{}", fake.getItem(), t.toString());
            }
        }
        return any || super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // ① §910g **潜行右键 = 唤醒已吞噬之书的效果**（服务端执行 ✓ 客户端只回成功 ✓ 免得开一堆界面 ✗）
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                int woken = wakeAbsorbedBooks(level, player, stack);
                player.displayClientMessage(Component.translatable(woken > 0
                                ? "message.tinkersnewlife.compendium.wake"
                                : "message.tinkersnewlife.compendium.wake_none",
                        woken), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        // ② 普通右键 = 打开"查阅"界面（纯客户端 ✓ 数据就在手上这叠的 NBT 里 ⇒ 不用发包 ✓）
        if (level.isClientSide) {
            // ⚠ 客户端类**不能**在公共代码里直接引用（§801/§813 的坑 ✗）⇒ 走 DistExecutor 甩过去 ✓
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.mofengbaizhi.tinkersnewlife.client.handler.CompendiumClient.openScreen(stack));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /**
     * §910g <b>唤醒：把每本已吞噬之书"用自己的 use 再跑一遍"</b> ✓。
     *
     * <p>做法：对每条记录造一个那本书的 {@link ItemStack}（只要物品 ✓ 不带它原来的 NBT ✗）
     * ⇒ 调它的 {@code use(level, player, hand)} ✓ ⇒ 效果就落在玩家身上了 ✓。
     * <p>跳过帕秋莉书本体（{@code ItemModBook}）✓：它们的 {@code use} 是"开界面" ✓
     * —— 一次唤醒开一堆界面没有意义 ✗（查阅请在界面里点单本 ✓）。
     *
     * <p>⚠⚠ <b>明确继承不了的情形</b>（如实记 ✓，免得再被当成 bug ✗）：
     * 若那本书的效果是写在**它自己模组的事件里**、靠"玩家背包/饰品/主手里有**它自己那件物品**"来判定的 ✗
     * ⇒ 我们无论怎么调都继承不了 ✗（只能改那个模组的代码 ✗）。
     * 例如诡厄巫法的「黑暗秘典」——已核对：**诡厄里没有任何类读取 {@code patchouli:book}** ✗，
     * 加魂的只有 {@code SoulItem} / {@code DarkScytheItem} / 方块实体 / 击杀事件 ✗
     * ⇒ 那本书本身并没有"恢复灵魂能量"的效果可供继承 ✗。
     *
     * @return 真正"做了事"（use 返回 SUCCESS/CONSUME ✓）的本数 ✓
     */
    private static int wakeAbsorbedBooks(Level level, Player player, ItemStack compendium) {
        ListTag list = absorbedList(compendium);
        int woken = 0;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.contains(KEY_ITEM, Tag.TAG_STRING)) continue;
            ResourceLocation itemId = ResourceLocation.tryParse(entry.getString(KEY_ITEM));
            if (itemId == null) continue;
            Item item = ForgeRegistries.ITEMS.getValue(itemId);
            if (item == null) continue;
            // 帕秋莉书本体的 use 是"开界面" ⇒ 跳过 ✓（查阅走界面 ✓）
            if (item instanceof vazkii.patchouli.common.item.ItemModBook) continue;
            try {
                ItemStack fake = new ItemStack(item);
                InteractionResultHolder<ItemStack> result = fake.use(level, player, InteractionHand.MAIN_HAND);
                if (result.getResult().consumesAction()) woken++;
            } catch (Throwable t) {
                LOG.warn("[百宝书] 唤醒 {} 的效果时出错（已跳过）：{}", itemId, t.toString());
            }
        }
        return woken;
    }
}
