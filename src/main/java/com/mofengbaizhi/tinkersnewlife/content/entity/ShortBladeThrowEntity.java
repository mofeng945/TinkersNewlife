package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import com.mofengbaizhi.tinkersnewlife.content.item.LongShortBladeItem;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

/**
 * ⭐ §1124 <b>长短刃 · 短刀投掷的投射物</b>（用户口径 ✓）—— 由
 * {@code LongShortBladeHandler#throwShortBlade} 在"短刀形态长按右键松手"时创建 ✓。
 *
 * <h2>为什么继承 {@link ThrowableItemProjectile}</h2>
 * ⚠ 契约要求那个构造器签名（{@code (EntityType, LivingEntity, Level)} ✓）与
 * {@code setItem} / {@code shootFromRotation} 两个方法 ✓ —— 三者**原版这个类全都自带** ✓
 * （反编译实证 ✓ `ThrowableItemProjectile(EntityType, LivingEntity, Level)` 是 public ✓、
 * {@code setItem} 是 public ✓、{@code shootFromRotation} 来自 {@code Projectile} ✓）
 * ⇒ ⭐ 直接继承即可 ✓ 不用自己写弹道/同步/NBT ✓。
 * <p>⭐ 再实现 {@link ItemSupplier} ⇒ 客户端可以直接用原版 {@code ThrownItemRenderer}
 * 把**手里那把武器**画出来 ✓（与石弹 {@code StoneShotEntity} 同一套做法 ✓）。
 *
 * <h2>命中逻辑（用户口径 ✓）</h2>
 * <ol>
 *   <li>先对**直接命中的实体**造成一次<b>投掷伤害</b> ✓（伤害 = 该武器的攻击力 ×
 *       {@link #DIRECT_HIT_MULTIPLIER} ✓）；</li>
 *   <li>然后在命中点放一次<b>半径 3 格的非破坏爆炸</b> ✓
 *       —— 用 {@code Level.ExplosionInteraction.NONE} ✓（用户口径「非破坏方块爆炸」✓，
 *       半径取 {@link LongShortBladeItem#THROW_EXPLOSION_RADIUS} ✓ 不写死 ✗）；</li>
 *   <li>命中/落地后**移除自身** ✓；飞行途中每 {@value #TRAIL_INTERVAL_TICKS} tick 掉一点粒子 ✓。</li>
 * </ol>
 *
 * <h2>⚠ 两条刻意的设计（别改错 ✗）</h2>
 * <ul>
 *   <li>⭐ <b>投掷不会把武器本体丢出去</b> ✓ —— 本实体只拿到物品栈的**副本**（{@code stack.copy()} ✓），
 *       玩家手里那把刀**不离开玩家** ✓（用户口径 ✓「投掷只是表现」✓）；</li>
 *   <li>⭐ <b>爆炸的"来源实体"传的是投掷者</b>（不是本投射物 ✓）—— 这样爆炸击杀**算玩家的** ✓
 *       （战利品表的 {@code killed_by_player} 才成立 ✓ 否则会出现"炸死了却什么都不掉"✗）。</li>
 * </ul>
 */
public class ShortBladeThrowEntity extends ThrowableItemProjectile implements ItemSupplier {

    /**
     * ⭐ 投掷直接命中的伤害倍率（× 该武器攻击力）✓。
     * <p>⚠ <b>这个数字是我定的</b> ✗ —— 用户口径只说了"造成一次投掷伤害"、没给数值 ✓
     * （⚠ 与突刺的 1.6 倍不同 ✓ 投掷应该比挥砍轻 ✓）⇒ 若要调，改这一个常量即可 ✓。
     */
    private static final float DIRECT_HIT_MULTIPLIER = 1.0F;

    /** ⚠ 拿不到武器攻击力时的兜底（照 {@code LongShortBladeItem#thrust} 里那个 10.0F ✓ 同一口径 ✓） */
    private static final float FALLBACK_ATTACK_DAMAGE = 10.0F;

    /** 命中点的粒子量 ✓ */
    private static final int IMPACT_PARTICLES = 16;

    /** 飞行途中掉粒子的间隔（tick ✓） */
    private static final int TRAIL_INTERVAL_TICKS = 2;

    /** ⚠ 防"同一 tick 里被结算两次"（爆炸只放一次 ✓）—— 只在内存里 ✓ 实体随即被移除 ✓ */
    private boolean detonated = false;

