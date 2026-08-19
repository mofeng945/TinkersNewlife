package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModBlocks;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.modifier.DragonboneTrait;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.lang.reflect.Method;
import java.util.List;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DragonsteelHandler {

    private static final ModifierId DRAGONSTEEL_FIRE = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "dragonsteel_fire"));
    private static final ModifierId DRAGONSTEEL_ICE = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "dragonsteel_ice"));
    private static final ModifierId DRAGONSTEEL_LIGHTNING = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "dragonsteel_lightning"));

    private static final float FIRE_EXPLOSION_BASE = 1.5f;
    private static final int FIRE_DURATION_BASE = 60;
    private static final int ICE_DURATION_BASE = 200;
    private static final int LIGHTNING_DISARM_BASE = 60;
    private static final int ICE_LIFETIME = 100;

    // ==================== 辅助方法：获取弹射物对应的武器 ====================
    private static ItemStack getProjectileWeapon(Projectile projectile, Player shooter) {
        // 1. 尝试从弹射物本身获取物品（三叉戟、匠魂标枪等）
        try {
            Method method = projectile.getClass().getMethod("getPickupItem");
            return (ItemStack) method.invoke(projectile);
        } catch (Exception ignored) {}
        try {
            Method method = projectile.getClass().getMethod("getItem");
            return (ItemStack) method.invoke(projectile);
        } catch (Exception ignored) {}

        // 2. 若弹射物无物品，则从玩家主手获取（弓/弩）
        ItemStack mainHand = shooter.getMainHandItem();
        if (!mainHand.isEmpty()) {
            return mainHand;
        }
        // 3. 若主手为空，尝试副手
        ItemStack offHand = shooter.getOffhandItem();
        if (!offHand.isEmpty()) {
            return offHand;
        }
        return ItemStack.EMPTY;
    }

    // ==================== 近战 ====================
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        LivingEntity target = event.getEntity();
        if (player.level().isClientSide) return;

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return;

        ToolStack tool = ToolStack.from(stack);
        if (tool == null) return;
        if (tool.getStats().getContainedStats().isEmpty()) return;

        int baseFireLevel = tool.getModifierLevel(DRAGONSTEEL_FIRE);
        int baseIceLevel = tool.getModifierLevel(DRAGONSTEEL_ICE);
        int baseLightningLevel = tool.getModifierLevel(DRAGONSTEEL_LIGHTNING);

        boolean hasDragonbone = DragonboneTrait.isConductionActive(tool);

        int fireLevel = (hasDragonbone && baseFireLevel > 0) ? baseFireLevel + 1 : baseFireLevel;
        int iceLevel = (hasDragonbone && baseIceLevel > 0) ? baseIceLevel + 1 : baseIceLevel;
        int lightningLevel = (hasDragonbone && baseLightningLevel > 0) ? baseLightningLevel + 1 : baseLightningLevel;

        if (fireLevel > 0) applyFireEffect(player.level(), target, fireLevel);
        if (iceLevel > 0) applyIceEffect(player.level(), target, iceLevel);
        if (lightningLevel > 0) applyLightningEffect(player.level(), target, lightningLevel);
    }

    // ==================== 弹射物 ====================
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult entityHit)) return;
        if (!(entityHit.getEntity() instanceof LivingEntity target)) return;
        if (!(event.getProjectile().getOwner() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        // ★ 获取弹射物对应的武器（标枪本体 或 弓/弩）
        ItemStack stack = getProjectileWeapon(event.getProjectile(), player);
        if (stack.isEmpty()) return;

        ToolStack tool = ToolStack.from(stack);
        if (tool == null) return;
        if (tool.getStats().getContainedStats().isEmpty()) return;

        int baseFireLevel = tool.getModifierLevel(DRAGONSTEEL_FIRE);
        int baseIceLevel = tool.getModifierLevel(DRAGONSTEEL_ICE);
        int baseLightningLevel = tool.getModifierLevel(DRAGONSTEEL_LIGHTNING);

        boolean hasDragonbone = DragonboneTrait.isConductionActive(tool);

        int fireLevel = (hasDragonbone && baseFireLevel > 0) ? baseFireLevel + 1 : baseFireLevel;
        int iceLevel = (hasDragonbone && baseIceLevel > 0) ? baseIceLevel + 1 : baseIceLevel;
        int lightningLevel = (hasDragonbone && baseLightningLevel > 0) ? baseLightningLevel + 1 : baseLightningLevel;

        if (fireLevel > 0) applyFireEffect(player.level(), target, fireLevel);
        if (iceLevel > 0) applyIceEffect(player.level(), target, iceLevel);
        if (lightningLevel > 0) applyLightningEffect(player.level(), target, lightningLevel);
    }

    // ==================== 效果实现（不变） ====================

    private static void applyFireEffect(Level level, LivingEntity target, int levelNum) {
        int fireTicks = FIRE_DURATION_BASE * levelNum;
        target.setSecondsOnFire(fireTicks / 20);

        float explosionPower = FIRE_EXPLOSION_BASE + (levelNum - 1) * 0.5f;
        level.explode(null, target.getX(), target.getY(), target.getZ(),
                explosionPower, false, Level.ExplosionInteraction.NONE);

        int radius = 1 + levelNum;
        BlockPos center = target.blockPosition();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                BlockPos firePos = center.offset(dx, 0, dz);
                if (level.isEmptyBlock(firePos) || level.getBlockState(firePos).canBeReplaced()) {
                    level.setBlockAndUpdate(firePos, Blocks.FIRE.defaultBlockState());
                }
            }
        }

        AABB range = new AABB(center).inflate(radius);
        List<Entity> nearby = level.getEntities(target, range, e -> e instanceof LivingEntity && e != target);
        for (Entity e : nearby) {
            if (e instanceof LivingEntity living) {
                living.setSecondsOnFire((fireTicks / 20) / 2);
            }
        }
    }

    private static void applyIceEffect(Level level, LivingEntity target, int levelNum) {
        int duration = ICE_DURATION_BASE * levelNum;
        if (ModEffects.FROST.get() != null) {
            target.addEffect(new MobEffectInstance(ModEffects.FROST.get(), duration, levelNum - 1));
        }

        BlockPos center = target.blockPosition();
        int count = 8 + levelNum * 2;

        for (int i = 0; i < count; i++) {
            int dx = level.random.nextInt(3) - 1;
            int dz = level.random.nextInt(3) - 1;
            int dy = level.random.nextInt(2);
            BlockPos icePos = center.offset(dx, dy, dz);

            if (level.isEmptyBlock(icePos) || level.getBlockState(icePos).canBeReplaced()) {
                level.setBlockAndUpdate(icePos, ModBlocks.FROST_ICE.get().defaultBlockState());
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.scheduleTick(icePos, ModBlocks.FROST_ICE.get(), ICE_LIFETIME);
                }
            }
        }
    }

    private static void applyLightningEffect(Level level, LivingEntity target, int levelNum) {
        int duration = LIGHTNING_DISARM_BASE * levelNum;
        if (ModEffects.DISARM.get() != null) {
            target.addEffect(new MobEffectInstance(ModEffects.DISARM.get(), duration, 0, false, true));
        }

        if (level instanceof ServerLevel serverLevel) {
            net.minecraft.world.entity.LightningBolt lightning =
                    new net.minecraft.world.entity.LightningBolt(
                            net.minecraft.world.entity.EntityType.LIGHTNING_BOLT, serverLevel);
            lightning.setPos(target.getX(), target.getY(), target.getZ());
            serverLevel.addFreshEntity(lightning);

            BlockPos center = target.blockPosition();
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
    }
}