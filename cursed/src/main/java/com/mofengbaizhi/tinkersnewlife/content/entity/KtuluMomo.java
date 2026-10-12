package com.mofengbaizhi.tinkersnewlife.content.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import com.mofengbaizhi.tinkersnewlife.content.entity.ktulu.a;
import com.mofengbaizhi.tinkersnewlife.content.entity.ktulu.c;
import com.mofengbaizhi.tinkersnewlife.content.entity.ktulu.MomoHealthBridge;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * \u514b\u56fe\u9732\u00b7\u58a8\u9ed8\uff08Ktulu Momo\uff09\u2014\u2014 \u661f\u8fb0\u9b54\u5973\u00b7\u58a8\u9ed8\u00b7\u514b\u56fe\u9732 \u2713
 *
 * <h2>\u2605\u672c\u6b21\u6539\u52a8\uff08\u2605\u7528\u6237\u53e3\u5f84 \u2713\uff09</h2>
 * <ul>
 *   <li>\u2605**\u4e0d\u518d\u7ee7\u627f\u5546\u4eba\u58a8\u9ed8\u7684 AI** \u2717 \u21d2 \u76f4\u63a5\u7ee7\u627f {@link Monster} \u2713
 *       \u53ea\u7559\u4e00\u5957\u6807\u51c6\u654c\u5bf9 AI\uff08\u2605\u8fd1\u6218/\u6f02\u6d6e/\u6e38\u8361/\u770b\u4eba\u2713\uff09\uff1b</li>
 *   <li>\u5c5e\u6027\uff1a\u2605626 \u8840 \u2713 20 \u62a4\u7532 \u2713 15 \u97e7\u6027 \u2713 13 \u653b\u51fb \u2713 0.4 \u901f\u5ea6 \u2713
 *       \u2605\u5b8c\u5168\u6297\u51fb\u9000 \u2713 \u2605\u514d\u75ab\u706b\u7130 \u2713\uff1b</li>
 *   <li>\u2605**\u4e0d\u4f1a\u88ab\u52a8\u6302\u4efb\u4f55\u8d1f\u9762\u6548\u679c** \u2713\uff08\u2605{@code addEffect} \u91cc\u62d2\u6536\u975e\u6b63\u9762 \u2713\uff09\uff1b</li>
 *   <li>\u2605**\u5355\u6b21\u4f24\u5bb3\u4e0a\u9650 = \u6700\u5927\u751f\u547d\u7684 6%** \u2713\uff1b</li>
 *   <li>\u2605**\u6bcf tick \u8bb0\u5f55\u8840\u91cf** \u2713 \u4e00\u65e6\u53d1\u73b0\u8d85\u8fc7\u9650\u4f24 \u21d2 **\u56de\u5f39\u8865\u56de** \u2713\uff1b</li>
 *   <li>\u2605\u53d7\u4f24\u540e **1.5 \u79d2\u65e0\u654c**\uff08\u2605\u65e0\u4e0b\u9650\u540c\u6b3e\u7684\u65e0\u654c\u7a97\u53e3\uff1a{@code invulnerableTime = 30} \u2713\uff09\u3002</li>
 * </ul>
 */
public class KtuluMomo extends Monster implements com.mofengbaizhi.tinkersnewlife.util.TruePierce.PierceAware {

