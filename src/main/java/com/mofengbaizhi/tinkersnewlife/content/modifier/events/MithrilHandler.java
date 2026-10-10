package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.FocusModifier;
import com.mofengbaizhi.tinkersnewlife.content.modifier.SpellBreakModifier;
import com.mofengbaizhi.tinkersnewlife.integration.irons_spellbooks.IronSpellsSpellAccess;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 材料「秘银」两个需要持续维持的特性（铁魔法联动）。
 *
 * <h2>① 专注（有等级，工具）</h2>
 * 手持时给持有者加 <b>+5% × 等级</b> 的 {@code CAST_TIME_REDUCTION}（法术吟唱缩减）属性 ——
 * 与「无止寒风」「远古庇护」同一套做法（transient 修饰符，每 10 tick 维持、脱手立即移除 ✓）。
 * 「吟唱不会被打断」那半在 {@code mixin.SpellInterruptFocusMixin} 里拦 ✓。
 *
 * <h2>② 破法（无等级）</h2>
 * 物品带此特性时，往它内部刻入 <b>1 级「法术反制」</b>（只在该物品还没有法术容器时写一次 ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MithrilHandler {

    private MithrilHandler() {
    }

    /** 专注：吟唱缩减属性修饰符 ID */
    private static final UUID CAST_SPEED_MODIFIER = UUID.fromString("9d4b7c15-3e82-4a6f-b0d1-7c5e2a91f348");

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (player.tickCount % 10 != 0) return;

        // ① 专注：吟唱缩减
        Attribute castReduction = IronSpellsSpellAccess.attribute("CAST_TIME_REDUCTION");
        if (castReduction != null) {
            AttributeInstance inst = player.getAttribute(castReduction);
            if (inst != null) {
                inst.removeModifier(CAST_SPEED_MODIFIER);
                int level = FocusModifier.heldLevel(player);
                if (level > 0) {
                    inst.addTransientModifier(new AttributeModifier(CAST_SPEED_MODIFIER,
                            "focus_cast_speed", FocusModifier.CAST_SPEED_PER_LEVEL * level,
                            AttributeModifier.Operation.ADDITION));
                }
            }
        }

        // ② 破法：自带 1 级法术反制（主手/副手/护甲都覆盖，已是容器则跳过）
        for (var stack : new net.minecraft.world.item.ItemStack[]{
                player.getMainHandItem(), player.getOffhandItem()}) {
            if (SpellBreakModifier.has(stack)) {
                IronSpellsSpellAccess.ensureInscribed(stack, SpellBreakModifier.INSCRIBED_SPELL,
                        SpellBreakModifier.INSCRIBED_LEVEL, SpellBreakModifier.CONTAINER_SLOTS);
            }
        }
        for (var stack : player.getArmorSlots()) {
            if (SpellBreakModifier.has(stack)) {
                IronSpellsSpellAccess.ensureInscribed(stack, SpellBreakModifier.INSCRIBED_SPELL,
                        SpellBreakModifier.INSCRIBED_LEVEL, SpellBreakModifier.CONTAINER_SLOTS);
            }
        }
    }
}
