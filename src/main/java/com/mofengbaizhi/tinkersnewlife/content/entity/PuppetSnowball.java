package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/**
 * 傀儡操术 · 雪傀儡投出的雪球：
 * 命中造成弹道伤害（发射时按主人输出/亲和快照计算），并附加短霜冻（amp0）。
 * 同队（主人/墨默/己方式神）目标豁免。
 */
public class PuppetSnowball extends Snowball {

    private float damage;
    private int frostTicks;

    public PuppetSnowball(EntityType<? extends Snowball> type, Level level) {
        super(type, level);
    }

    /** 发射时配置伤害与霜冻时长（服务端快照） */
    public void configure(float damage, int frostTicks) {
        this.damage = damage;
        this.frostTicks = frostTicks;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level().isClientSide) return;
        Entity target = result.getEntity();
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        Entity shooter = getOwner();
        ServerPlayer owner = null;
        if (shooter instanceof PuppetSnowGolem golem) {
            owner = golem.getOwner();
        }
        if (PuppetUtil.isAllyOf(le, owner)) return;
        le.hurt(damageSources().mobProjectile(this, shooter instanceof LivingEntity living ? living : null), damage);
        if (le.isAlive() && frostTicks > 0) {
            le.addEffect(new MobEffectInstance(ModEffects.FROST.get(), frostTicks, 0));
        }
    }
}
