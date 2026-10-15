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

    // ==================== TNL876 \u2605\u6027\u80fd\uff1a\u70ed\u70b9\u8def\u5f84\u7f13\u5b58 \u2713 ====================
    /** \u2605\u6743\u5a01\u8840\u91cf\u7f13\u5b58\uff08\u2605\u907f\u514d getHealth \u6bcf\u6b21\u90fd\u67e5 map/\u8c03 JNI \u2713\uff09 */
    private float tnl$auth = -1.0F;
    /** TNL893 \u2605\u2605 \u771f\u6b7b\u6807\u5fd7\uff1a\u2605\u4e00\u65e6\u771f\u6b7b \u21d2 \u2605\u5f7b\u5e95\u505c\u6b62\u62a4\u8840/\u56de\u5f39/\u5bf9\u5916\u906e\u853d \u2713
     *  \u2605\u7528\u6237\u53cd\u9988\uff1a\u2605"\u5982\u679c\u8fd9\u4e00\u51fb\u4f24\u5bb3\u4f7f\u4ed6\u6b7b\u4ea1\uff0c\u4ed6\u53c8\u56de\u5f39\u8840\u91cf\uff0c
     *  \u4f1a\u8fdb\u5165\u659c\u5012\u62bd\u643a\u9636\u6bb5\uff0c\u867d\u7136\u80fd\u88ab\u6253\u6b7b\uff0c\u4f46\u6389\u843d\u7269\u548c\u6b7b\u4ea1\u5224\u5b9a\u5df2\u7ecf\u63d0\u524d\u4e86" \u2713
     *  \u2605\u539f\u56e0 \u2717\uff1a\u2605\u539f\u7248\u5728\u8840\u91cf\u5230 0 \u65f6**\u7acb\u523b\u8d70\u6b7b\u4ea1\u6d41\u7a0b**\uff08\u2605\u6389\u6218\u5229\u54c1/\u5224\u5b9a/\u5012\u5730 \u2713\uff09\uff0c
     *  \u2605\u800c\u6211\u7684\u56de\u5f39\u53c8\u628a\u5b83\u62c9\u56de 1 \u8840 \u2717 \u21d2 \u2605\u590d\u6d3b\u6210"\u5012\u5730\u62bd\u643a" \u2713\u3002
     */
    private boolean tnl$dead = false;

    /** TNL893 \u2605\u662f\u5426\u5df2\u771f\u6b7b\uff08\u2605\u4f9b\u5185\u6838/\u6df7\u5165\u5224\u65ad \u2713\uff09 */
    public boolean tnl$isDead() {
        return this.tnl$dead;
    }
    /** \u2605\u4e0a\u6b21\u540c\u6b65\u7ed9\u8840\u6761\u7684\u8fdb\u5ea6\uff08\u2605\u6ca1\u53d8\u5c31\u4e0d\u53d1\u5305 \u2713\uff09 */
    private float tnl$prog = -1.0F;

    /** \u2605\u4f9b\u5185\u6838/\u6df7\u5165\u66f4\u65b0\u7f13\u5b58 \u2713 */
    public void tnl$setAuth(float v) {
        this.tnl$auth = v;
    }

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
        if (this.level().isClientSide || this.tnl$dead) {
            return super.hurt(source, amount);   // TNL893 \u2605\u771f\u6b7b\u540e\u4e0d\u5e72\u9884 \u2713
        }
        a.q0(this);
        // TNL871 \u2605\u2605 \u771f\u4f24\u8bc6\u522b\uff1a\u2605\u8fd9\u4e9b\u4f24\u5bb3\u7c7b\u578b\u4e0d\u4f1a\u901a\u77e5 onTruePierce \u2717
        //   \u21d2 \u2605\u65e7\u5199\u6cd5\u4f1a\u628a\u5b83\u5f53\u666e\u901a\u4f24\u5bb3\u653e\u884c\uff08\u2605\u5b9e\u6d4b 25.5 \u2713\uff09
        //   \u2605\u73b0\u5728\uff1a\u2605**\u6309\u4f24\u5bb3\u7c7b\u578b\u76f4\u63a5\u62e6** \u2713 \u2014\u2014 \u2605\u4e0d\u6389\u8840 \u2717 \u2605\u4e0d\u51fa\u4f24\u5bb3\u6570\u5b57 \u2717 \u2605\u4e0d\u5403\u60e9\u7f5a \u2717
        String tnl$id = source.getMsgId();
        if (tnl$id != null && (tnl$id.contains("truePierce") || tnl$id.contains("true_pierce"))) {
            // TNL872 \u2605\u2605 \u7528\u6237\u53e3\u5f84\uff08\u2605\u6700\u7ec8\u7248 \u2713\uff09\uff1a
            //   \u2605\u771f\u4f24**\u7167\u5e38\u6253\u4f24\u5bb3** \u2713 \u2014\u2014 \u2605\u4e0d\u518d\u4e00\u5f8b\u62e6\u6389 \u2717
            //   \u2605\u4f46\u5fc5\u987b**\u5403\u514d\u75ab\u7a97\u7684\u7ea6\u675f** \u2713\uff08\u2605\u7a97\u5185\u6253\u4e0d\u52a8 \u2717 \u2605\u4e5f\u4e0d\u80fd\u628a\u7a97\u6e05\u6389 \u2717\uff09
            //   \u2605\u6240\u4ee5\u8fd9\u91cc**\u53ea\u8bb0\u5f55\u4e0d\u62e6**\u2717 \u21d2 \u2605\u4e0b\u9762\u8d70\u540c\u4e00\u5957\u95f8\u95e8
            //   \uff08q2\uff1a\u65e0\u654c\u7a97\u5185\u8fd4\u56de 0 \u2713\uff09\u002b \u9650\u4f24\uff08LivingHurt/LivingDamage LOWEST \u2713\uff09\u3002
            a.qB("PIERCE", tnl$id, amount, a.qC(this));
        }
        // TNL866 \u2605\u2605 hurt \u53ea\u5f53"\u95f8\u95e8"\uff1a\u65e0\u654c\u7a97\u5185/\u9884\u7b97\u7528\u5b8c\u5c31\u76f4\u63a5\u62e6 \u2713
        //   \u2605\u771f\u6b63\u7684\u9650\u4f24\u5728 LivingDamageEvent\uff08LOWEST\uff09\u91cc\u505a \u2713\uff08\u2605\u56e0\u4e3a\u4f24\u5bb3\u4f1a\u5728\u90a3\u4e4b\u524d\u88ab\u5404\u7279\u6027\u653e\u5927 \u2713\uff09
        float gate = a.q2(this, amount, this.tickCount);
        a.qB("GATE", source.getMsgId(), amount, "gate=", gate, a.qC(this));
        if (gate <= 0.0F) {
            if (a.qH()) {
                this.tnl$shieldFlash();     // TNL907 \u2605\u7a97\u5185\u6321\u4e0b \u2713
            }
            return false;
        }
        boolean tnl$hurt = super.hurt(source, amount);
        // TNL913 \u2605\u2605 \u63a2\u9488\uff1a\u2605\u95f8\u95e8\u653e\u884c\u4f46 super.hurt \u8fd4\u56de false \u2717
        //   \u2605\u8bf4\u660e\u662f**\u539f\u7248 invulnerableTime**\u5728\u62e6 \u2717\uff08\u2605\u4e0d\u662f\u6211\u4eec\u7684\u7a97 \u2713\uff09
        //   \u2605\u7528\u6237\u53e3\u5f84\uff1a\u2605"\u514d\u75ab\u7a97\u7591\u4f3c\u53d8\u4e3a 2 \u500d\uff0c\u540e 1.5s \u65e0\u6cd5\u63a5\u7ba1" \u2713
        if (!tnl$hurt && System.currentTimeMillis() - tnl$lastVanillaLog > 800L) {
            tnl$lastVanillaLog = System.currentTimeMillis();
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                    "TNL913 vanilla blocked: msgId={}, invulnerableTime={}, ourGate={}, state={}",
                    source.getMsgId(), this.invulnerableTime, gate, a.qC(this));
        }
        this.tnl$auth = a.qF(this);   // TNL876 \u2605\u53d7\u4f24\u540e\u5237\u65b0\u7f13\u5b58 \u2713
        if (this.tnl$auth <= 0.0F) {   // TNL1146 only the mirror may declare it dead, the sword calls die() and isDeadOrDying() would latch the protection off
            this.tnl$dead = true;     // TNL893 \u2605\u8fd9\u4e00\u51fb\u771f\u7684\u6253\u6b7b\u4e86 \u21d2 \u2605\u4e0d\u518d\u56de\u5f39 \u2713
        }
        return tnl$hurt;
    }

    /** \u2605\u2605 \u6bcf tick \u8bb0\u5f55\u8840\u91cf \u21d2 \u4e00\u65e6\u7a81\u7834\u9650\u4f24\u5c31**\u56de\u5f39\u8865\u56de** \u2713 */
    /**
     * TNL851 \u2605\u2605 \u6bcf tick\uff1a\u5185\u6838\u56de\u5f39 \u21d2 \u8986\u5199\u539f\u7248\u503c\uff08\u2605\u4ec5\u4f5c\u663e\u793a \u2713\uff09
     * \u002b \u8840\u6761\u540c\u6b65 \u002b \u5230\u96f6\u5c31\u6b7b \u2713\u3002
     */
    /** TNL893 \u2605\u2605 \u771f\u6b7b\u94a9\u5b50\uff1a\u2605\u5148\u7f6e\u6807\u5fd7 \u002b \u6ce8\u9500\u5185\u6838 \u2717 \u21d2 \u2605\u4e4b\u540e\u4e0d\u518d\u56de\u5f39/\u4e0d\u518d\u62a4\u8840 \u2713\uff08\u2605\u6389\u843d\u7269\u4e0e\u6b7b\u4ea1\u5224\u5b9a\u53ea\u767d\u4e00\u6b21 \u2713\uff09 */
    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        this.tnl$dead = true;
        try {
            Vq$release();
        } catch (Throwable ignored) {
        }
        super.die(source);
    }

    /** TNL907 \u2605\u514d\u75ab\u7a97\u6321\u4e0b \u21d2 \u2605\u901a\u77e5\u5468\u56f4\u73a9\u5bb6\u8ba9\u8840\u6761\u95ea\u91d1 \u2713\uff08\u2605\u9650\u6d41 250ms \u2713\uff09 */
    private long tnl$lastFlash = 0L;
    private long tnl$lastVanillaLog = 0L;

    public void tnl$shieldFlash() {
        if (this.level().isClientSide) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - this.tnl$lastFlash < 60L) {
            return;
        }
        this.tnl$lastFlash = now;
        if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            com.mofengbaizhi.tinkersnewlife.network.starfall.PacketKtuluShieldFlash pkt =
                    new com.mofengbaizhi.tinkersnewlife.network.starfall.PacketKtuluShieldFlash();
            int sent = 0;
            for (net.minecraft.server.level.ServerPlayer sp : sl.players()) {
                if (sp.distanceToSqr(this) < 64.0D * 64.0D) {
                    com.mofengbaizhi.tinkersnewlife.TinkersNewlife.CHANNEL.send(
                            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp), pkt);
                    sent++;
                }
            }
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                    "TNL908 server sent shield flash to {} players", sent);
        }
    }

    private void Vq$release() {
        a.q4(this);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || this.tnl$dead) {
            return;   // TNL893 \u2605\u771f\u6b7b\u540e\u4e0d\u518d\u62a4\u8840/\u56de\u5f39 \u2713
        }
        a.q0(this);
        float authoritative = a.q1(this, this.tickCount);
        if (a.qH()) {
            this.tnl$shieldFlash();     // TNL907 \u2605\u6321\u4e0b\u4e86 \u21d2 \u2605\u95ea\u91d1 \u2713
        }
        // TNL876 \u2605\u2605 \u6027\u80fd\uff1a\u53ea\u5728"\u771f\u7684\u4e0d\u4e00\u6837"\u65f6\u624d\u5199 \u2713
        //   \u2605setHealth \u4f1a\u6807\u810f\u5b9e\u4f53\u6570\u636e \u21d2 \u2605\u6bcf tick \u8c03\u5c31\u7b49\u4e8e\u6bcf tick \u53d1\u5305 \u2717
        this.tnl$auth = authoritative;
        if (Math.abs(super.getHealth() - authoritative) > 0.01F) {
            this.setHealth(Math.max(0.0F, authoritative));
        }
        // TNL876 \u2605\u8840\u6761\u4e5f\u53ea\u5728\u53d8\u5316\u65f6\u540c\u6b65 \u2713\uff08\u2605\u907f\u514d\u6bcf tick \u53d1 BossEvent \u5305 \u2717\uff09
        float tnl$p = Math.max(0.0F, authoritative / this.getMaxHealth());
        if (Math.abs(tnl$p - this.tnl$prog) > 0.0005F) {
            this.tnl$prog = tnl$p;
            this.bossEvent.setProgress(tnl$p);
        }
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

    /**
     * TNL875 \u2605\u2605 \u5bf9\u5916\u59cb\u7ec8\u62a5\u544a**\u6743\u5a01\u8840\u91cf** \u2713
     *
     * <p>\u2605\u8fd9\u662f"\u963b\u6b62\u6539\u8840"\u6700\u786c\u7684\u4e00\u5c42 \u2713\uff1a
     * \u2605\u5373\u4f7f\u6709\u4eba\u7528\u53cd\u5c04/Unsafe **\u76f4\u63a5\u628a\u5b57\u6bb5\u5199\u6210 0** \u2717\uff0c
     * \u2605\u6b7b\u4ea1\u5224\u5b9a\uff08{@code isAlive}/{@code isDeadOrDying} \u2713\uff09\u3001\u53d7\u4f24\u8ba1\u7b97\u3001
     * \u5ba2\u6237\u7aef\u6e32\u67d3\u5168\u90e8\u8bfb\u7684\u90fd\u662f\u8fd9\u91cc\u8fd4\u56de\u7684\u503c \u2713\u3002
     */
    @Override

    public float getHealth() {
        try {
            if (this.level() == null || this.level().isClientSide || this.tnl$dead) {
                return super.getHealth();   // TNL893 \u2605\u771f\u6b7b\u540e\u4e0d\u518d\u906e\u853d \u2713
            }
            // TNL876 \u2605\u5148\u8bfb\u7f13\u5b58\uff08\u2605getHealth \u662f\u8d85\u70ed\u70b9 \u2713\uff09
            if (this.tnl$auth >= 0.0F) {
                return this.tnl$auth;
            }
            float v = a.qF(this);
            this.tnl$auth = v;
            return v;
        } catch (Throwable th) {
            return super.getHealth();
        }
    }

    /**
     * TNL1146 one cheat hit used to latch the boss off, because hurt() saw isDeadOrDying() (the
     * sword calls die() even when the death is cancelled) and set tnl$dead. After that the whole
     * protection is skipped: no damage limit, no immunity window. This clears the latch.
     */
    public void tnl$unDead() {
        try {
            this.tnl$dead = false;
            float auth = a.qF(this);
            if (auth > 0.0F) {
                this.tnl$auth = auth;
                if (Math.abs(super.getHealth() - auth) > 0.01F) {
                    this.setHealth(auth);
                }
                this.deathTime = 0;
                this.hurtTime = 0;
            }
        } catch (Throwable ignored) {
        }
    }

    /** TNL1086 Astral Resonance charge state, synced so the client can play the Warden style wind up. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_CHARGING =
            net.minecraft.network.syncher.SynchedEntityData.defineId(KtuluMomo.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_CHARGING, Boolean.FALSE);
    }

    public boolean tnl$isCharging() {
        return this.entityData.get(DATA_CHARGING);
    }

    public void tnl$setCharging(boolean charging) {
        this.entityData.set(DATA_CHARGING, charging);
    }

}
