package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.CompendiumItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * <b>帕秋莉的百宝书 —— 继承"无视七咒减伤"</b>（§910n / §910o）。
 *
 * <p>用户口径：「**按理来说应当能无视七咒的伤害减半**」＋「**我是说启示之证和倒转之启他们的效果本来就带着**」✓
 *
 * <h3>神秘遗物自己的规则（反编译 {@code EnigmaticEventHandler#onLivingHurt}，偏移 1880~1957 ✓）</h3>
 * <pre>
 *   Item held = attacker.getMainHandItem().getItem();
 *   boolean exempt = held == THE_TWIST || held == THE_INFINITUM || held == ELDRITCH_PAN;
 *   if (!exempt) event.setAmount(amount * CursedRing.monsterDamageDebuff.getValue().asModifierInverted());
 * </pre>
 * ⇒ 这三件是它自己显式豁免的 ✓（**启示之证不在名单里** ✗）。
 *
 * <h3>§910o 为什么从 LivingHurtEvent 改到 LivingDamageEvent</h3>
 * 用户实测"没生效" ✗，而日志给出关键线索：满蓄力下实时攻击力 **5.31**（= 9.0 × ≈0.59 ✓）
 * ⇒ **它的减伤确实生效了** ✓，但本处理器那行日志没出现 ✗ ⇒ 说明补回**没走到** ✓。
 * 最可能的原因是**事件顺序**：两边的优先级若是同级，先后就取决于**注册顺序** ✗ 不可靠 ✗。
 * {@link LivingDamageEvent} 在**所有** {@code LivingHurtEvent} 处理完之后才触发 ✓
 * ⇒ 在这里补回**必定在它减伤之后** ✓（减伤是乘法 ⇒ 与护甲减免可交换 ✓ 补回比例不变 ✓）。
 *
 * <h3>做法</h3>
 * <ul>
 *   <li>系数**直接读它自己的配置**（反射 {@code CursedRing.monsterDamageDebuff → getValue() →
 *       asModifierInverted()} ✓ 调用名全部来自上面那段字节码 ✓）—— 不猜、不写死 ✓；
 *       读不到时退回 {@link #FALLBACK_FACTOR}（2.0 ✓）并打日志 ✓；</li>
 *   <li>条件：主手是百宝书 ✓ ＋ 百宝书**吞过那三件豁免物品之一** ✓ ＋ 玩家是受七咒之人 ✓ ＋ 目标是怪物 ✓；</li>
 *   <li>**条件检查也打日志**（§910o ✓）：只打一次，把四个条件的真假列出来 ✓
 *       ⇒ 万一还不生效，一眼能看出卡在哪个条件 ✓（不再"没生效却无线索" ✗）。</li>
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
     * @return 它乘的系数（例如 0.59 ✓）；读不到返回 {@code -1}
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
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;

        ItemStack held = player.getMainHandItem();
        boolean isCompendium = held.getItem() instanceof CompendiumItem;
        boolean exemptHeld = isCompendium && hasExemptItem(held);
        boolean cursed = isCompendium && CompendiumItem.isTheCursedOne(player);

        // §910p ⚠ 它**没有**目标类型判定（反编译偏移 1870~1957 ✓）：
        //   只要"攻击者受七咒" ＋ "手持的不是那三件豁免物品" ⇒ 就乘减伤 ✓
        //   —— `monsterDamageDebuff` 只是个**误导性的配置名** ✗，实际对**任何目标**生效 ✓。
        //   我一开始按名字加了 `Monster` 判断 ⇒ 日志里 `目标是怪物=false` ⇒ 永远不补 ✗（用户实测 ✓）。
        if (isCompendium && !tnl$logged) {
            tnl$logged = true;
            LOG.info("[百宝书] §910p 条件检查：主手百宝书={} 吞过豁免物品={} 是受七咒之人={} ⇒ {}",
                    true, exemptHeld, cursed,
                    (exemptHeld && cursed) ? "补回" : "不补（看前面哪项=false ✗）");
        }

        if (!exemptHeld || !cursed) return;

        float applied = readCurseFactor();
        float factor = applied > 0.0F && applied < 1.0F ? applied : 1.0F / FALLBACK_FACTOR;
        float before = event.getAmount();
        event.setAmount(before / factor);                                 // 除掉它减掉的那一份 ⇒ 还原 ✓
        LOG.info("[百宝书] §910p 七咒减伤豁免生效：{} → {}（神秘遗物的系数={}；兜底={}）",
                before, event.getAmount(), applied, FALLBACK_FACTOR);
    }
}
