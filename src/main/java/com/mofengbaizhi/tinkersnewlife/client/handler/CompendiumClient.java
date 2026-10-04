package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.screen.CompendiumScreen;
import com.mofengbaizhi.tinkersnewlife.content.item.CompendiumItem;
import com.mofengbaizhi.tinkersnewlife.network.compendium.PacketCompendiumAbsorb;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>帕秋莉的百宝书 —— 客户端部分</b>（§910 / §910d / §910e）：开界面 ＋ 物品栏里的"吞噬"手势 ✓。
 *
 * <p>挂的是 <b>Forge 总线</b>（注解里不写 bus ⇒ 默认 FORGE ✓）＋ {@code Dist.CLIENT} ✓
 * ⇒ 专服加载不到这里 ✓ 不会 NoClassDefFoundError ✗。
 * <p>事件链已核对（反汇编 Forge jar ✓）：{@code MouseHandler#onPress} →
 * {@code ForgeHooksClient.onScreenMouseClickedPre} ⇒ 正是 {@code ScreenEvent.MouseButtonPressed.Pre} ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT)
public final class CompendiumClient {

    private static final Logger LOG = LoggerFactory.getLogger("TinkersNewlife/Compendium");

    /**
     * §910g <b>已知"书 id ≠ 物品 id"的对应表</b>（推不出来的那几件，只能硬记 ✓）。
     * <p>「倒转之启」`enigmaticlegacy:the_twist`：反编译确认它的类
     * {@code TheTwist extends TheAcknowledgment} ✓ ⇒ **书 id 继承自父类 = `enigmaticlegacy:the_acknowledgment`** ✓
     * （两条 lore 也印证："与启示之证的用途几乎一样…只不过被包裹在不同的封面" ✓）。
     * <p>⚠ 加新条目时请**先在帕秋莉书表里验证**（本方法只接受书表里真实存在的 id ✓）免得写错 ✗。
     */
    /**
     * §910q <b>"效果型知识道具"白名单</b>（不是帕秋莉书 ✗ 但也是"能吞进来的知识" ✓）——
     * 它们的界面没有 ✓，吞进来后靠**潜行右键唤醒**（= 把那一本「用一下」✓）继承效果 ✓。
     * <ul>
     *   <li><b>神秘遗物</b>：野猎指南 {@code hunter_guidebook} / 兽友指南 {@code animal_guidebook} ✓
     *       —— ⚠ 这两件的**被动**（"放物品栏里"那种）写在神秘遗物自己的事件里 ✗ 继承不到 ✗；
     *       但它们 item 自己的 {@code use()} 部分能靠唤醒转调 ✓。</li>
     *   <li><b>诡厄巫法</b>：三本会调 {@code SEHelper} 的魔典（怨恨/亲善/坚守 ✓）
     *       ＋ 发研究（= 回魂等能力的解锁条件 ✓）的那批卷轴 ✓ ⇒ 唤醒 = 用一遍 ✓。</li>
     * </ul>
     */
    private static final java.util.Set<String> EFFECT_KNOWLEDGE_IDS = java.util.Set.of(
            // 神秘遗物
            "enigmaticlegacy:hunter_guidebook",     // 野猎指南
            "enigmaticlegacy:animal_guidebook",     // 兽友指南
            // 诡厄巫法：三本魔典
            "goety:grimoire_of_grudges",            // 怨恨之书
            "goety:grimoire_of_goodwill",           // 亲善之书
            "goety:grimoire_of_grounding",          // 坚守之书
            // 诡厄巫法：研究卷轴（用一下 = 解锁研究 ⇒ 回魂之类能力 ✓）
            "goety:dark_scroll", "goety:ravaging_scroll", "goety:warred_scroll",
            "goety:buried_scroll", "goety:haunting_scroll", "goety:front_scroll",
            "goety:mistral_scroll", "goety:floral_scroll", "goety:bygone_scroll",
            "goety:terminus_scroll", "goety:forbidden_scroll"
    );

