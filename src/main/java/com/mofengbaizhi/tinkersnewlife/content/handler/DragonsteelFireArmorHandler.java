package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.util.ArmorModifierHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public class DragonsteelFireArmorHandler {

    private static final String MODIFIER_ID = "dragonsteel_fire_armor";

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        if (!(entity instanceof Player)) return;

        if (ArmorModifierHelper.hasModifierOnArmor(entity, MODIFIER_ID)) {
            // ⭐ 统一被动效果规格：每 1 秒检查、时长 12 秒、剩余 <11 秒时刷新，避免效果图标闪烁
            ArmorModifierHelper.addPassiveEffect(entity, MobEffects.FIRE_RESISTANCE, 0);
        }
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;

        int totalLevel = ArmorModifierHelper.getTotalModifierLevelOnArmor(target, MODIFIER_ID);
        if (totalLevel > 0) {
            // 点燃时长：基础 2 秒，每级 +2 秒
            int fireDuration = 2 + totalLevel * 2;
            attacker.setSecondsOnFire(fireDuration);
        }
    }
}