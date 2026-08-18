package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.modifier.util.ArmorModifierHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public class DragonsteelIceArmorHandler {

    private static final String MODIFIER_ID = "dragonsteel_ice_armor";

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        if (!(entity instanceof Player player)) return;
        if (!ArmorModifierHelper.hasModifierOnArmor(entity, MODIFIER_ID)) return;

        // 清除冻结刻度
        if (player.getTicksFrozen() > 0) {
            player.setTicksFrozen(0);
        }

        // 水面结冰（脚下）
        Level level = player.level();
        BlockPos pos = player.blockPosition().below();
        if (level.getBlockState(pos).getFluidState().is(FluidTags.WATER)) {
            level.setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState());
        }
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;

        int totalLevel = ArmorModifierHelper.getTotalModifierLevelOnArmor(target, MODIFIER_ID);
        if (totalLevel > 0) {
            // 霜冻时长：基础 40 tick（2秒），每级 +20 tick（1秒）
            int frostDuration = 40 + totalLevel * 20;
            if (ModEffects.FROST.get() != null) {
                attacker.addEffect(new MobEffectInstance(
                        ModEffects.FROST.get(), frostDuration, 0));
            }
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        DamageSource source = event.getSource();

        if (source.is(DamageTypeTags.IS_FREEZING)) {
            if (ArmorModifierHelper.hasModifierOnArmor(entity, MODIFIER_ID)) {
                event.setCanceled(true);
            }
        }
    }
}