    /**
     * §910q 新模组「诡厄遗物」({@code goeticlegacy}) 的启发式：命名空间命中 ＋ 类名像"书/卷/颂/典" ✓
     * ⇒ 以后它新增同类物品不用再来改代码 ✓（被动型效果仍然继承不到 ✗ 见 {@link #EFFECT_KNOWLEDGE_IDS} 的说明）。
     */
    private static boolean looksLikeGoeticLegacyKnowledge(net.minecraft.world.item.Item item) {
        net.minecraft.resources.ResourceLocation id =
                net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);
        if (id == null || !"goeticlegacy".equals(id.getNamespace())) return false;
        String cls = item.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
        return cls.contains("book") || cls.contains("scroll") || cls.contains("ode")
                || cls.contains("codex") || cls.contains("tome") || cls.contains("page");
    }

    private static final java.util.Map<String, String> KNOWN_BOOK_IDS = java.util.Map.of(
            "enigmaticlegacy:the_twist", "enigmaticlegacy:the_acknowledgment"
    );

    private CompendiumClient() {}

    /** 手持右键 ⇒ 打开"查阅"界面 ✓（由 {@code CompendiumItem#use} 经 DistExecutor 调过来 ✓） */
    public static void openScreen(ItemStack stack) {
        Minecraft.getInstance().setScreen(new CompendiumScreen(stack));
    }

    /**
     * §910e <b>把"这本书的帕秋莉书 id"推出来</b>（三条规则 ✓ 越靠前越权威 ✓）：
     * <ol>
     *   <li>NBT {@code patchouli:book} —— 最权威 ✓（本仓既有口径 ✓ 创造栏那本走这条 ✓）；</li>
     *   <li>物品是帕秋莉的 {@code ItemModBook} ⇒ 直接问它（{@code Book.id} 是公共字段 ✓）；</li>
     *   <li><b>通用兜底</b>（§910e 新加 ✓）：帕秋莉约定书数据在
     *       {@code data/<命名空间>/patchouli_books/<路径>/book.json} ✓，
     *       而<u>物品 id 的路径通常就等于书的路径</u> ——
     *       例：物品 {@code enigmaticlegacy:the_acknowledgment}
     *       ⇔ {@code data/enigmaticlegacy/patchouli_books/the_acknowledgment/book.json} ✓
     *       ⇒ 探一下这个资源在不在，在就认 ✓
     *       （神秘遗物「启示之证」就是这样一本书：物品类里藏着自己的私有 BOOK_ID，
     *        物品上**没有** NBT ✗ —— 用户实测点出来的 ✓）。</li>
     * </ol>
     * ⚠ 本方法会用到 {@code Minecraft}（客户端类 ✓）⇒ 只能放在客户端类里 ✓
     * （公共代码里那份 {@link CompendiumItem#bookIdOf} 仍然只认 NBT ✓ 服务端用 ✓）。
     *
     * @return 推出来的书 id ✓；推不出来返回 {@code null}（⇒ 这本书吞不了 ✓）
     */
    private static String resolveBookId(ItemStack stack) {
        // ① NBT `patchouli:book`
        String fromTag = CompendiumItem.bookIdOf(stack);
        if (fromTag != null) return fromTag;

        // ② 帕秋莉的 ItemModBook ⇒ 问它自己
        try {
            if (stack.getItem() instanceof vazkii.patchouli.common.item.ItemModBook) {
                vazkii.patchouli.common.book.Book book =
                        vazkii.patchouli.common.item.ItemModBook.getBook(stack);
                if (book != null && book.id != null) return book.id.toString();
            }
        } catch (Throwable t) {
            LOG.info("[百宝书] 规则②问帕秋莉失败：{}", t.toString());
        }

        // ②' §910g 已知映射（书 id ≠ 物品 id 的那几件 ✓ 必须先在书表里验证过 ✓）
        try {
            ResourceLocation rawId = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (rawId != null && KNOWN_BOOK_IDS.containsKey(rawId.toString())) {
                ResourceLocation mapped = ResourceLocation.tryParse(KNOWN_BOOK_IDS.get(rawId.toString()));
                if (mapped != null
                        && vazkii.patchouli.common.book.BookRegistry.INSTANCE.books.containsKey(mapped)) {
                    LOG.info("[百宝书] 规则②'：已知映射 {} → {} ✓", rawId, mapped);
                    return mapped.toString();
                }
                LOG.info("[百宝书] 规则②'：映射 {} → {} 但书表里没有这本，忽略 ✗",
                        rawId, KNOWN_BOOK_IDS.get(rawId.toString()));
            }
        } catch (Throwable t) {
            LOG.info("[百宝书] 规则②'失败：{}", t.toString());
        }

        // ②'' §910q "效果型知识道具"：按白名单 / 新模组的类名启发式收下 ✓
        //   书 id 用合成形式 `effect:<物品id>` ✓（界面里点它只会提示"请潜行右键唤醒" ✓）
        try {
            ResourceLocation rawItem = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (rawItem != null) {
                if (EFFECT_KNOWLEDGE_IDS.contains(rawItem.toString())
                        || looksLikeGoeticLegacyKnowledge(stack.getItem())) {
                    LOG.info("[百宝书] 规则②''：{} 属效果型知识道具 ⇒ 收下 ✓", rawItem);
                    return "effect:" + rawItem;
                }
            }
        } catch (Throwable t) {
            LOG.info("[百宝书] 规则②''跳过（{}）", t.toString());
        }

        // ③ 通用兜底：**拿物品 id 当候选书 id，问帕秋莉"到底有没有这本书"** ✓
        //    （帕秋莉约定：书 id = `patchouli_books/<路径>` 那层目录 ⇒ 与物品 id **常常同名** ✓
        //      实测证据：神秘遗物「启示之证」的字节码里同时有 "enigmaticlegacy" 与 "the_acknowledgment"
        //      ⇒ 它的私有 BOOK_ID = `enigmaticlegacy:the_acknowledgment` = **物品 id 本身** ✓
        //      其 book.json 的 model 也正是这个 ✓）
        //    ⚠ §910f：上一版只"探资源 `data/<ns>/patchouli_books/<路径>/book.json`" ⇒ 客户端**没命中** ✗
        //      （日志：`解析书id=null` 且无异常 ⇒ 是资源没找到，客户端资源管理器不一定含 mod 的 data/ ✗）
        //      ⇒ 改成**直接问帕秋莉的书表** ✓（`BookRegistry.INSTANCE.books` 已核对是 public Map ✓）
        try {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (itemId != null) {
                var books = vazkii.patchouli.common.book.BookRegistry.INSTANCE.books;
                boolean hitExact = books.containsKey(itemId);
                LOG.info("[百宝书] 规则③：候选书id={} 帕秋莉已加载书数={} 同名命中={}",
                        itemId, books.size(), hitExact);
                // 3a 候选 = 物品 id（绝大多数情况 ✓）
                if (hitExact) return itemId.toString();
                // 3b 备选：同名不同命名空间（书挂在别的前缀下也能认 ✓）
                for (ResourceLocation id : books.keySet()) {
                    if (id.getPath().equals(itemId.getPath())) return id.toString();
                }
                // 3c 最后再探一次资源（留给"书数据路径 = 物品路径"但注册表还没加载好的极端情况 ✓）
                ResourceLocation probe = new ResourceLocation(itemId.getNamespace(),
                        "patchouli_books/" + itemId.getPath() + "/book.json");
                if (Minecraft.getInstance().getResourceManager().getResource(probe).isPresent()) {
                    return itemId.toString();
                }
            }
        } catch (Throwable t) {
            LOG.info("[百宝书] 规则③失败：{}", t.toString());
        }
        return null;
    }

    /**
     * §910 <b>物品栏手势</b>：手上有百宝书（光标拖着 / 主手 / 背包里）＋ <b>右键</b>一格<b>帕秋莉书</b> ⇒ 吞噬 ✓。
     *
     * <p>为什么挂 {@code ScreenEvent.MouseButtonPressed.Pre}：
     * 它比原版的槽位处理**更早** ✓ ⇒ 认出来就能 {@code setCanceled(true)} 拦掉原版那一手 ✓。
     * <p>为什么用 {@code getSlotUnderMouse()}：1.20.1 里它是 <b>public</b> ✓（已核对源码 ✓）⇒ 不用 AT/mixin ✓。
     */
    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 1) return;                              // 只认右键 ✓
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || !slot.hasItem()) return;

        ItemStack hovered = slot.getItem();
        ItemStack carriedStack = screen.getMenu().getCarried();
        boolean carried = carriedStack.getItem() instanceof CompendiumItem;
        boolean inHand = player.getMainHandItem().getItem() instanceof CompendiumItem;
        boolean inBag = !carried && !inHand && CompendiumItem.findInInventory(player) != null;
        String bookId = (carried || inHand || inBag) ? resolveBookId(hovered) : null;

        // 探针：只要"这次右键跟百宝书有关"就打一行 ✓（无关就一声不响 ✓ 不刷屏 ✓）
        if (carried || inHand || inBag) {
            LOG.info("[百宝书] 右键：界面={} 槽位={} 槽内={} 解析书id={} 光标={} 主手={} 背包={} 创造={}",
                    screen.getClass().getSimpleName(), slot.index, hovered.getDescriptionId(),
                    bookId, carried, inHand, inBag, player.isCreative());
        }

        if (!carried && !inHand && !inBag) return;                        // 手上/包里没百宝书 ⇒ 不插手 ✓
        if (bookId == null) {
            // §910e 不认得的书：只在"正拖着百宝书"时提示一句 ✓（免得右键泥土也刷屏 ✗）
            if (carried) {
                player.displayClientMessage(
                        Component.translatable("message.tinkersnewlife.compendium.not_patchouli"), true);
            }
            return;
        }

        // §910k 七咒门禁（客户端先拦一道 ✓ 只为给提示 ✓ 服务端仍会再校验 ✓ 不信任客户端 ✓）
        if (!CompendiumItem.canAbsorb(player, hovered)) {
            LOG.info("[百宝书] 七咒限制：{} 需要受咒者，当前不满足 ⇒ 拦下", hovered.getDescriptionId());
            player.displayClientMessage(
                    Component.translatable("message.tinkersnewlife.compendium.cursed_only"), true);
            event.setCanceled(true);     // 拦掉原版那一手（免得把百宝书"放一个"进去 ✗）
            return;
        }

        int source;
        if (carried && player.isCreative()) {
            // §910d 创造模式：光标那叠服务端看不见 ✗ ⇒ 本地登记 ✓ ＋ 让服务端只吃掉这本书 ✓
            ResourceLocation hoveredItem = ForgeRegistries.ITEMS.getKey(hovered.getItem());
            boolean first = CompendiumItem.absorb(carriedStack, bookId, hovered.getHoverName().getString(),
                    hoveredItem == null ? "" : hoveredItem.toString());
            LOG.info("[百宝书] 创造模式光标：本地登记完成（第一次={}）现在共 {} 本 ⇒ 请服务端消费该书",
                    first, CompendiumItem.absorbedCount(carriedStack));
            source = PacketCompendiumAbsorb.SOURCE_CLIENT_LOCAL;
        } else if (carried) {
            source = PacketCompendiumAbsorb.SOURCE_CARRIED;
        } else if (inHand) {
            source = PacketCompendiumAbsorb.SOURCE_MAIN_HAND;
        } else {
            source = PacketCompendiumAbsorb.SOURCE_INVENTORY;
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(hovered.getItem());
        TinkersNewlife.CHANNEL.sendToServer(new PacketCompendiumAbsorb(bookId,
                itemId == null ? "" : itemId.toString(), source));
        LOG.info("[百宝书] 已发包：书id={} 物品={} 来源={}", bookId, itemId, source);
        event.setCanceled(true);                                          // 拦掉原版的"放一个" ✗
    }
}