    /**
     * ⭐ <b>契约构造器</b> ✓ —— {@code LongShortBladeHandler#throwShortBlade} 用的就是它 ✓
     * （{@code new ShortBladeThrowEntity(ModEntities.SHORT_BLADE_THROW.get(), player, sl)} ✓）。
     */
    public ShortBladeThrowEntity(EntityType<? extends ShortBladeThrowEntity> type, LivingEntity shooter, Level level) {
        super(type, shooter, level);
    }

    /** 反序列化用的常规构造器（{@code EntityType} 重建实体时走它 ✓） */
    public ShortBladeThrowEntity(EntityType<? extends ShortBladeThrowEntity> type, Level level) {
        super(type, level);
    }

    /**
     * ⚠ 物品栈为空时的兜底物品 ✓（正常流程里 {@code throwShortBlade} 会先 {@code setItem(stack.copy())} ✓
     * ⇒ 这里几乎只有"NBT 丢了"那种异常情况才会用到 ✓）。
     */
    @Override
    protected Item getDefaultItem() {
        try {
            Item item = ModItems.LONG_SHORT_BLADE.get();
            if (item != null) {
                return item;
            }
        } catch (Throwable ignored) {
            // 注册表还没就绪等 ⇒ 走下面的兜底 ✓
        }
        return Items.IRON_SWORD;
    }

    /** 飞行途中的轻微尾迹 ✓（⚠ 只在服务端发 ✓ 两边都发会变双份 ✗） */
    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || this.isRemoved()) {
            return;
        }
        if (this.tickCount % TRAIL_INTERVAL_TICKS == 0 && this.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY(), this.getZ(),
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /**
     * 命中实体 ✓：先给**直接命中者**一次投掷伤害 ✓，再在命中点炸 ✓。
     */
    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level().isClientSide) {
            return;
        }
        Entity target = result.getEntity();
        if (target instanceof LivingEntity living && living.isAlive()) {
            // ⚠ 清无敌帧 ⇒ 否则刚被打过的目标会吃掉这一下 ✗（本仓多个伤害路径都这么做 ✓）
            living.invulnerableTime = 0;
            living.hurt(this.damageSources().thrown(this, this.getOwner()), directHitDamage());
        }
        detonate(result.getLocation());
    }

    /** 命中方块 ✓：不造成伤害，直接炸 ✓ */
    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (this.level().isClientSide) {
            return;
        }
        detonate(result.getLocation());
    }

    /**
     * ⭐ 在命中点放一次**半径 3 格的非破坏爆炸** ✓ 然后移除自身 ✓。
     *
     * <p>⚠ {@code Level.ExplosionInteraction.NONE} ＝ **不破坏方块** ✓ 但仍会伤到生物 ✓（用户口径 ✓）。
     * ⚠ 来源实体传**投掷者** ✗ 不是本投射物 —— 这样爆炸击杀**算玩家的** ✓（掉落/经验才正常 ✓）。
     */
    private void detonate(Vec3 at) {
        if (this.detonated) {
            return;
        }
        this.detonated = true;
        try {
            Entity source = this.getOwner() != null ? this.getOwner() : this;
            this.level().explode(source, at.x, at.y, at.z,
                    LongShortBladeItem.THROW_EXPLOSION_RADIUS, Level.ExplosionInteraction.NONE);
            if (this.level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z,
                        IMPACT_PARTICLES, 0.25D, 0.25D, 0.25D, 0.08D);
            }
        } catch (Throwable ignored) {
            // ⚠ 爆炸/粒子出问题也绝不能连累游戏 ✓（本仓一贯口径 ✓）
        } finally {
            this.discard();
        }
    }

    /** 这一发的投掷伤害 ＝ 该武器攻击力 × {@link #DIRECT_HIT_MULTIPLIER} ✓（拿不到就用兜底 ✓） */
    private float directHitDamage() {
        try {
            ItemStack stack = this.getItem();
            if (!stack.isEmpty()) {
                ToolStack tool = ToolHelper.getToolStack(stack);
                if (tool != null) {
                    return Math.max(1.0F, tool.getStats().get(ToolStats.ATTACK_DAMAGE) * DIRECT_HIT_MULTIPLIER);
                }
            }
        } catch (Throwable ignored) {
            // 落到下面的兜底 ✓
        }
        return FALLBACK_ATTACK_DAMAGE * DIRECT_HIT_MULTIPLIER;
    }
}
