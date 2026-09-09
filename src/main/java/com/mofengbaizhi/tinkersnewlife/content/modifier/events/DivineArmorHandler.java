package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.content.modifier.GodrealmOverstepModifier;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * 神灵金盔甲特性结算器：
 * <ul>
 *   <li>越过神域：穿戴神灵金盔甲（含该特性）时免疫爆炸/火焰/魔法伤害（参照原版 IInvulnerableItem）。</li>
 *   <li>天地所铸 / 灵魂折扣：分别需 TCon 护甲耐久钩子 / goety 灵魂折扣集成，见各 modifier 注释（后续补）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = com.mofengbaizhi.tinkersnewlife.TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DivineArmorHandler {

    private DivineArmorHandler() {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity wearer = event.getEntity();
        if (wearer.level().isClientSide) return;
        for (ItemStack armor : wearer.getArmorSlots()) {
            if (armor.isEmpty()) continue;
            ToolStack tool = ToolHelper.getToolStack(armor);
            if (tool == null || tool.getModifierLevel(GodrealmOverstepModifier.ID) <= 0) continue;
            DamageSource src = event.getSource();
            if (src.is(DamageTypeTags.IS_FIRE) || src.is(DamageTypeTags.IS_EXPLOSION)
                    || isMagic(src)) {
                event.setCanceled(true);
            }
            break;
        }
    }

    private static boolean isMagic(DamageSource src) {
        java.util.Optional<net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType>> key =
                src.typeHolder().unwrapKey();
        if (key.isEmpty()) return false;
        String p = key.get().location().getPath();
        return p.equals("magic") || p.equals("indirect_magic") || p.equals("sonic_boom");
    }
}
