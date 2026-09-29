package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DreadsteelSlashEntity extends Projectile {

    private static final EntityDataAccessor<Float> DAMAGE =
            SynchedEntityData.defineId(DreadsteelSlashEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> OWNER_UUID_STR =
            SynchedEntityData.defineId(DreadsteelSlashEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> PIERCING_LEVEL =
            SynchedEntityData.defineId(DreadsteelSlashEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SLASH_WIDTH =
            SynchedEntityData.defineId(DreadsteelSlashEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SLASH_SPEED =
            SynchedEntityData.defineId(DreadsteelSlashEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> WEAKNESS_DURATION =
            SynchedEntityData.defineId(DreadsteelSlashEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BLINDNESS_DURATION =
            SynchedEntityData.defineId(DreadsteelSlashEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> WITHER_DURATION =
            SynchedEntityData.defineId(DreadsteelSlashEntity.class, EntityDataSerializers.INT);

    /** §826 刀光颜色：用本模组「悚怖钢」自己的配色 ✓（`assets/tinkersnewlife/mantle/colors.json` 里的 dreadsteel ✓ 改这一个常量即可） */
    private static final int SLASH_TINT = 0xFF55FF;
    /** 每道弧光的寿命（tick ✓ 与唐横刀一致） */
    private static final int ARC_LIFE_TICKS = 5;
    /** 每几 tick 挥出一道弧光 ✓（2 ⇒ 剑气飞过时留下一串 ✓ 不糊） */
    private static final int ARC_INTERVAL_TICKS = 2;

    private int life = 0;
    private int maxLife = 120;
    private final Set<UUID> hitEntities = new HashSet<>();

    public DreadsteelSlashEntity(EntityType<? extends Projectile> entityType, Level level) {
        super(entityType, level);
    }

    public DreadsteelSlashEntity(Level level, LivingEntity owner, float damage, int piercing,
                                 float width, float speed, int weaknessDur, int blindnessDur, int witherDur) {
        super(ModEntities.DREADSTEEL_SLASH.get(), level);
        this.setOwner(owner);
        this.setDamage(damage);
        this.setPiercingLevel(piercing);
        this.setSlashWidth(width);
        this.setSlashSpeed(speed);
        this.setWeaknessDuration(weaknessDur);
        this.setBlindnessDuration(blindnessDur);
        this.setWitherDuration(witherDur);

        Vec3 lookVec = owner.getLookAngle().normalize();
        double x = owner.getX() + lookVec.x * 1.5;
        double y = owner.getY() + owner.getEyeHeight() - 0.2;
        double z = owner.getZ() + lookVec.z * 1.5;
        this.setPos(x, y, z);
        this.setDeltaMovement(lookVec.scale(speed));
        float yRot = (float) (Math.atan2(lookVec.x, lookVec.z) * 180 / Math.PI);
        this.setYRot(yRot);

        float halfWidth = width * 0.9f;
        AABB box = new AABB(
                this.getX() - halfWidth, this.getY() - halfWidth * 0.6, this.getZ() - halfWidth,
                this.getX() + halfWidth, this.getY() + halfWidth * 0.6, this.getZ() + halfWidth
        );
        this.setBoundingBox(box);
    }

    @Override
    protected void defineSynchedData() {
        this.getEntityData().define(DAMAGE, 0f);
        this.getEntityData().define(OWNER_UUID_STR, "");
        this.getEntityData().define(PIERCING_LEVEL, 0);
        this.getEntityData().define(SLASH_WIDTH, 1.0f);
        this.getEntityData().define(SLASH_SPEED, 1.0f);
        this.getEntityData().define(WEAKNESS_DURATION, 0);
        this.getEntityData().define(BLINDNESS_DURATION, 0);
        this.getEntityData().define(WITHER_DURATION, 0);
    }

    // ==================== Getter/Setter ====================
    public void setDamage(float damage) { this.getEntityData().set(DAMAGE, damage); }
    public float getDamage() { return this.getEntityData().get(DAMAGE); }
    public void setOwnerUUID(UUID uuid) { this.getEntityData().set(OWNER_UUID_STR, uuid != null ? uuid.toString() : ""); }
    @Nullable public UUID getOwnerUUID() {
        String str = this.getEntityData().get(OWNER_UUID_STR);
        if (str == null || str.isEmpty()) return null;
        try { return UUID.fromString(str); } catch (IllegalArgumentException e) { return null; }
    }
    public void setPiercingLevel(int level) { this.getEntityData().set(PIERCING_LEVEL, level); }
    public int getPiercingLevel() { return this.getEntityData().get(PIERCING_LEVEL); }
    public void setSlashWidth(float width) { this.getEntityData().set(SLASH_WIDTH, width); }
    public float getSlashWidth() { return this.getEntityData().get(SLASH_WIDTH); }
    public void setSlashSpeed(float speed) { this.getEntityData().set(SLASH_SPEED, speed); }
    public float getSlashSpeed() { return this.getEntityData().get(SLASH_SPEED); }
    public void setWeaknessDuration(int dur) { this.getEntityData().set(WEAKNESS_DURATION, dur); }
    public int getWeaknessDuration() { return this.getEntityData().get(WEAKNESS_DURATION); }
    public void setBlindnessDuration(int dur) { this.getEntityData().set(BLINDNESS_DURATION, dur); }
    public int getBlindnessDuration() { return this.getEntityData().get(BLINDNESS_DURATION); }
    public void setWitherDuration(int dur) { this.getEntityData().set(WITHER_DURATION, dur); }
    public int getWitherDuration() { return this.getEntityData().get(WITHER_DURATION); }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            // §826 用户口径：刀光改成唐横刀那种**弧面** ✓ ⇒ 不再在客户端喷横扫粒子 ✗
            //   （弧光由**服务端**生成实体 ⇒ 天然同步给所有玩家 ✓）
            return;
        }
        life++;
        if (life > maxLife) { this.discard(); return; }

        // §826 沿飞行轨迹挥出刀光 ✓（用户口径：改成唐横刀那种弧光样式 ✓）
        if (life % ARC_INTERVAL_TICKS == 0) {
            spawnSlashArc();
        }

        Vec3 motion = this.getDeltaMovement();
        Vec3 currentPos = this.position();
        Vec3 nextPos = currentPos.add(motion);
        float width = this.getSlashWidth();

        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                this.level(), this, currentPos, nextPos,
                this.getBoundingBox().expandTowards(motion).inflate(width * 1.5),
                entity -> entity instanceof LivingEntity && entity != this.getOwner()
        );

        if (entityHit != null) {
            this.onHit(entityHit);
            if (hitEntities.size() > this.getPiercingLevel()) {
                this.discard(); return;
            }
        }

        ClipContext clipContext = new ClipContext(
                currentPos, nextPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this
        );
        BlockHitResult blockHit = this.level().clip(clipContext);
        if (blockHit.getType() != HitResult.Type.MISS) {
            this.onHit(blockHit);
            this.discard(); return;
        }

        this.setPos(nextPos.x, nextPos.y, nextPos.z);
        this.setDeltaMovement(motion.scale(0.99));
        if (motion.length() < 0.01) this.discard();
    }

    /**
     * §826 <b>挥出一道悚怖钢刀光</b> ✓ —— 用户口径：「**把悚怖钢的剑气实体的横扫粒子也改成唐刀光效的样式**」✓
     *
     * <p>实现：直接复用唐横刀那套 {@link SoldierSlashEntity}（弧形面片 ＋ 弧光贴图 ＋ 加色混合 ✓
     * 见 {@code SoldierSlashRenderer}）⇒ 剑气飞过时沿轨迹留下一串**弧光** ✓，
     * 原来的 {@code SWEEP_ATTACK} / {@code DRAGON_BREATH} / {@code FLAME} 粒子已全部删除 ✗。
     *
     * <p>⚠ 只在**服务端**生成（弧光是同步实体 ⇒ 别的玩家也看得见 ✓）；客户端分支已直接 return ✓。
     */
    private void spawnSlashArc() {
        try {
            float width = Math.max(0.8F, this.getSlashWidth());
            SoldierSlashEntity arc = new SoldierSlashEntity(
                    this.level(), this.position(),
                    this.random.nextFloat() * 360.0F,        // 自转：每道角度都不同 ✓
                    width * 1.1F,                            // 大小随剑气宽度走 ✓
                    ARC_LIFE_TICKS, 0, SLASH_TINT);
            arc.setMirrored(this.random.nextBoolean());      // 左右镜像 ⇒ 更像连续斩击 ✓
            this.level().addFreshEntity(arc);
        } catch (Throwable ignored) {
            // 光效失败绝不影响剑气本身 ✓
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        if (target == null) return;
        Entity owner = this.getOwner();
        if (owner != null && target == owner) return;
        UUID targetUUID = target.getUUID();
        if (hitEntities.contains(targetUUID)) return;
        if (target instanceof LivingEntity living) {
            float damage = this.getDamage();
            boolean hurt = living.hurt(this.damageSources().magic(), damage);
            if (hurt) {
                living.invulnerableTime = 0;
                hitEntities.add(targetUUID);
                int weaknessDur = this.getWeaknessDuration();
                int blindnessDur = this.getBlindnessDuration();
                int witherDur = this.getWitherDuration();
                if (weaknessDur > 0) living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, weaknessDur, 1));
                if (blindnessDur > 0) living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, blindnessDur, 0));
                if (witherDur > 0) living.addEffect(new MobEffectInstance(MobEffects.WITHER, witherDur, 1));
                float width = this.getSlashWidth();
                for (int i = 0; i < 25; i++) {
                    double dx = (this.random.nextDouble() - 0.5) * width * 2.0;
                    double dy = (this.random.nextDouble() - 0.5) * width * 1.5;
                    double dz = (this.random.nextDouble() - 0.5) * width * 2.0;
                    this.level().addParticle(ParticleTypes.EXPLOSION,
                            target.getX() + dx, target.getY() + dy, target.getZ() + dz, 0, 0, 0);
                }
                this.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.8f);
                if (hitEntities.size() > this.getPiercingLevel()) this.discard();
            }
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        float width = this.getSlashWidth();
        for (int i = 0; i < 15; i++) {
            double dx = (this.random.nextDouble() - 0.5) * width;
            double dy = (this.random.nextDouble() - 0.5) * width * 0.6;
            double dz = (this.random.nextDouble() - 0.5) * width;
            this.level().addParticle(ParticleTypes.EXPLOSION,
                    this.getX() + dx, this.getY() + dy, this.getZ() + dz, 0, 0, 0);
        }
        this.discard();
    }

    // ⭐ 不再重写 getOwner()：基类 Projectile.getOwner() 通过 ownerUUID 直接 level.getEntity()（O(1)）。
    // 原重写每 tick 做 64 格全实体扫描，且主人离远后斩击可误伤自己。
    @Override
    public void setOwner(@Nullable Entity entity) {
        super.setOwner(entity);
        if (entity != null) this.setOwnerUUID(entity.getUUID());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        this.setDamage(compound.getFloat("damage"));
        if (compound.hasUUID("owner")) this.setOwnerUUID(compound.getUUID("owner"));
        this.setPiercingLevel(compound.getInt("piercing"));
        this.setSlashWidth(compound.getFloat("width"));
        this.setSlashSpeed(compound.getFloat("speed"));
        this.setWeaknessDuration(compound.getInt("weaknessDur"));
        this.setBlindnessDuration(compound.getInt("blindnessDur"));
        this.setWitherDuration(compound.getInt("witherDur"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        compound.putFloat("damage", this.getDamage());
        UUID uuid = this.getOwnerUUID();
        if (uuid != null) compound.putUUID("owner", uuid);
        compound.putInt("piercing", this.getPiercingLevel());
        compound.putFloat("width", this.getSlashWidth());
        compound.putFloat("speed", this.getSlashSpeed());
        compound.putInt("weaknessDur", this.getWeaknessDuration());
        compound.putInt("blindnessDur", this.getBlindnessDuration());
        compound.putInt("witherDur", this.getWitherDuration());
    }

    @Override
    public boolean isPickable() { return false; }
}