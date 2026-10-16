package com.mofengbaizhi.tinkersnewlife.content.skill;

import java.util.concurrent.ConcurrentHashMap;

import java.util.UUID;

import java.util.Map;

import java.util.ArrayList;

import net.minecraftforge.fml.common.Mod;

import net.minecraft.world.effect.MobEffects;

import net.minecraft.world.effect.MobEffectInstance;

import net.minecraft.sounds.SoundSource;

import net.minecraft.sounds.SoundEvents;

import net.minecraft.core.particles.ParticleTypes;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

/**
 * TNL1176 混沌和弦（法术编号 3），新体系里最后一个迁过来的技能。
 *
 * <p>每次施法共 10 波。两波间隔随机 1.5~3 秒，每波从
 * 两种高度（离地 0.15 或 2.3 格）里随机挑一个，范围 30 格，伤害为
 * 监守者音波伤害，范围内所有生物跳跃 ×1.5。硬控在最后一波之后
 * 0.5 秒结束 —— 那时波可能还在飞。
 */
public final class ChordGoal extends MomoSpellGoal {

    private static final int CHARGE_TICKS = 40;
    private static final int WAVES = 10;
    private static final int GAP_MIN_TICKS = 30;
    private static final int GAP_RANGE_TICKS = 30;
    private static final double MAX_RADIUS = 30.0D;
    private static final double SPEED = 0.15D;
    private static final double HEIGHT_LOW = 0.15D;
    private static final double HEIGHT_HIGH = 2.3D;
    private static final int COOLDOWN_TICKS = 2400;
    private static final int AFTER_LAST_TICKS = 10;

    /** 最长可能的施法总时长：蓄力 ＋ 10 波按最慢节奏 ＋ 余量 */
    private static final int TOTAL_TICKS = CHARGE_TICKS + WAVES * (GAP_MIN_TICKS + GAP_RANGE_TICKS) + 260;   // TNL1200 rings must finish flying

    private static final Random RNG = new Random();

    private int wavesLeft;
    private long nextWaveAt;
    private boolean released;

    public ChordGoal(KtuluMomo momo) {
        super(momo, 3, COOLDOWN_TICKS, TOTAL_TICKS);
    }

    @Override
    protected int cooldownLeft() {
        return (int) Math.max(0L, this.momo.tnl$chordCoolUntil() - this.momo.level().getGameTime());
    }

    @Override
    public boolean canUse() {
        if (!MomoSkills.DISCORDANT_CHORD) {
            return false;
        }
        LivingEntity target = this.stun.target();
        if (target == null || !target.isAlive() || this.momo.distanceTo(target) > MAX_RADIUS) {
            return false;
        }
        return this.ready();
    }

    @Override
    protected void onStart() {
        this.stun.begin();                     // TNL1232 和弦：蓄力到硬控结束之间定身
        ServerLevel level = (ServerLevel) this.momo.level();
        this.wavesLeft = WAVES;
        this.released = false;
        this.rings.clear();
        this.nextWaveAt = 0L;
        this.momo.setDeltaMovement(Vec3.ZERO);
        LivingEntity target = this.stun.target();
        if (target != null) {
            double dx = target.getX() - this.momo.getX();
            double dz = target.getZ() - this.momo.getZ();
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
            this.momo.setYRot(yaw);
            this.momo.setYHeadRot(yaw);
            this.momo.setYBodyRot(yaw);
        }
        DiscordantChordHandler.startChargeFx(level, this.momo);
        DiscordantChordHandler.applyJumpBuff(level, this.momo);
        DiscordantChordHandler.markChord(level, this.momo, CHARGE_TICKS + 10);
        TinkersNewlife.LOGGER.warn("TNL1176 Momo starts the Discordant Chord, {} waves queued", WAVES);
    }

    @Override
    protected boolean onTick() {
        ServerLevel level = (ServerLevel) this.momo.level();
        long now = level.getGameTime();
        DiscordantChordHandler.markChord(level, this.momo, 8);

        if (this.momo.tnl$castTicks() > TOTAL_TICKS - CHARGE_TICKS) {
            // 还在蓄力
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                    this.momo.getX(), this.momo.getY() + 1.6D, this.momo.getZ(), 2, 0.4D, 0.4D, 0.4D, 0.01D);
            this.momo.setDeltaMovement(Vec3.ZERO);
            return false;
        }

