package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.util.ArmorModifierHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 词条·抗魔（黑暗金属护甲部件自带）：穿戴者受到魔法伤害（{@link DamageSource#isMagic()}）时，
 * 把 {@code 5%×等级 + 1.5} 点魔法伤害转化为物理伤害——护甲在伤害结算前已按原版流程减免物理部分，
 * 此处等效为直接减免等量魔法伤害（护甲/魔抗词条材料等级一般 1 级）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DarkMetalMagicResistHandler {

    /** 词条 id（ArmorModifierHelper 按字符串查找） */
    private static final String MODIFIER_ID = "dark_metal_magic_resist";

    /** 原版魔法伤害 tag（DamageTypeTags 无 IS_MAGIC 常量，显式建 TagKey） */
    private static final net.minecraft.tags.TagKey<net.minecraft.world.damagesource.DamageType> MAGIC =
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                    new net.minecraft.resources.ResourceLocation("minecraft", "is_magic"));

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        DamageSource source = event.getSource();
        if (source == null || !source.is(MAGIC)) return;   // 仅魔法伤害
        LivingEntity victim = event.getEntity();

        int lv = ArmorModifierHelper.getTotalModifierLevelOnArmor(victim, MODIFIER_ID);
        if (lv <= 0) return;

        float amount = event.getAmount();
        float converted = amount * 0.05f * lv + 1.5f;      // 5%×等级 + 1.5 点 → 转为物理（此处直接减免）
        event.setAmount(Math.max(0f, amount - converted));
    }
}
