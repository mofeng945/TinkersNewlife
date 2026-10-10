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
 *   <li>⚠ <b>§910v 起不再继承任何效果</b> ✗（用户口径：「**不继承效果了…只当万能百宝书用就好**」✓）
 *       —— 它只做"记录 + 查阅"，书本身**不消耗** ✓。</li>
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

    /** §910l 诊断只打一次 ✓ */
    private static boolean tnl$logged = false;

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
        String wantItem = itemId == null ? "" : itemId;
        ListTag list = absorbedList(compendium).copy();   // ⚠ 必须 copy：直接改原 tag 里的 list 有时不生效 ✓

        // §910m **同一本书再吞一次 ⇒ 刷新那条记录**（不新增 ✓）：
        //   老记录（§910g 之前吞的）没有 `item` 字段 ✗ ⇒ 属性/效果转发只能拿书 id 猜 ✗
        //   —— 而「倒转之启」的书 id 恰好是 `enigmaticlegacy:the_acknowledgment`（§910g 映射 ✓）
        //   猜出来就变成**启示之证**（3.5 伤害 ✗ 用户实测 ✓）⇒ 再吞一本真·倒转之启即可修正 ✓。
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            if (!bookId.equals(e.getString(KEY_ID))) continue;
            boolean changed = false;
            if (!wantItem.isEmpty() && !wantItem.equals(e.getString(KEY_ITEM))) {
                e.putString(KEY_ITEM, wantItem);
                changed = true;
            }
            if (displayName != null && !displayName.isEmpty() && !displayName.equals(e.getString(KEY_NAME))) {
                e.putString(KEY_NAME, displayName);
                changed = true;
            }
            if (changed) {
                compendium.getOrCreateTag().put(KEY_BOOKS, list);
                LOG.info("[百宝书] 已刷新记录 {} ⇒ 物品={}（旧记录缺物品 id 时靠这一步修好 ✓）", bookId, wantItem);
            }
            return false;
        }

        CompoundTag entry = new CompoundTag();
        entry.putString(KEY_ID, bookId);
        entry.putString(KEY_NAME, displayName);
        entry.putString(KEY_ITEM, wantItem);   // §910g 唤醒/借属性要用 ✓
        list.add(entry);
        compendium.getOrCreateTag().put(KEY_BOOKS, list);
        return true;
    }

    /**
     * §910k <b>七咒门禁</b>：神秘遗物里"带七咒限制"的物品都实现它的标记接口
     * {@code com.aizistral.enigmaticlegacy.api.items.ICursed}（**空接口** ✓ 零方法 ✓），
     * 而那条限制的原文是「<b>承受七咒之人，才能使用该物品</b>」✓
     * ⇒ 判定 = {@code SuperpositionHandler.isTheCursedOne(player)} ✓。
     *
     * <p>用户口径：「**限定为有七咒限制的书在未满足条件情况下不能吞噬**」✓
     * ⇒ 带限制的书，**不是受咒者就吞不了** ✓（客户端先拦一道给提示 ✓ 服务端再校验一次 ✓）。
     *
     * <p>⚠ 为什么用**反射**：本模组对神秘遗物没有（也不该有）任何依赖 ✗
     * ⇒ 直接引用它的类会给"没装神秘遗物"的整合包埋 NoClassDefFoundError 的雷 ✗（§801 的坑 ✓）。
     * 没装神秘遗物时这两个判定一律返回 false ⇒ 门禁自动失效 ✓ 不会误拦 ✓。
     */
    private static boolean implementsCursed(Class<?> cls) {
        while (cls != null && cls != Object.class) {
            for (Class<?> itf : cls.getInterfaces()) {
                if ("com.aizistral.enigmaticlegacy.api.items.ICursed".equals(itf.getName())) return true;
                if (implementsCursed(itf)) return true;
            }
            cls = cls.getSuperclass();
        }
        return false;
    }

    /** 这件物品是不是"带七咒限制"的书（= 神秘遗物的 {@code ICursed} ✓ 含父类/接口继承 ✓） */
    public static boolean isCursedItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!net.minecraftforge.fml.ModList.get().isLoaded("enigmaticlegacy")) return false;
        try {
            return implementsCursed(stack.getItem().getClass());
        } catch (Throwable t) {
            LOG.warn("[百宝书] 判断七咒限制失败（按「无限制」处理）：{}", t.toString());
            return false;
        }
    }

    /**
     * 玩家是不是"承受七咒之人" ✓（反射调 {@code SuperpositionHandler.isTheCursedOne(Player)} ✓）。
     * <p>⚠ 查不到时**按"不是"处理**（fail-closed ✓）—— 宁可挡住，也不让带限制的书被绕过 ✓。
     */
    public static boolean isTheCursedOne(Player player) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("enigmaticlegacy")) return false;
        try {
            Class<?> helper = Class.forName("com.aizistral.enigmaticlegacy.handlers.SuperpositionHandler");
            Object result = helper.getMethod("isTheCursedOne", Player.class).invoke(null, player);
            return result instanceof Boolean b && b;
        } catch (Throwable t) {
            LOG.warn("[百宝书] 查询是否受七咒失败（按「未受咒」处理 ⇒ 挡下带限制的书）：{}", t.toString());
            return false;
        }
    }

    /** §910k 这本书能不能被吞：**没限制**的随便吞 ✓；**带七咒限制**的只有受咒者能吞 ✓ */
    public static boolean canAbsorb(Player player, ItemStack book) {
        return !isCursedItem(book) || isTheCursedOne(player);
    }

    /**
     * 已吞噬记录里的**物品 id**（与 {@code absorbedStacks} 同一套口径 ✓：
     * 有 {@code item} 就用它 ✓；老记录缺这个字段时**回退拿书 id** 顶 ✓）。
     * <p>§910n 用来判断"百宝书吞没吞过某件自带豁免的物品" ✓（例如倒转之启 ✓）。
     */
    public static java.util.List<String> absorbedItemIds(ItemStack compendium) {
        ListTag list = absorbedList(compendium);
        java.util.List<String> out = new java.util.ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String raw = entry.contains(KEY_ITEM, Tag.TAG_STRING) && !entry.getString(KEY_ITEM).isEmpty()
                    ? entry.getString(KEY_ITEM)
                    : entry.getString(KEY_ID);
            if (!raw.isEmpty()) out.add(raw);
        }
        return out;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 普通右键 = 打开"查阅"界面（纯客户端 ✓ 数据就在手上这叠的 NBT 里 ⇒ 不用发包 ✓）
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
            // ⚠ §1118q 帕秋莉是**可选**依赖 ⇒ 不在场时不能碰这个类 ✗
            //   （`instanceof` 同样要解析类 ✓ ⇒ 没装帕秋莉时这条循环第一次就会 NoClassDefFoundError ✗）
            if (com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader.isPatchouli()
                    && item instanceof vazkii.patchouli.common.item.ItemModBook) {
                continue;
            }
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
