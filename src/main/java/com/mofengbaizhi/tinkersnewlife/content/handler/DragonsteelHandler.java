package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModBlocks;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.modifier.DragonboneTrait;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Explosion;
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

import java.util.List;

/**
 * 龙钢三系武器词条（dragonsteel_fire / dragonsteel_ice / dragonsteel_lightning）近战/弹射命中效果。
 * <p>
 * ⚠️ 2026-09 修改：龙炎（爆炸）与龙霆（闪电）效果<b>不再伤到持有者自己</b>：
 * <ul>
 *   <li>龙炎爆炸：不用 vanilla {@code level.explode}（无差别范围伤害会炸到贴脸近战自己），
 *       改为「爆炸视觉/音效 + 手动范围结算」，结算时<b>排除攻击者本人</b>（伤害用爆炸伤害源）。</li>
 *   <li>龙霆闪电：不用 vanilla {@link LightningBolt} 落雷（落雷会随机劈附近实体含贴脸自己），
 *       改为「纯视觉闪电（{@code setVisualOnly}）+ 只对命中目标 {@code thunderHit}」——
 *       雷只劈目标，绝不波及持有者。</li>
 * </ul>
 */
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

    /**
     * ⭐ §1118m <b>递归闸门</b> ✓ —— 用户实测：「**我锁定问题是龙炎特性，以前也出这样的问题**」✓
     *
     * <h2>根因（读代码确证 ✓）</h2>
     * 龙炎爆炸是**手动结算**的 ✓（`applyFireEffect` 里 `living.hurt(explosionSource, …)` ✓），
     * 而那个爆炸伤害源是 `level.damageSources().explosion(new Explosion(level, attacker, …))` ✓
     * ⇒ **`source.getEntity()` 仍然是攻击者** ✗ ⇒ `hurt` 会**再发一次 `LivingAttackEvent`** ✗
     * ⇒ {@link #onLivingAttack} 又用同一把龙钢武器取到 tool ✗ ⇒ **又炸一次** ✗
     * ⇒ **连环爆炸 / 递归** ⇒ `StackOverflowError` ✗ ⇒ 事件处理器抛异常
     * ⇒ Forge `EventBus.handleException` 记录它时撞上本整合包的 **log4j 类加载冲突** ✗
     * ⇒ **整个服务端 tick 崩掉** ✓（与备忘录 §1014 / §1015 记的是**同一个坑** ✓ 也是用户说"以前也出过"的原因 ✓）。
     *
     * <h2>修法</h2>
     * 在"我们自己的龙钢效果结算"期间把本标志置真 ✓ ⇒ 期间到达的攻击事件**一律忽略** ✓
     * （`try/finally` 保证复位 ✓）。⚠ 注意：爆炸**该伤的敌人照旧伤** ✓ ——
     * 我们只是不再让"我们自己造成的伤害"**反过来再触发自己的词条** ✓✓。
     */
    private static boolean applyingDragonsteel = false;

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        // §1118m 正在结算我们自己的龙钢效果 ⇒ 忽略这次攻击（否则龙炎爆炸会无限自我触发 ✗）
        if (applyingDragonsteel) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (attacker.level().isClientSide) return;
        LivingEntity target = event.getEntity();
        if (target == attacker) return;

        // ⭐ 统一取工具：玩家近战/弹射双路径+咒力核心兜底；怪物只查主手
        ToolStack tool = ToolHelper.getCombatToolWith(event.getSource(), attacker,
                DRAGONSTEEL_FIRE, DRAGONSTEEL_ICE, DRAGONSTEEL_LIGHTNING);
        if (tool == null) return;

        applyDragonsteelEffects(attacker.level(), attacker, target, tool);
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult entityHit)) return;
        if (!(entityHit.getEntity() instanceof LivingEntity target)) return;
        if (!(event.getProjectile().getOwner() instanceof LivingEntity attacker)) return;
        if (attacker.level().isClientSide) return;

        // ⭐ 统一取工具（弹射路径 + 校验），主手无武器时兜底取佩戴的咒力核心
        ToolStack tool = ToolHelper.getCombatToolWith(event.getProjectile(), attacker,
                DRAGONSTEEL_FIRE, DRAGONSTEEL_ICE, DRAGONSTEEL_LIGHTNING);
        if (tool == null) return;

        applyDragonsteelEffects(attacker.level(), attacker, target, tool);
    }

    /** 根据工具上的龙钢三系特性等级施加效果（近战/弹射共用） */
    private static void applyDragonsteelEffects(Level level, LivingEntity attacker, LivingEntity target, ToolStack tool) {
        int baseFireLevel = ToolHelper.getActiveModifierLevel(tool, DRAGONSTEEL_FIRE);
        int baseIceLevel = ToolHelper.getActiveModifierLevel(tool, DRAGONSTEEL_ICE);
        int baseLightningLevel = ToolHelper.getActiveModifierLevel(tool, DRAGONSTEEL_LIGHTNING);

        boolean hasDragonbone = DragonboneTrait.isConductionActive(tool);

        int fireLevel = (hasDragonbone && baseFireLevel > 0) ? baseFireLevel + 1 : baseFireLevel;
        int iceLevel = (hasDragonbone && baseIceLevel > 0) ? baseIceLevel + 1 : baseIceLevel;
        int lightningLevel = (hasDragonbone && baseLightningLevel > 0) ? baseLightningLevel + 1 : baseLightningLevel;

        // ⭐ §1118m 递归闸门：整个"施加效果"过程置真 ✓ ⇒ 期间由我们造成的伤害（爆炸/闪电 ✓）
        //   再触发 LivingAttackEvent 时会被 onLivingAttack 直接忽略 ✓ 不再连环自我触发 ✓
        boolean prevGuard = applyingDragonsteel;
        applyingDragonsteel = true;
        try {
            if (fireLevel > 0) applyFireEffect(level, attacker, target, fireLevel);
            if (iceLevel > 0) applyIceEffect(level, attacker, target, iceLevel);
            if (lightningLevel > 0) applyLightningEffect(level, attacker, target, lightningLevel);
        } finally {
            applyingDragonsteel = prevGuard;
        }
    }

    // ===== 效果实现（爆炸/闪电均不伤持有者自己） =====

    /**
     * 龙炎：点燃目标 + 目标周围范围爆炸。
     * ⚠️ 不再用 vanilla level.explode（会炸到贴脸近战的自己），改为视觉爆炸 +
     * 手动范围结算（爆炸伤害源），结算排除攻击者本人。
     * ⚠️ 不在目标脚下放置真实火方块（火会烧毁击杀后的掉落物），改撒火焰粒子做视觉。
     */
    private static void applyFireEffect(Level level, LivingEntity attacker, LivingEntity target, int levelNum) {
        int fireTicks = FIRE_DURATION_BASE * levelNum;
        target.setSecondsOnFire(fireTicks / 20);

        float explosionPower = FIRE_EXPLOSION_BASE + (levelNum - 1) * 0.5f;
        double radius = explosionPower;

        // 视觉 + 音效（模拟原版爆炸，不生成真实火方块以免烧掉落物）
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                    target.getX(), target.getY(), target.getZ(), 1, 0, 0, 0, 0);
            // 火焰粒子模拟爆发视觉（不放置 FIRE 方块）
            serverLevel.sendParticles(ParticleTypes.FLAME,
                    target.getX(), target.getY() + 0.5, target.getZ(),
                    20 + levelNum * 10, radius, 0.5, radius, 0.02);
            serverLevel.sendParticles(ParticleTypes.LAVA,
                    target.getX(), target.getY() + 0.2, target.getZ(),
                    6 + levelNum * 2, 0.8, 0.3, 0.8, 0.05);
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        // 手动爆炸结算：范围内除攻击者自己（与目标自身）外的实体受爆炸伤害
        DamageSource explosionSource = level.damageSources().explosion(
                new Explosion(level, attacker, target.getX(), target.getY(), target.getZ(),
                        explosionPower, false, Explosion.BlockInteraction.KEEP));
        BlockPos center = target.blockPosition();
        AABB range = new AABB(center).inflate(radius);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, range,
                e -> e != attacker && e != target && e.isAlive());
        for (Entity e : nearby) {
            if (!(e instanceof LivingEntity living)) continue;
            double d = Math.sqrt(living.distanceToSqr(target.getX(), target.getY(), target.getZ()));
            if (d > radius) continue;
            // 距离衰减的爆炸伤害（与 vanilla 爆炸观感一致）
            float f = (float) (1.0 - d / radius);
            living.hurt(explosionSource, f * f * 8.0f);
            living.setSecondsOnFire((fireTicks / 20) / 2);
        }
    }

    private static void applyIceEffect(Level level, LivingEntity attacker, LivingEntity target, int levelNum) {
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

    /**
     * 龙霆：缴械目标 + 雷击目标。
     * ⚠️ 不再用 vanilla LightningBolt 实体落雷（其 thunderHit 会波及范围内随机实体，含贴脸近战自己），
     * 改为「纯视觉闪电（setVisualOnly）+ 手动只对命中目标 thunderHit」——雷只劈目标，绝不伤持有者。
     */
    private static void applyLightningEffect(Level level, LivingEntity attacker, LivingEntity target, int levelNum) {
        int duration = LIGHTNING_DISARM_BASE * levelNum;
        if (ModEffects.DISARM.get() != null) {
            target.addEffect(new MobEffectInstance(ModEffects.DISARM.get(), duration, 0, false, true));
        }

        if (level instanceof ServerLevel serverLevel) {
            // 纯视觉闪电（不产生原版随机落雷伤害）
            LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, serverLevel);
            bolt.setVisualOnly(true);
            bolt.setPos(target.getX(), target.getY(), target.getZ());
            serverLevel.addFreshEntity(bolt);

            // 手动雷击：只劈命中目标（伤害源 = 闪电，5 点 + 点燃，与原版落雷语义一致）
            if (target.isAlive()) {
                target.thunderHit(serverLevel, bolt);
            }

            // 清除闪电周围火焰
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