        if (this.wavesLeft > 0 && (this.nextWaveAt == 0L || now >= this.nextWaveAt)) {
            this.wavesLeft--;
            int gap = GAP_MIN_TICKS + RNG.nextInt(GAP_RANGE_TICKS + 1);
            this.nextWaveAt = now + gap;
            double baseY = this.momo.getY() + (RNG.nextBoolean() ? HEIGHT_LOW : HEIGHT_HIGH);
            // TNL1235 用户："墨默释放着和弦开始移动，于是带着波跑"。
            // 每道波必须记住**自己出生时的圆心**，不能再实时读 momo 坐标。
            this.rings.add(new double[] {0.0D, baseY, this.momo.getX(), this.momo.getY(), this.momo.getZ()});
            level.playSound(null, this.momo.blockPosition(), net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM,
                    net.minecraft.sounds.SoundSource.HOSTILE, 1.8F, 1.0F);
            TinkersNewlife.LOGGER.warn("TNL1176 chord wave {} of {} at height {}, next in {} ticks",
                    WAVES - this.wavesLeft, WAVES,
                    String.format(java.util.Locale.ROOT, "%.2f", baseY - this.momo.getY()), gap);
            if (this.wavesLeft <= 0) {
                this.releaseAt = now + AFTER_LAST_TICKS;
            }
        }

        // 同时有多道波在飞：间隔比一道波飞完 30 格所需的时间还短
        java.util.Iterator<double[]> it = this.rings.iterator();
        while (it.hasNext()) {
            double[] ring = it.next();
            ring[0] += SPEED;
            if (ring[0] > MAX_RADIUS) {
                it.remove();
                continue;
            }
            DiscordantChordHandler.drawRing(level, this.momo, ring[2], ring[3], ring[4], ring[0], ring[1]);
            DiscordantChordHandler.hitRing(level, this.momo, ring[2], ring[3], ring[4], ring[0], ring[1]);
        }

        if (!this.released) {
            this.stun.keep();                  // TNL1237 keep 定身：否则近战 Goal 会拖着 Boss 追人
            this.momo.setDeltaMovement(Vec3.ZERO);
        }
        if (!this.released && this.releaseAt > 0L && now >= this.releaseAt) {
            // TNL1200 硬控在最后一波后 0.5 秒结束；但技能继续跑，好让波
            // 把剩下的路飞完，而 Boss 已经自由（不再定身、不再摆和弦姿势）。
            this.releaseAt = 0L;
            this.released = true;
            this.stun.end();                   // TNL1232 硬控一结束就放手（波继续飞）
            this.momo.tnl$setChordCasting(false);
            TinkersNewlife.LOGGER.warn("TNL1200 chord control released, waves still flying");
        }
        if (this.released && this.rings.isEmpty()) {
            return true;
        }
        return this.momo.tnl$castTicks() <= 0;
    }

    /** 在飞的波：[0] 半径，[1] 基准高度，[2..4] 出生时的圆心 x/y/z */
    private final java.util.List<double[]> rings = new java.util.ArrayList<>();
    private long releaseAt;

    @Override
    protected void onStop() {
        this.stun.end();
        long now = this.momo.level().getGameTime();
        DiscordantChordHandler.clearJumpBuff();
        this.momo.tnl$setChordCoolUntil(now + COOLDOWN_TICKS);
        this.rings.clear();
        this.releaseAt = 0L;
        TinkersNewlife.LOGGER.warn("TNL1176 Discordant Chord finished");
    }
    // ===== TNL1213 和弦的实现整体收进技能类（★原 DiscordantChordHandler 原样成为内部类 ✓）=====
static final class DiscordantChordHandler {   // TNL1213 收编自原 DiscordantChordHandler.java

    /** TNL1153 每次施法 10 波 */
    /** TNL1153 波间隔随机 1.5~3 秒 */
    /** TNL1153 范围改成 30 格 */
    private static final double MAX_RADIUS = 30.0D;
    /** TNL1153 离地的两种发射高度 */
    /** 原版监守者音波伤害 */
    private static final float SONIC_DAMAGE = 10.0F;
    /** TNL1154 冷却从**施法结束**起算，不是从起手算 */
    private static final int EFFECT_TICKS = 100;       // 5 s of weakness and slowness

    /** 每个墨默：蓄力到这个世界时间，空闲时不用 */
    /** 每个墨默：本次施法在飞的环 */
    /** TNL1153 还有多少波没放 */
    /** TNL1153 下一波到点的时间 */
    /** TNL1157 本次施法硬控结束的时间 */

    /** TNL1155 当前带着和弦跳跃加成的人 */
    private static final List<LivingEntity> JUMPED = new ArrayList<>();

    /** TNL1153 间隔与高度的随机源 */

    private static final class Ring {
        double radius;
        final double baseY;

        Ring(double baseY) {
            this.baseY = baseY;
        }
    }

    private DiscordantChordHandler() {
    }



    /** TNL1176 给技能调用的入口：起手音效与烟雾 */
    public static void startChargeFx(ServerLevel level, KtuluMomo momo) {
        level.playSound(null, momo.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE,
                SoundSource.HOSTILE, 2.0F, 0.6F);
        level.sendParticles(ParticleTypes.CLOUD,
                momo.getX(), momo.getY() + 1.6D, momo.getZ(), 6, 0.4D, 0.4D, 0.4D, 0.02D);
    }

