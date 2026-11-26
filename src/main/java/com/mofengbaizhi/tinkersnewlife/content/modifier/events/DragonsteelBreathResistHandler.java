package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.util.ArmorModifierHelper;
import com.mofengbaizhi.tinkersnewlife.integration.iceandfire.IceAndFireDamage;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * §802 龙钢／怂怖钢盔甲对**冰火龙息**的抗性（★用户口径 ✓）。
 *
 * <pre>
 *   龙炎钢盔甲 dragonsteel_fire_armor      ⇒ ★免疫【火】龙息 ✓；冰／雷 ⇒ ★伤害 ×0.5 ✓
 *   龙霜钢盔甲 dragonsteel_ice_armor       ⇒ ★免疫【冰】龙息 ✓；火／雷 ⇒ ★伤害 ×0.5 ✓
 *   龙霆钢盔甲 dragonsteel_lightning_armor ⇒ ★免疫【雷】龙息 ✓；火／冰 ⇒ ★伤害 ×0.5 ✓
 *   ★特例：怂怖钢盔甲 dreadsteel_armor       ⇒ ★★免疫全部三种龙息 ✓
 * </pre>
 *
 * <p>★叠加口径：任一部位带「免疫该属性」的特性 ⇒ ★整体免疫（取最强 ✓）；
 * 否则只要有任一部位带「另两种」之一 ⇒ ★整体减半 ✓。
 *
 * <p>★两道闸门：{@link LivingAttackEvent} 负责**完全免疫**（★取消 ⇒ 不掉血 ✓）；
 * {@link LivingHurtEvent} 负责**减半**（★与无下限同样的两道闸门结构 ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public class DragonsteelBreathResistHandler {

    private static final String[] ARMOR_IDS = {
            "dragonsteel_fire_armor",       // ★对应 FireAndIceDamage.FIRE ✓
            "dragonsteel_ice_armor",        // ★ICE ✓
            "dragonsteel_lightning_armor"   // ★LIGHTNING ✓
    };
    private static final String DREADSTEEL_ARMOR = "dreadsteel_armor";

    /** ★减半系数（★用户口径：非对应属性减免 50% ✓） */
    private static final float OTHER_MULTIPLIER = 0.5F;

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) {
            return;
        }
        int kind = IceAndFireDamage.kindOf(event.getSource());
        if (kind < 0) {
            return;
        }
        if (isImmune(target, kind)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) {
            return;
        }
        int kind = IceAndFireDamage.kindOf(event.getSource());
        if (kind < 0) {
            return;
        }
        if (isImmune(target, kind)) {
            event.setAmount(0.0F);
            event.setCanceled(true);
            return;
        }
        if (isHalved(target, kind)) {
            event.setAmount(event.getAmount() * OTHER_MULTIPLIER);
        }
    }

    /** ★免疫：怂怖钢（全免）或 对应属性那一套 ✓ */
    private static boolean isImmune(LivingEntity target, int kind) {
        if (ArmorModifierHelper.hasModifierOnArmor(target, DREADSTEEL_ARMOR)) {
            return true;
        }
        return ArmorModifierHelper.hasModifierOnArmor(target, ARMOR_IDS[kind]);
    }

    /** ★减半：穿了另两种属性之一的那一套 ✓ */
    private static boolean isHalved(LivingEntity target, int kind) {
        for (int i = 0; i < ARMOR_IDS.length; i++) {
            if (i == kind) {
                continue;
            }
            if (ArmorModifierHelper.hasModifierOnArmor(target, ARMOR_IDS[i])) {
                return true;
            }
        }
        return false;
    }
}