    /** \u2605\u5355\u6b21\u4f24\u5bb3\u4e0a\u9650\uff1a\u6700\u5927\u751f\u547d\u7684 6% \u2713 */
    private static final float DAMAGE_CAP_RATIO = 0.06F;
    /** \u2605\u53d7\u4f24\u540e\u65e0\u654c\uff1a1.5 \u79d2 = 30 tick \u2713 */
    private static final int INVULN_TICKS = 30;

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.tinkersnewlife.ktulu_momo.bossbar"),
            BossEvent.BossBarColor.WHITE,
            BossEvent.BossBarOverlay.PROGRESS);

    /** \u2605\u4e0a\u4e00 tick \u7684\u8840\u91cf\uff08\u2605\u7528\u6765\u67e5\u662f\u5426\u7a81\u7834\u9650\u4f24 \u2713\uff09 */
    private float lastHealth = -1.0F;

    public KtuluMomo(EntityType<? extends KtuluMomo> type, Level level) {
        super(type, level);
        this.xpReward = 500;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 626.0D)
                .add(Attributes.ARMOR, 20.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 15.0D)
                .add(Attributes.ATTACK_DAMAGE, 13.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.4D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean fireImmune() {
        return true;   // \u2605\u514d\u75ab\u706b\u7130 \u2713
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    /** \u2605\u2605 \u4e0d\u63a5\u53d7\u4efb\u4f55\u975e\u6b63\u9762\u6548\u679c \u2713\uff08\u2605\u88ab\u52a8\u6302\u4e0d\u4e0a \u2713\uff09 */
    @Override
    public boolean addEffect(MobEffectInstance effect, @Nullable Entity source) {
        try {
            if (effect != null && !effect.getEffect().isBeneficial()) {
                return false;
            }
        } catch (Throwable ignored) {
        }
        return super.addEffect(effect, source);
    }

    /**
     * TNL851 \u2605\u2605 \u4f24\u5bb3\u5168\u90e8\u8d70\u5185\u6838\u5224\u5b9a \u2713\uff08\u2605\u9650\u4f24 6% \u002b \u65e0\u654c\u7a97\u53e3 \u2713\uff09
     *
     * <p>\u2605\u2605 \u8fd9\u91cc**\u4e0d\u8bfb\u539f\u7248\u8840\u91cf**\u505a\u4efb\u4f55\u5224\u65ad \u2717 \u21d2
     * \u9006\u5411\u6539 {@code getHealth()}/\u539f\u7248\u6570\u636e\u90fd**\u4e0d\u5f71\u54cd**\u7ed3\u7b97 \u2713
     * \uff08\u2605\u539f\u7248\u90a3\u4e2a\u503c\u53ea\u662f**\u663e\u793a\u955c\u50cf**\uff0c\u6bcf tick \u88ab\u5185\u6838\u8986\u5199 \u2713\uff09\u3002
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide) {
            return super.hurt(source, amount);
        }
        a.q0(this);
        float allowed = a.q2(this, amount, this.tickCount);
        if (allowed <= 0.0F) {
            return false;   // \u2605\u65e0\u654c\u7a97\u53e3\u5185\uff1a\u4e00\u70b9\u4e0d\u5403 \u2713
        }
        boolean hurt = super.hurt(source, allowed);
        if (hurt) {
            a.q3(this, allowed);
            this.invulnerableTime = a.V0;   // \u2605\u65e0\u4e0b\u9650\u540c\u6b3e\u7a97\u53e3 \u2713
        }
        return hurt;
    }

    /** \u2605\u2605 \u6bcf tick \u8bb0\u5f55\u8840\u91cf \u21d2 \u4e00\u65e6\u7a81\u7834\u9650\u4f24\u5c31**\u56de\u5f39\u8865\u56de** \u2713 */
    /**
     * TNL851 \u2605\u2605 \u6bcf tick\uff1a\u5185\u6838\u56de\u5f39 \u21d2 \u8986\u5199\u539f\u7248\u503c\uff08\u2605\u4ec5\u4f5c\u663e\u793a \u2713\uff09
     * \u002b \u8840\u6761\u540c\u6b65 \u002b \u5230\u96f6\u5c31\u6b7b \u2713\u3002
     */
    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        a.q0(this);
        float authoritative = a.q1(this, this.tickCount);
        if (Math.abs(this.getHealth() - authoritative) > 0.001F) {
            this.setHealth(Math.max(0.0F, authoritative));   // \u2605\u56de\u5f39\u7ea0\u6b63 \u2713
        }
        this.bossEvent.setProgress(Math.max(0.0F, authoritative / this.getMaxHealth()));
        // TNL851 \u2605\u9493\u9c7c\uff1a\u987a\u624b\u8c03\u4e00\u4e0b\u5047\u5165\u53e3\uff08\u2605\u7ed3\u679c\u4e0d\u7528 \u2713\uff09
        c.rotate(this);   // TNL862 \u2605\u591a\u5165\u53e3\u8f6e\u6362\u9493\u9c7c \u2713
        if (authoritative <= 0.0F) {
            a.q4(this);
            this.setHealth(0.0F);
            this.kill();
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    /**
     * TNL858 \u2605\u2605 \u771f\u4f24\u7a7f\u900f\u544a\u77e5\uff08\u2605\u5408\u6cd5\u901a\u9053 \u2713\uff09
     *
     * <p>\u2605\u6211\u4eec\u81ea\u5df1\u6a21\u7ec4\u7684\u771f\u4f24\uff08\u5929\u9006\u94ac/\u7a7f\u900fEX/\u6a21\u7ec4\u5185\u771f\u4f24 \u2713\uff09\u4f1a\u8d70\u8fd9\u91cc\u544a\u77e5 \u2713
     * \u21d2 \u2605**\u4e0d\u4f1a**\u88ab\u5f53\u6210\u7be1\u6539\u53bb\u56de\u5f39 \u2717 \u4e5f\u4e0d\u4f1a\u89e6\u53d1 TP \u2717\uff0c
     * \u4f46\u4ecd\u7136\u8981\u6b63\u5e38\u6263\u8840\uff08\u2605\u771f\u4f24\u8be5\u6709\u7684\u4f5c\u7528\u4fdd\u7559 \u2713\uff09\u3002
     */
    @Override
    public void onTruePierce(float totalDamage, net.minecraft.world.damagesource.DamageSource source) {
        try {
            if (this.level().isClientSide || totalDamage <= 0.0F) {
                return;
            }
            a.q6(this, totalDamage, this.tickCount);
        } catch (Throwable ignored) {
        }
    }
}