    /** TNL1176 给技能调用的入口：施法期间范围内跳跃 ×1.5 */
    public static void applyJumpBuff(ServerLevel level, KtuluMomo momo) {
        JUMPED.clear();
        var jumpMod = new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                java.util.UUID.nameUUIDFromBytes("tnl1155_chord_jump".getBytes()),
                "tnl1155_chord_jump", 0.5D,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL);
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class,
                momo.getBoundingBox().inflate(MAX_RADIUS))) {
            if (le == momo) {
                continue;
            }
            var attr = le.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH);
            if (attr != null && !attr.hasModifier(jumpMod)) {
                attr.addTransientModifier(jumpMod);
                JUMPED.add(le);
            }
        }
        TinkersNewlife.LOGGER.warn("TNL1176 chord jump buff on {} creatures", JUMPED.size());
    }

    /** TNL1176 给技能调用的入口：收回跳跃加成 */
    public static void clearJumpBuff() {
        for (LivingEntity le : JUMPED) {
            var attr = le.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH);
            if (attr != null) {
                attr.removeModifier(java.util.UUID.nameUUIDFromBytes("tnl1155_chord_jump".getBytes()));
            }
        }
        JUMPED.clear();
    }

    /** TNL1157 给附近玩家保持和弦标记，客户端据此选和弦姿势。*/
    public static void markChord(ServerLevel level, KtuluMomo momo, int ticks) {
        for (net.minecraft.world.entity.player.Player near : level.getEntitiesOfClass(
                net.minecraft.world.entity.player.Player.class, momo.getBoundingBox().inflate(48.0D))) {
            near.getCooldowns().addCooldown(
                    com.mofengbaizhi.tinkersnewlife.content.ModItems.AEONIAN_CHORD_MARKER.get(), ticks);
        }
    }

    /** TNL1114 深灰尘埃，看着像圆环而不是一团白。*/
    public static void drawRing(ServerLevel level, KtuluMomo momo, double ox, double oy, double oz, double radius, double baseY) {   // TNL1235 圆心由调用方给
        var grey = new net.minecraft.core.particles.DustParticleOptions(
                new org.joml.Vector3f(0.10F, 0.10F, 0.12F), 1.6F);
        int points = Math.max(32, (int) (radius * 8.0D));
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2.0D) * i / points;
            double x = ox + Math.cos(a) * radius;
            double z = oz + Math.sin(a) * radius;
            level.sendParticles(grey, x, baseY + 0.25D, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /**
     * TNL1153 被波扫到的东西吃监守者音波伤害并被掀飞，随后
     * 虚弱又迟缓。每道波在自己的高度上，所以矮的那道可以跳过去。
     */
    public static void hitRing(ServerLevel level, KtuluMomo momo, double ox, double oy, double oz, double radius, double baseY) {   // TNL1235 圆心由调用方给
        double band = 0.9D;   // TNL1237 判定也必须用锚定圆心（用户："锚定了粒子但是没锚定判定"）
        // TNL1237 以**波心**搜实体：Boss 走开了也要能找到波上的目标
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(ox - MAX_RADIUS - 2.0D, oy - 4.0D, oz - MAX_RADIUS - 2.0D, ox + MAX_RADIUS + 2.0D, oy + 4.0D, oz + MAX_RADIUS + 2.0D))) {
            if (le == momo || !le.isAlive()) {
                continue;
            }
            double d = Math.sqrt(Math.pow(le.getX() - ox, 2.0D)
                    + Math.pow(le.getZ() - oz, 2.0D));
            if (Math.abs(d - radius) > band) {
                continue;
            }
            // TNL1154 精准判定：波面必须切过实体自己的碰撞箱，留一点点余量。
            // 站立箱 0~1.8、下蹲 0~1.5、跳跃会把两者一起抬高。所以
            // 矮波（0.15）跳得过去，高波（2.3）蹲得下去。
            double wave = baseY - oy;
            double boxLow = le.getY() - oy;
            double boxHigh = boxLow + le.getBbHeight();
            double margin = 0.5D;
            if (wave < boxLow - margin || wave > boxHigh + margin) {
                continue;
            }
            le.hurt(level.damageSources().sonicBoom(momo), SONIC_DAMAGE);
            Vec3 out = new Vec3(le.getX() - ox, 0.0D, le.getZ() - oz);
            if (out.lengthSqr() < 1.0E-4D) {
                out = new Vec3(1.0D, 0.0D, 0.0D);
            }
            Vec3 flat = out.normalize();
            le.setDeltaMovement(flat.x * 1.2D, 1.15D, flat.z * 1.2D);
            le.hurtMarked = true;
            le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EFFECT_TICKS, 1, false, true, true));
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EFFECT_TICKS, 1, false, true, true));
            TinkersNewlife.LOGGER.warn("TNL1104 chord wave r={} hit {}",
                    String.format("%.2f", radius), le.getName().getString());
        }
    }

}
}
