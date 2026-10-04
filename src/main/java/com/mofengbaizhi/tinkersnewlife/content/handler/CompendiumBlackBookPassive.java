package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.CompendiumItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * <b>帕秋莉的百宝书 —— 继承「黑暗秘典」的回魂被动</b>（§910r）。
 *
 * <p>用户口径：「**诡厄遗物给黑暗秘典额外写了放在背包里恢复灵魂能量的效果**」✓ —— 完全正确 ✓，
 * 而且这次找到了源头：**是新模组「诡厄遗物」（{@code goeticlegacy}）** ✓（不是诡厄本体 ✗
 * —— 这也解释了 §910g~§910i 那几轮为什么在诡厄里怎么翻都翻不到 ✗）。
 *
 * <h3>它的实现（反编译 {@code goeticlegacy.event.passive.BlackBookPassiveHandler} ✓）</h3>
 * <pre>
 *   // PlayerTickEvent（END、仅服务端 ✓）
 *   boolean has = !InventoryQueryUtils.collectItemsFromInventoryAndBookBag(player,
 *         s -&gt; id(s) == patchouli:guide_book &amp;&amp; s.getTag().getString("patchouli:book").equals("goety:black_book")
 *      ).isEmpty();
 *   AttributeInstance soulRegen = player.getAttribute(ModAttributes.SOUL_REGEN.get());
 *   if (has) soulRegen.addTransientModifier(new AttributeModifier(BLACK_BOOK_SOUL_REGEN_UUID,
 *                 "black_book_passive_soul_regen", Config.BLACK_BOOK_PASSIVE.passiveSoulRegen.get(), ADDITION));
 *   else     soulRegen.removeModifier(BLACK_BOOK_SOUL_REGEN_UUID);
 * </pre>
 * ⇒ 它给玩家挂的是**自定义属性 {@code SOUL_REGEN} 的一个瞬时修饰符** ✓（数值来自它的配置 ✓）。
 *
 * <h3>这里的镜像做法</h3>
 * <ul>
 *   <li>条件换成"**玩家背包里的百宝书吞过 {@code goety:black_book}**" ✓；</li>
 *   <li>加的是**同一个属性**（{@code ModAttributes.SOUL_REGEN} ✓ 反射取 ✓）、
 *       **同样的数值**（反射读它配置里的 {@code passiveSoulRegen} ✓ 不猜 ✗）；</li>
 *   <li>⚠ 用**我们自己的 UUID**（{@link #OUR_UUID}）而不是它的 ✗ ——
 *       否则它那句"没有黑暗秘典就 removeModifier(它的UUID)"会把我们加的也撤掉 ✗；</li>
 *   <li>拿不到配置/属性时**什么都不做**并打日志 ✓（绝不让本模组因为对方改版而崩 ✗）。</li>
 * </ul>
 *
 * <p>⚠ 附带一件事：§910i 那个"吞黑暗秘典就授予诡厄 FORBIDDEN 研究"是**当时的猜测** ✗
 * （因为找不到真机制 ✗）⇒ 现在真相明确（就是本条被动 ✓），**已把那一段撤掉** ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class CompendiumBlackBookPassive {

    private static final Logger LOG = LoggerFactory.getLogger("TinkersNewlife/Compendium");

    /** 黑暗秘典的书 id（帕秋莉 NBT `patchouli:book` 的值 ✓） */
    private static final String BLACK_BOOK_ID = "goety:black_book";

    /** 我们自己的修饰符 UUID（**故意**与对方不同 ✓ 免得被它的清理逻辑撤掉 ✗） */
    private static final UUID OUR_UUID = UUID.fromString("7b1e6d2a-0f3c-4a58-9d21-5c8f0a6b41d7");

    private static final String OUR_NAME = "tnl_compendium_black_book_soul_regen";

    /** 反射缓存：属性 与 配置数值（各解一次 ✓） */
    private static Attribute soulRegenAttribute;
    private static double soulRegenAmount = Double.NaN;
    private static boolean reflectTried;

    private CompendiumBlackBookPassive() {}

    /** 反射拿"诡厄遗物"的 SOUL_REGEN 属性 + 它配置里的数值 ✓（拿不到就一直是 NaN ⇒ 本功能自动失效 ✓） */
    private static void resolve(ServerPlayer player) {
        if (reflectTried) return;
        reflectTried = true;
        if (!ModList.get().isLoaded("goeticlegacy")) return;
        try {
            Class<?> modAttrs = Class.forName("com.qoocies.goeticlegacy.registries.ModAttributes");
            Object holder = modAttrs.getField("SOUL_REGEN").get(null);
            Object attr = holder.getClass().getMethod("get").invoke(holder);
            if (attr instanceof Attribute a) soulRegenAttribute = a;

            Class<?> cfg = Class.forName("com.qoocies.goeticlegacy.Config");
            Object passive = cfg.getField("BLACK_BOOK_PASSIVE").get(null);
            Object value = passive.getClass().getField("passiveSoulRegen").get(passive);
            Object raw = value.getClass().getMethod("get").invoke(value);
            if (raw instanceof Double d) soulRegenAmount = d;

            LOG.info("[百宝书] §910r 已读到诡厄遗物的回魂属性/数值：属性={} 数值={}",
                    soulRegenAttribute, soulRegenAmount);
        } catch (Throwable t) {
            LOG.warn("[百宝书] §910r 读取诡厄遗物的回魂属性/配置失败（本功能自动失效）：{}", t.toString());
        }
    }

    /** 玩家身上有没有"吞过黑暗秘典的百宝书" ✓（背包 + 主副手都看 ✓ 按**书 id** 判 ✓ 最稳 ✓） */
    private static boolean hasCompendiumWithBlackBook(ServerPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !(s.getItem() instanceof CompendiumItem)) continue;
            for (String id : CompendiumItem.absorbedItemIds(s)) {
                if (BLACK_BOOK_ID.equals(id)) return true;
            }
            // 老记录可能只存了"物品 id = patchouli:guide_book"（书 id 才是 goety:black_book ✓）
            if (CompendiumItem.hasAbsorbed(s, BLACK_BOOK_ID)) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;

        resolve(player);
        if (soulRegenAttribute == null || Double.isNaN(soulRegenAmount)) return;

        AttributeInstance inst = player.getAttribute(soulRegenAttribute);
        if (inst == null) return;

        boolean shouldHave = hasCompendiumWithBlackBook(player);
        AttributeModifier existing = inst.getModifier(OUR_UUID);
        if (shouldHave && existing == null) {
            inst.addTransientModifier(new AttributeModifier(OUR_UUID, OUR_NAME, soulRegenAmount,
                    AttributeModifier.Operation.ADDITION));
            LOG.info("[百宝书] §910r 已挂上黑暗秘典回魂被动（数值={}，来源=百宝书 ✓）", soulRegenAmount);
        } else if (!shouldHave && existing != null) {
            inst.removeModifier(OUR_UUID);
            LOG.info("[百宝书] §910r 百宝书不再含黑暗秘典 ⇒ 已撤下回魂被动 ✓");
        }
    }
}
