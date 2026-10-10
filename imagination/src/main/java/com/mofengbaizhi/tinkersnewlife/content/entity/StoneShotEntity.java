package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.content.handler.KineticImpactHandler;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * <b>石弹</b>（§983 弹弓专用弹射物）。
 *
 * <p>做法：继承 {@link AbstractArrow} ⇒ 白拿原版箭矢的**弹道/插地/穿透/暴击/着火**等逻辑 ✓，
 * 也就自动接上匠魂的远程改装（{@code ModifierHooks.PROJECTILE_LAUNCH} 那 7 参数钩子要的就是
 * {@code AbstractArrow} ✓）；渲染则复用它实现的 {@link ItemSupplier} ＋ 原版
 * {@code ThrownItemRenderer}（画成飞出去的石头 ✓ 不用自己写渲染器 ✓）。
 *
 * <h2>命中逻辑（用户口径）</h2>
 * <ol>
 *   <li><b>普通命中</b>：交给 {@code super.onHitEntity} ⇒ 原版箭矢那套"速度 × 基础伤害"结算 ✓；</li>
 *   <li><b>命中"正在滞空"的目标</b> ⇒ 额外**强制给它一个向下的拉力**（{@link KineticImpactHandler#pullDown}）✓
 *       ＋ 打标记：等它**撞到地面/落水/攀爬物**时结算**动能伤害** ✓
 *       —— 该伤害用本模组自己的 {@code tinkersnewlife:kinetic} 源（带
 *       {@code #minecraft:bypasses_invulnerability} 标签 ✓）⇒ **创造模式玩家也会被打到** ✓（用户点名要求 ✓）。</li>
 *   <li><b>落地</b>（§1038 用户口径「石子射出去落地时产生石头破碎粒子然后消失」）⇒ 撞到方块就按弹药材质
 *       喷一次**方块破坏粒子**（圆石弹喷圆石碎粒 ✓），然后**直接消失** ⇒ 不再插在地上等人捡 ✓。</li>
 * </ol>
 */
public class StoneShotEntity extends AbstractArrow implements ItemSupplier {

    /** 弹药（射出去的到底哪块石头 —— 用于渲染、命中结算、以及落地碎掉的粒子材质） */
    private ItemStack ammo = ItemStack.EMPTY;
    /** 这一发的"基础动能"（由弹弓的面板伤害决定 ✓） */
    private float impactDamage = 2.0F;

    private static final String TAG_AMMO = "Ammo";
    private static final String TAG_IMPACT = "ImpactDamage";

    /** §1038 落地碎裂的粒子数量 / 扩散半径 / 初速度（口径照原版"挖掉方块"那套粒子 ✓） */
    private static final int SHATTER_PARTICLES = 24;
    private static final double SHATTER_SPREAD = 0.15D;
    private static final double SHATTER_SPEED = 0.12D;

    public StoneShotEntity(EntityType<? extends StoneShotEntity> type, Level level) {
        super(type, level);
        this.pickup = AbstractArrow.Pickup.ALLOWED;   // 石头便宜 ⇒ 允许捡回来 ✓
    }

    public StoneShotEntity(Level level, LivingEntity shooter, ItemStack ammo, float impactDamage) {
        super(ModEntities.STONE_SHOT.get(), shooter, level);
        this.ammo = ammo;
        this.impactDamage = impactDamage;
        this.setBaseDamage(impactDamage);             // 让原版那套"速度 × baseDamage"生效 ✓
        this.pickup = AbstractArrow.Pickup.ALLOWED;
    }

    @Override
    public ItemStack getItem() {
        return getPickupItem();
    }

    @Override
    protected ItemStack getPickupItem() {
        return this.ammo.isEmpty() ? new ItemStack(Items.COBBLESTONE) : this.ammo;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put(TAG_AMMO, this.ammo.save(new CompoundTag()));
        tag.putFloat(TAG_IMPACT, this.impactDamage);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_AMMO)) {
            this.ammo = ItemStack.of(tag.getCompound(TAG_AMMO));
        }
        if (tag.contains(TAG_IMPACT)) {
            this.impactDamage = tag.getFloat(TAG_IMPACT);
            this.setBaseDamage(this.impactDamage);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        // 先判"命中那一刻"是否滞空 —— 原版结算会推动目标，之后再判就不准了 ✓
        boolean airborne = false;
        if (target instanceof LivingEntity living) {
            // ⚠ §984：**不要**用 getOnPos() 去判"离地高度" —— 飞行生物（幻翼等）的 getOnPos()
            //    常常就返回它脚下那一格，差值只剩小数部分（≈0.3）⇒ 会被误判成"没滞空" ✗
            //    （用户实测"打幻翼没反应"正是这个原因 ✓）。离地就按滞空处理 ✓。
            airborne = !living.onGround() && !living.isInWater() && !living.onClimbable() && !living.isPassenger()
                    && living.isAlive();
        }
        // ① 普通命中：原版箭矢结算（速度 × 基础伤害 ✓ 创造玩家免疫这一层 ✓ 符合预期 ✓）
        super.onHitEntity(result);
        // ② 滞空目标：强制向下拉 + 打标记，落地时由 KineticImpactHandler 结算动能伤害 ✓
        if (airborne && target instanceof LivingEntity living && !this.level().isClientSide) {
            KineticImpactHandler.pullDown(living, this.impactDamage);
        }
    }

    /**
     * §1038：撞到方块（= 落地）⇒ 喷破碎粒子 ＋ 消失。
     *
     * <p>只在服务端做一次 ✓（粒子经 {@code ServerLevel.sendParticles} 广播给所有客户端 ✓，
     * 两边都喷会变成双份 ✗）。
     */
    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (this.level().isClientSide) {
            return;
        }
        this.shatterOnGround(result);
    }

    /** 按弹药对应的方块喷一次"方块破坏"粒子，然后直接移除实体（不再插地可捡 ✓） */
    private void shatterOnGround(BlockHitResult result) {
        if (this.level() instanceof ServerLevel server) {
            Vec3 at = result.getLocation();
            server.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, this.ammoBlockState()),
                    at.x, at.y, at.z,
                    SHATTER_PARTICLES, SHATTER_SPREAD, SHATTER_SPREAD, SHATTER_SPREAD, SHATTER_SPEED);
        }
        this.discard();
    }

    /**
     * 弹药对应的方块状态 —— 圆石弹喷圆石碎粒、石头喷石头碎粒 ✓；
     * 万一弹药不是方块物品（理论上不会：弹药标签里全是石头类 ✓）就退回圆石 ✓。
     */
    private BlockState ammoBlockState() {
        if (!this.ammo.isEmpty()) {
            Block block = Block.byItem(this.ammo.getItem());
            if (block != Blocks.AIR) {
                return block.defaultBlockState();
            }
        }
        return Blocks.COBBLESTONE.defaultBlockState();
    }
}
