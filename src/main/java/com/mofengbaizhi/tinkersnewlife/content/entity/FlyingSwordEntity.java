package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class FlyingSwordEntity extends Projectile {

    private static final EntityDataAccessor<Float> DAMAGE =
            SynchedEntityData.defineId(FlyingSwordEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<ItemStack> ITEM_STACK =
            SynchedEntityData.defineId(FlyingSwordEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> HIT_COUNT =
            SynchedEntityData.defineId(FlyingSwordEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> IS_CHASE_MODE =
            SynchedEntityData.defineId(FlyingSwordEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> TARGET_UUID =
            SynchedEntityData.defineId(FlyingSwordEntity.class, EntityDataSerializers.STRING);

    private boolean returning = false;
    private Vec3 startPos;
    private int hitCount = 0;
    private Consumer<Integer> returnCallback;
    private Vector3f trailColor;

    // 新增：存储发射时的方向（用于普通模式固定朝向）
    private Vec3 launchDirection;

    private LivingEntity target;
    private static final int MAX_ATTACKS = 20;
    private int ticksSinceLastAttack = 0;
    private static final int ATTACK_INTERVAL = 10;
    private int chaseTicks = 0;
    private static final int MAX_CHASE_TICKS = 300;
    private static final double MAX_CHASE_DISTANCE = 40.0;

    public FlyingSwordEntity(EntityType<? extends Projectile> type, Level level) {
        super(type, level);
        initTrailColor();
    }

    public FlyingSwordEntity(Level level, Player owner, float damage, ItemStack stack) {
        super(ModEntities.FLYING_SWORD.get(), level);
        this.setOwner(owner);
        this.setDamage(damage);
        this.setItemStack(stack.copy());
        this.setPos(owner.getX(), owner.getEyeY() - 0.2, owner.getZ());
        this.startPos = this.position();
        this.setHitCount(0);
        this.setChaseMode(false);
        this.setTargetUUID("");
        initTrailColor();
    }

    private void initTrailColor() {
        int hash = Math.abs(this.getUUID().hashCode());
        float r = ((hash >> 16) & 0xFF) / 255.0f * 0.8f + 0.2f;
        float g = ((hash >> 8) & 0xFF) / 255.0f * 0.8f + 0.2f;
        float b = (hash & 0xFF) / 255.0f * 0.8f + 0.2f;
        this.trailColor = new Vector3f(r, g, b);
    }

    @Override
    protected void defineSynchedData() {
        this.getEntityData().define(DAMAGE, 0f);
        this.getEntityData().define(ITEM_STACK, ItemStack.EMPTY);
        this.getEntityData().define(HIT_COUNT, 0);
        this.getEntityData().define(IS_CHASE_MODE, false);
        this.getEntityData().define(TARGET_UUID, "");
    }

    public void setDamage(float damage) { this.getEntityData().set(DAMAGE, damage); }
    public float getDamage() { return this.getEntityData().get(DAMAGE); }
    public void setItemStack(ItemStack stack) { this.getEntityData().set(ITEM_STACK, stack.copy()); }
    public ItemStack getItemStack() { return this.getEntityData().get(ITEM_STACK); }
    public void setHitCount(int count) { this.getEntityData().set(HIT_COUNT, count); this.hitCount = count; }
    public int getHitCount() { return this.getEntityData().get(HIT_COUNT); }
    public void setChaseMode(boolean chase) { this.getEntityData().set(IS_CHASE_MODE, chase); }
    public boolean isChaseMode() { return this.getEntityData().get(IS_CHASE_MODE); }
    public void setTargetUUID(String uuid) { this.getEntityData().set(TARGET_UUID, uuid); }
    public String getTargetUUID() { return this.getEntityData().get(TARGET_UUID); }
    public void setReturnCallback(Consumer<Integer> callback) { this.returnCallback = callback; }

    // 新增方法
    public void setLaunchDirection(Vec3 dir) { this.launchDirection = dir; }
    public Vec3 getLaunchDirection() { return this.launchDirection; }

    public void findAndSetTarget() {
        if (this.level().isClientSide) return;
        LivingEntity owner = (LivingEntity) this.getOwner();
        if (owner == null) return;

        double searchRange = 32.0;
        AABB searchBox = new AABB(
                owner.getX() - searchRange, owner.getY() - searchRange, owner.getZ() - searchRange,
                owner.getX() + searchRange, owner.getY() + searchRange, owner.getZ() + searchRange
        );

        List<LivingEntity> enemies = this.level().getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> e != owner && e.isAlive() && e.isAttackable() && !(e instanceof Player)
                        && !isOwnedBy(e, owner));

        if (enemies.isEmpty()) return;
        enemies.sort(Comparator.comparingDouble(e -> e.distanceTo(owner)));
        this.target = enemies.get(0);
        this.setTargetUUID(this.target.getUUID().toString());
    }

    private boolean isOwnedBy(LivingEntity target, LivingEntity owner) {
        if (target instanceof TamableAnimal tameable) {
            return owner.getUUID().equals(tameable.getOwnerUUID());
        }
        return false;
    }

    @Nullable
    private LivingEntity getTarget() {
        String uuidStr = this.getTargetUUID();
        if (uuidStr == null || uuidStr.isEmpty()) return null;
        try {
            UUID uuid = UUID.fromString(uuidStr);
            // ⭐ 服务端用 ServerLevel.getEntity(UUID) 直接查找（O(1)），避免每 tick 64 格全实体扫描。
            // 该方法仅在服务端存在，调用方 tick() 已通过 isClientSide 早退保证在服务端执行。
            if (this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                Entity entity = serverLevel.getEntity(uuid);
                if (entity instanceof LivingEntity living && living.isAlive()) {
                    return living;
                }
            }
        } catch (IllegalArgumentException ignored) {}
        return null;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            spawnTrailParticles();
            return;
        }

        if (this.getOwner() == null || !this.getOwner().isAlive()) {
            this.discard();
            return;
        }

        LivingEntity owner = (LivingEntity) this.getOwner();

        if (isChaseMode()) {
            tickChaseMode(owner);
        } else {
            tickNormalMode(owner);
        }
    }

    private void tickNormalMode(LivingEntity owner) {
        if (this.returning) {
            Vec3 ownerPos = owner.getEyePosition().subtract(0, 0.2, 0);
            Vec3 toOwner = ownerPos.subtract(this.position());
            double distance = toOwner.length();
            if (distance < 1.0) {
                if (this.returnCallback != null && this.hitCount < MAX_ATTACKS) {
                    this.returnCallback.accept(this.hitCount);
                }
                this.discard();
                return;
            }
            Vec3 velocity = toOwner.normalize().scale(1.5);
            this.setDeltaMovement(velocity);
            this.setPos(this.position().add(velocity));
            return;
        }

        Vec3 motion = this.getDeltaMovement();
        this.setPos(this.position().add(motion));

        if (this.startPos != null && this.distanceToSqr(this.startPos) >= 100.0) {
            this.returning = true;
            return;
        }

        attackNearbyEntities(owner);

        if (!this.level().getBlockState(this.blockPosition()).isAir()) {
            this.returning = true;
            return;
        }

        if (this.position().y < -64 || this.distanceTo(owner) > 64) {
            this.discard();
        }
    }

    private void tickChaseMode(LivingEntity owner) {
        chaseTicks++;

        if (chaseTicks > MAX_CHASE_TICKS || this.distanceTo(owner) > MAX_CHASE_DISTANCE) {
            if (this.returnCallback != null) {
                this.returnCallback.accept(this.hitCount);
            }
            this.discard();
            return;
        }

        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || isOwnedBy(target, owner)) {
            findAndSetTarget();
            target = getTarget();
            if (target == null) {
                if (this.returnCallback != null) {
                    this.returnCallback.accept(this.hitCount);
                }
                this.discard();
                return;
            }
        }

        if (this.hitCount >= MAX_ATTACKS) {
            if (this.returnCallback != null) {
                this.returnCallback.accept(this.hitCount);
            }
            this.discard();
            return;
        }

        double distToTarget = this.distanceTo(target);
        if (distToTarget > 64) {
            if (this.returnCallback != null) {
                this.returnCallback.accept(this.hitCount);
            }
            this.discard();
            return;
        }

        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 toTarget = targetPos.subtract(this.position());
        double distance = toTarget.length();

        if (distance > 0.5) {
            Vec3 velocity = toTarget.normalize().scale(1.0);
            this.setDeltaMovement(velocity);
            this.setPos(this.position().add(velocity));
        }

        ticksSinceLastAttack++;
        if (ticksSinceLastAttack >= ATTACK_INTERVAL) {
            ticksSinceLastAttack = 0;
            if (this.getBoundingBox().intersects(target.getBoundingBox().inflate(0.5))) {
                if (!isOwnedBy(target, owner)) {
                    attackEntity(owner, target);
                } else {
                    findAndSetTarget();
                }
            }
        }

        if (!target.isAlive()) {
            if (this.returnCallback != null) {
                this.returnCallback.accept(this.hitCount);
            }
            this.discard();
        }
    }

    private void attackNearbyEntities(LivingEntity owner) {
        AABB searchBox = this.getBoundingBox().inflate(0.5);
        for (Entity target : this.level().getEntities(this, searchBox,
                e -> e instanceof LivingEntity && e != owner && e.isAlive())) {
            if (this.getBoundingBox().intersects(target.getBoundingBox())) {
                if (target instanceof LivingEntity living) {
                    if (isOwnedBy(living, owner)) continue;
                    attackEntity(owner, living);
                    break;
                }
            }
        }
    }

    private void attackEntity(LivingEntity owner, LivingEntity target) {
        float damage = this.getDamage();
        DamageSource source = this.damageSources().playerAttack((Player) owner);
        target.hurt(source, damage);
        target.invulnerableTime = 0;
        this.hitCount++;
        this.setHitCount(this.hitCount);

        if (this.hitCount >= MAX_ATTACKS) {
            this.discard();
            return;
        }

        if (this.level() instanceof net.minecraft.server.level.ServerLevel server) {
            server.sendParticles(ParticleTypes.CRIT,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    10, 0.3, 0.3, 0.3, 0.1);
        }
    }

    private void spawnTrailParticles() {
        Vec3 motion = this.getDeltaMovement();
        if (motion.lengthSqr() < 0.001) return;

        for (int i = 0; i < 4; i++) {
            double offset = 0.1 + i * 0.2;
            Vec3 pos = this.position().subtract(motion.scale(offset));
            double spread = 0.08;
            double x = pos.x + (this.random.nextDouble() - 0.5) * spread;
            double y = pos.y + (this.random.nextDouble() - 0.5) * spread;
            double z = pos.z + (this.random.nextDouble() - 0.5) * spread;

            Vector3f color;
            if (isChaseMode()) {
                color = new Vector3f(
                        Math.min(1.0f, trailColor.x() + 0.5f),
                        trailColor.y() * 0.4f,
                        trailColor.z() * 0.3f
                );
            } else {
                color = new Vector3f(
                        Math.min(1.0f, trailColor.x() * 1.3f),
                        Math.min(1.0f, trailColor.y() * 1.3f),
                        Math.min(1.0f, trailColor.z() * 1.3f)
                );
            }

            DustParticleOptions dust = new DustParticleOptions(color, 1.5f);
            float vx = (float) motion.x * 0.12f;
            float vy = (float) motion.y * 0.12f;
            float vz = (float) motion.z * 0.12f;
            this.level().addParticle(dust, x, y, z, vx, vy, vz);
        }

        if (this.tickCount % 2 == 0) {
            Vec3 pos = this.position();
            Vector3f brightColor = isChaseMode() ?
                    new Vector3f(1.0f, 0.3f, 0.1f) :
                    new Vector3f(
                            Math.min(1.0f, trailColor.x() + 0.5f),
                            Math.min(1.0f, trailColor.y() + 0.5f),
                            Math.min(1.0f, trailColor.z() + 0.5f)
                    );
            DustParticleOptions dust = new DustParticleOptions(brightColor, 2.0f);
            this.level().addParticle(dust, pos.x, pos.y, pos.z, 0, 0, 0);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    // ==================== 持久化（飞剑在区块卸载/服务器重启后恢复） ====================

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setDamage(tag.getFloat("FlyingDamage"));
        if (tag.contains("FlyingItem")) {
            this.setItemStack(ItemStack.of(tag.getCompound("FlyingItem")));
        }
        this.setHitCount(tag.getInt("FlyingHitCount"));
        this.setChaseMode(tag.getBoolean("FlyingChaseMode"));
        if (tag.contains("FlyingTargetUUID")) {
            this.setTargetUUID(tag.getString("FlyingTargetUUID"));
        }
        if (tag.contains("FlyingLaunchDir")) {
            var dirTag = tag.getList("FlyingLaunchDir", net.minecraft.nbt.Tag.TAG_DOUBLE);
            if (dirTag.size() == 3) {
                this.launchDirection = new Vec3(dirTag.getDouble(0), dirTag.getDouble(1), dirTag.getDouble(2));
            }
        }
        if (tag.contains("FlyingStartPos")) {
            var posTag = tag.getList("FlyingStartPos", net.minecraft.nbt.Tag.TAG_DOUBLE);
            if (posTag.size() == 3) {
                this.startPos = new Vec3(posTag.getDouble(0), posTag.getDouble(1), posTag.getDouble(2));
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("FlyingDamage", this.getDamage());
        if (!this.getItemStack().isEmpty()) {
            tag.put("FlyingItem", this.getItemStack().save(new CompoundTag()));
        }
        tag.putInt("FlyingHitCount", this.getHitCount());
        tag.putBoolean("FlyingChaseMode", this.isChaseMode());
        if (!this.getTargetUUID().isEmpty()) {
            tag.putString("FlyingTargetUUID", this.getTargetUUID());
        }
        if (this.launchDirection != null) {
            var dirTag = new net.minecraft.nbt.ListTag();
            dirTag.add(net.minecraft.nbt.DoubleTag.valueOf(this.launchDirection.x));
            dirTag.add(net.minecraft.nbt.DoubleTag.valueOf(this.launchDirection.y));
            dirTag.add(net.minecraft.nbt.DoubleTag.valueOf(this.launchDirection.z));
            tag.put("FlyingLaunchDir", dirTag);
        }
        if (this.startPos != null) {
            var posTag = new net.minecraft.nbt.ListTag();
            posTag.add(net.minecraft.nbt.DoubleTag.valueOf(this.startPos.x));
            posTag.add(net.minecraft.nbt.DoubleTag.valueOf(this.startPos.y));
            posTag.add(net.minecraft.nbt.DoubleTag.valueOf(this.startPos.z));
            tag.put("FlyingStartPos", posTag);
        }
    }
}