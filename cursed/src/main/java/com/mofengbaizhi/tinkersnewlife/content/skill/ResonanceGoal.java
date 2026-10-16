package com.mofengbaizhi.tinkersnewlife.content.skill;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;

/**
 * TNL1174 幻音共鸣：第一个按诡厄那套迁过来的技能（真正做成 Goal）。
 *
 * <p>触发：目标在两格内**持续 20 秒**。随后 Boss 定身蓄力 2 秒，
 * 发出尖啸，把目标击退到十格以外。
 *
 * <p>所有状态都在本实例里：贴身计时是普通字段（一个 Boss 一个 Goal）、
 * 冷却在实体上、施法状态走同步的法术通道、
 * 定身靠 isBusy 闸门 ＋ 每 tick 清零速度 —— 没有全服扫描、没有 UUID 表、不用 {@code setNoAi}。
 */
public final class ResonanceGoal extends MomoSpellGoal {

    private static final double NEAR_RANGE = 2.0D;
    private static final int NEAR_TICKS_REQUIRED = 400;
    private static final int CHARGE_TICKS = 40;
    private static final int COOLDOWN_TICKS = 200;
    private static final int TOTAL_TICKS = CHARGE_TICKS + 10;

    /** TNL1090 用户口径：测动画期间把 Boss 近战基础伤害压成 0 */
    private static final boolean ATTACK_ZERO = true;

    /** 击退距离（原本在 AstralResonanceHandler 里）*/
    private static final double KNOCK_DISTANCE = 11.0D;

    private int nearTicks;

    public ResonanceGoal(com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo momo) {
        super(momo, 2, COOLDOWN_TICKS, TOTAL_TICKS);
    }

    @Override
    protected int cooldownLeft() {
        return (int) Math.max(0L, this.momo.tnl$resonanceCoolUntil() - this.momo.level().getGameTime());
    }

    @Override
    public boolean canUse() {
        // 触发判断发生在定身之前 ⇒ 这里读实体自己的目标
        if (!MomoSkills.ASTRAL_RESONANCE) {
            return false;
        }
        if (ATTACK_ZERO) {
            var inst = this.momo.getAttribute(
                    net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            if (inst != null && inst.getBaseValue() != 0.0D) {
                inst.setBaseValue(0.0D);
            }
        }
        LivingEntity target = this.stun.target();
        if (target == null || !target.isAlive()) {
            this.nearTicks = 0;
            return false;
        }
        if (this.momo.distanceTo(target) > NEAR_RANGE) {
            this.nearTicks = 0;
            return false;
        }
        // TNL1192 常量写的是"20 秒 400 tick"，但老代码每 tick 加 5 ⇒ 实际 4 秒就放。
        // 现在改成每 tick 加 1，才是文档说的 20 秒。
        this.nearTicks += 1;
        if (this.nearTicks < NEAR_TICKS_REQUIRED) {
            return false;
        }
        if (!this.ready()) {
            return false;
        }
        this.nearTicks = 0;
        return true;
    }

    @Override
    protected void onStart() {
        ServerLevel level = (ServerLevel) this.momo.level();
        LivingEntity target = this.stun.target();
        this.momo.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        this.momo.hurtMarked = true;
        if (this.momo.getNavigation() != null) {
            this.momo.getNavigation().stop();
        }
        // TNL1168 起手时朝目标一次，不再每 tick 调转头控制（那会让头来回摆）
        if (target != null) {
            double dx = target.getX() - this.momo.getX();
            double dz = target.getZ() - this.momo.getZ();
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
            this.momo.setYRot(yaw);
            this.momo.setYHeadRot(yaw);
            this.momo.setYBodyRot(yaw);
        }
        level.playSound(null, this.momo.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE,
                net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 1.0F);
        level.sendParticles(ParticleTypes.SCULK_SOUL,
                this.momo.getX(), this.momo.getY() + 1.2D, this.momo.getZ(), 16, 0.5D, 0.8D, 0.5D, 0.02D);
        TinkersNewlife.LOGGER.warn("TNL1174 Momo begins Astral Resonance charge on {}",
                target == null ? "?" : target.getName().getString());
    }

    @Override
    protected boolean onTick() {
        ServerLevel level = (ServerLevel) this.momo.level();
        if (this.momo.tnl$castTicks() <= 10) {
            // wind up is over: the shriek happens once, then the goal ends
            this.shriek(level, level.getGameTime());   // TNL1207 the skill owns its own effect
            return true;
        }
        this.momo.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        this.momo.hurtMarked = true;
        if (this.momo.getNavigation() != null) {
            this.momo.getNavigation().stop();
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL,
                this.momo.getX(), this.momo.getY() + 1.2D, this.momo.getZ(), 1, 0.3D, 0.4D, 0.3D, 0.01D);
        return false;
    }

    @Override
    protected void onStop() {
        long now = this.momo.level().getGameTime();
        this.momo.tnl$setResonanceCoolUntil(now + COOLDOWN_TICKS);
        this.momo.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }
    private void shriek(ServerLevel level, long now) {
        this.momo.tnl$setResonanceCoolUntil(now + COOLDOWN_TICKS);   // TNL1172 on the entity
        // TNL1164 user: never clear the AI for a wind up or a recovery -- it
        // sticks (teleporting high left the boss frozen two seconds). Rooting is done
        // by zeroing the movement every tick instead, which is already done above.
        LivingEntity target = this.stun.target();
        Vec3 origin = this.momo.position().add(0.0D, 1.0D, 0.0D);
        level.playSound(null, this.momo.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.HOSTILE, 3.0F, 1.0F);
        if (target == null || !target.isAlive()) {
            TinkersNewlife.LOGGER.warn("TNL1083 Momo shrieked but had no target");
            return;
        }
        Vec3 dir = target.position().subtract(this.momo.position());
        if (dir.lengthSqr() < 1.0E-4D) {
            dir = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 flat = new Vec3(dir.x, 0.0D, dir.z).normalize();
        // 水平速度要够（带阻力也能飞出十格）
        target.setDeltaMovement(flat.x * 4.6D, 0.50D, flat.z * 4.6D);   // TNL1103 push only, no damage
        target.hurtMarked = true;
        // TNL1103 用户口径：尖啸**不造成伤害**，只把目标掀飞。
        // 画出音波光柱，像监守者的弹道那样
        Vec3 end = origin.add(flat.scale(KNOCK_DISTANCE));
        for (int i = 0; i <= 30; i++) {
            double f = i / 12.0D;
            level.sendParticles(ParticleTypes.SONIC_BOOM,
                    origin.x + (end.x - origin.x) * f,
                    origin.y + (end.y - origin.y) * f,
                    origin.z + (end.z - origin.z) * f, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        TinkersNewlife.LOGGER.warn("TNL1084 Momo shrieked, knocked {} away along {}",
                target.getName().getString(), flat);
    }

}
