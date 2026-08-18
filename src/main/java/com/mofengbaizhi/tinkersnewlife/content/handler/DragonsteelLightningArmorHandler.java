package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.modifier.util.ArmorModifierHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public class DragonsteelLightningArmorHandler {

    private static final String MODIFIER_ID = "dragonsteel_lightning_armor";
    private static final int NIGHT_VISION_DURATION = 220;

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        if (!(entity instanceof Player)) return;

        int totalLevel = ArmorModifierHelper.getTotalModifierLevelOnArmor(entity, MODIFIER_ID);
        if (totalLevel <= 0) return;

        // 抗性提升（等级 × 等级）
        entity.addEffect(new MobEffectInstance(
                MobEffects.DAMAGE_RESISTANCE,
                NIGHT_VISION_DURATION,
                totalLevel - 1,
                false, false, true
        ));

        // 夜视
        entity.addEffect(new MobEffectInstance(
                MobEffects.NIGHT_VISION,
                NIGHT_VISION_DURATION,
                0,
                false, false, true
        ));
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;

        int totalLevel = ArmorModifierHelper.getTotalModifierLevelOnArmor(target, MODIFIER_ID);
        if (totalLevel <= 0) return;

        Level level = target.level();
        if (level instanceof ServerLevel serverLevel) {
            // 召唤闪电
            net.minecraft.world.entity.LightningBolt lightning =
                    new net.minecraft.world.entity.LightningBolt(
                            net.minecraft.world.entity.EntityType.LIGHTNING_BOLT,
                            serverLevel
                    );
            lightning.setPos(attacker.getX(), attacker.getY(), attacker.getZ());
            serverLevel.addFreshEntity(lightning);

            // 清除闪电周围火焰
            BlockPos center = attacker.blockPosition();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos pos = center.offset(dx, dy, dz);
                        if (serverLevel.getBlockState(pos).getBlock() == Blocks.FIRE) {
                            serverLevel.removeBlock(pos, false);
                        }
                    }
                }
            }
        }

        // 缴械时长：基础 20 tick（1秒），每级 +10 tick（0.5秒）
        int disarmDuration = 20 + totalLevel * 10;
        if (ModEffects.DISARM.get() != null) {
            attacker.addEffect(new MobEffectInstance(
                    ModEffects.DISARM.get(),
                    disarmDuration,
                    0,
                    false, true
            ));
        }
    }

    private static void removeFireAround(ServerLevel level, BlockPos center) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (level.getBlockState(pos).getBlock() == Blocks.FIRE) {
                        level.removeBlock(pos, false);
                    }
                }
            }
        }
    }
}