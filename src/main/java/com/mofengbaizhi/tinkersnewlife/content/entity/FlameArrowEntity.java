package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 灶·开 火焰箭：笔直轨迹、速度较慢（无重力），命中目标或扎在方块上
 * 引发火焰爆炸：中心伤害 = (1 + 咒力亲和/100) × (当前攻击伤害 + 咒力输出×10) × 200%，
 * 随距离衰减，不破坏方块，命中者被点燃。
 */
public class FlameArrowEntity extends AbstractArrow {

    /** 爆炸半径（格） */
    private static final float EXPLOSION_RADIUS = 5.0F;
    /** 最大飞行时间（tick）：超过后静默消失 */
    private static final int MAX_FLIGHT_TICKS = 400;

    public FlameArrowEntity(EntityType<? extends AbstractArrow> type, Level level) {
        super(type, level);
        this.setNoGravity(true);   // ⭐ 笔直轨迹
        this.pickup = Pickup.DISALLOWED;
        this.setSecondsOnFire(100); // 火焰外观（配合尾迹粒子）
    }

    /** 由术式施法者发射（眼睛高度起射） */
    public FlameArrowEntity(Level level, LivingEntity shooter) {
        this(ModEntities.FLAME_ARROW.get(), level);
        this.setOwner(shooter);
        this.setPos(shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ());
    }

    @Override
    protected ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        explode();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        explode();
    }

    /** 命中：火焰爆炸（中心伤害公式 + 距离衰减 + 点燃），不破坏方块 */
    private void explode() {
        Level level = level();
        if (level.isClientSide) return;
        ServerLevel server = (ServerLevel) level;
        Vec3 c = position();

        // 音效 + 粒子（爆炸云 + 火焰 + 熔岩 + 烟）
        server.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 4.0F, 1.0F);
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        server.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 40, 1.4, 1.4, 1.4, 0.08);
        server.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 20, 1.1, 1.1, 1.1, 0.1);
        server.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 25, 1.0, 1.0, 1.0, 0.05);

        if (getOwner() instanceof ServerPlayer shooter) {
            // 中心伤害 = (1 + 咒力亲和/100) × (当前攻击伤害 + 咒力输出×10) × 200%
            int output = CursePowerHelper.getCurseOutputLevel(shooter);
            int affinity = CursePowerHelper.getCurseAffinity(shooter);
            double attack = shooter.getAttributeValue(Attributes.ATTACK_DAMAGE);
            double centerDamage = (1.0 + affinity / 100.0) * (attack + output * 10.0) * 2.0;

            float radius = EXPLOSION_RADIUS;
            for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class,
                    AABB.ofSize(c, radius * 2, radius * 2, radius * 2))) {
                if (e == shooter) continue; // 不炸到施法者自己
                double dist = e.position().distanceTo(c);
                if (dist > radius) continue;
                double dmg = centerDamage * (1.0 - dist / radius); // 中心满额，边缘衰减
                if (dmg <= 0) continue;
                // 模块化魔杖增幅咒术（对每个受击实体应用）
                dmg = com.mofengbaizhi.tinkersnewlife.content.modifier.ModularStaffModifier
                        .getSpellAmplification(shooter, (float) dmg);
                e.hurt(server.damageSources().explosion(shooter, shooter), (float) dmg);
                e.setSecondsOnFire(3);
            }

            // 点燃 (1 + 咒力输出) 半径的区域（地面放火）
            igniteArea(server, c, 1 + output);
        }
        discard();
    }

    /** 点燃区域：在爆炸点水平半径 radius 内的地面上放置火焰（仅实心方块上方，限量） */
    private void igniteArea(ServerLevel server, Vec3 c, int radius) {
        int placed = 0;
        int cx = (int) Math.floor(c.x);
        int cy = (int) Math.floor(c.y);
        int cz = (int) Math.floor(c.z);
        for (int dx = -radius; dx <= radius && placed < 64; dx++) {
            for (int dz = -radius; dz <= radius && placed < 64; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(cx + dx, cy, cz + dz);
                // 从爆炸高度向下找 8 格内的地面
                for (int dy = 0; dy >= -8; dy--) {
                    BlockPos below = m.offset(0, dy - 1, 0);
                    if (server.getBlockState(below).isFaceSturdy(server, below, Direction.UP)) {
                        BlockPos firePos = m.offset(0, dy, 0);
                        if (server.getBlockState(firePos).isAir()) {
                            server.setBlock(firePos, Blocks.FIRE.defaultBlockState(), 3);
                            placed++;
                        }
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        // 火焰尾迹（客户端视觉）
        if (level().isClientSide) {
            level().addParticle(ParticleTypes.FLAME, getX(), getY(), getZ(), 0, 0, 0);
            level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0.02, 0);
        }
        // 未命中的超时消失
        if (!level().isClientSide && tickCount > MAX_FLIGHT_TICKS) {
            discard();
        }
    }
}
