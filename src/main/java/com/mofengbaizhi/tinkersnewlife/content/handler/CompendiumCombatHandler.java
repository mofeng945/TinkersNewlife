package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.CompendiumItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * <b>帕秋莉的百宝书 —— 继承"无视七咒减伤"</b>（§910n）。
 *
 * <p>用户口径：「**按理来说应当能无视七咒的伤害减半**」＋「**我是说启示之证和倒转之启他们的效果本来就带着**」✓
 * —— 用户是对的 ✓，而且我把证据挖出来了。
 *
 * <h3>神秘遗物自己的规则（反编译 {@code EnigmaticEventHandler#onLivingHurt}，偏移 1880~1957 ✓）</h3>
 * <pre>
 *   Item held = attacker.getMainHandItem().getItem();
 *   boolean exempt = held == EnigmaticItems.THE_TWIST          // 倒转之启
 *                 || held == EnigmaticItems.THE_INFINITUM      // 无止之言
 *                 || held == EnigmaticItems.ELDRITCH_PAN;      // 邪术平底锅
 *   if (!exempt) event.setAmount(amount * CursedRing.monsterDamageDebuff.getValue().asModifierInverted());
 * </pre>
 * ⇒ **这三件是它自己显式豁免的** ✓（注意：启示之证 {@code THE_ACKNOWLEDGMENT} **不在**名单里 ✗）。
 *
 * <h3>这里的做法：照抄它的规则，但把"拿着某件物品"换成"百宝书吞过某件物品"</h3>
 * <ul>
 *   <li>挂在 {@link EventPriority#LOWEST} ✓ ⇒ 跑在神秘遗物**之后** ✓，
 *       此时 {@code getAmount()} 已是被减过的值 ✓ ⇒ 除掉那个系数即还原 ✓；</li>
 *   <li>系数**直接读它自己的配置**（反射：{@code CursedRing.monsterDamageDebuff → getValue() →
 *       asModifierInverted()} ✓ 三个调用名全部来自上面那段字节码 ✓）——**不猜、不写死** ✓；</li>
 *   <li>反射失败时退回 {@link #FALLBACK_FACTOR}（2.0 = 常见的一半 ✓）并打日志 ✓；</li>
 *   <li>条件：玩家主手是百宝书 ✓ ＋ 百宝书**吞过上面三件之一** ✓ ＋ 玩家是受七咒之人 ✓ ＋ 目标是怪物 ✓。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class CompendiumCombatHandler {

    private static final Logger LOG = LoggerFactory.getLogger("TinkersNewlife/Compendium");

    /** 神秘遗物里"自带无视七咒减伤"的那三件（与它字节码里的名单一一对应 ✓） */
    private static final Set<String> EXEMPT_ITEMS = Set.of(
            "enigmaticlegacy:the_twist",        // 倒转之启
            "enigmaticlegacy:the_infinitum",    // 无止之言
            "enigmaticlegacy:eldritch_pan"      // 邪术平底锅
    );

    /** 反射读不到配置时的兜底系数（2.0 = 假设减半 ✓） */
    public static final float FALLBACK_FACTOR = 2.0F;

    /** 诊断只打一次 ✓ */
    private static boolean tnl$logged = false;

    private CompendiumCombatHandler() {}

    /**
     * 反射读神秘遗物第四重咒的实际系数（它减伤时乘的那个数 ✓）。
     *
     * @return 它乘的系数（例如 0.5 ✓）；读不到返回 {@code -1}
     */
    private static float readCurseFactor() {
        if (!ModList.get().isLoaded("enigmaticlegacy")) return -1.0F;
        try {
            Class<?> ring = Class.forName("com.aizistral.enigmaticlegacy.items.CursedRing");
            Object param = ring.getField("monsterDamageDebuff").get(null);
            Object perhaps = param.getClass().getMethod("getValue").invoke(param);
            Object factor = perhaps.getClass().getMethod("asModifierInverted").invoke(perhaps);
            return factor instanceof Float f ? f : -1.0F;
        } catch (Throwable t) {
            LOG.warn("[百宝书] §910n 读取七咒减伤系数失败（改用兜底 {}）：{}", FALLBACK_FACTOR, t.toString());
            return -1.0F;
        }
    }

    /** 百宝书是不是吞过"那三件豁免物品"之一 ✓（按记录里的**物品 id** 判 ✓ 与借属性的口径一致 ✓） */
    private static boolean hasExemptItem(ItemStack compendium) {
        for (String id : CompendiumItem.absorbedItemIds(compendium)) {
            if (EXEMPT_ITEMS.contains(id)) return true;
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;

        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof CompendiumItem)) return;
        if (!hasExemptItem(held)) return;                                 // 没吞过那三件 ⇒ 不插手 ✓
        if (!(event.getEntity() instanceof Monster)) return;              // "对怪物的伤害" ✓
        if (!CompendiumItem.isTheCursedOne(player)) return;               // 没受七咒 ⇒ 本来就没减 ✓

        float applied = readCurseFactor();
        float factor = applied > 0.0F && applied < 1.0F ? applied : 1.0F / FALLBACK_FACTOR;
        float before = event.getAmount();
        event.setAmount(before / factor);                                 // 除掉它减掉的那一份 ⇒ 还原 ✓
        if (!tnl$logged) {
            tnl$logged = true;
            LOG.info("[百宝书] §910n 七咒减伤豁免生效：{} → {}（神秘遗物的系数={}；兜底={}）",
                    before, event.getAmount(), applied, FALLBACK_FACTOR);
        }
    }
}
