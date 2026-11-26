package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.InscriptionModifier;
import com.mofengbaizhi.tinkersnewlife.integration.irons_spellbooks.IronSpellsSpellAccess;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 特性「<b>刻印</b>」结算器（铁魔法联动，见 {@link InscriptionModifier}）：
 * 手持 / 穿戴带刻印的物品时，给持有者维持 <b>+10% × 件数</b> 的 {@code SPELL_POWER}
 * （法术强度）属性 ✓。
 *
 * <ul>
 *   <li>transient 修饰符，每 10 tick 重算一次 → 脱手 / 脱下立即移除 ✓；</li>
 *   <li>用**独立 UUID**：与「奥法支配」（+100%）「万法归一」（法力上限）互不覆盖、可叠加 ✓；</li>
 *   <li>铁魔法不在场时属性取不到 → 静默跳过（特性本身也来自铁魔法联动材料）✓。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class InscriptionHandler {

    private InscriptionHandler() {
    }

    /** 刻印的法术强度修饰符 id（与奥法支配的不同 → 两者叠加 ✓） */
    private static final UUID POWER_MODIFIER_ID = UUID.fromString("7c1d5a84-2f60-4b93-9e15-8ad3c07f2b41");

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 10 != 0) return;

        int count = InscriptionModifier.countWorn(entity);
        Attribute attr = IronSpellsSpellAccess.attribute("SPELL_POWER");
        if (attr == null) return;
        AttributeInstance instance = entity.getAttribute(attr);
        if (instance == null) return;

        instance.removeModifier(POWER_MODIFIER_ID);
        if (count > 0) {
            instance.addTransientModifier(new AttributeModifier(POWER_MODIFIER_ID, "inscription_spell_power",
                    InscriptionModifier.POWER_PER_ITEM * count, AttributeModifier.Operation.ADDITION));
        }
    }
}
