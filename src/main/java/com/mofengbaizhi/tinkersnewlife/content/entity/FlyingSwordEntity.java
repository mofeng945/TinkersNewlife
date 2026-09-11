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
    /** 命中后尝试换目标的搜索半径（格）：该范围内没有其他敌人则继续攻击原目标 */
    private static final double RETARGET_RANGE = 16.0;
    /** 返航已持续的 tick 数（主人跑太快追不上时兜底收剑） */
    private int returnTicks = 0;
    private static final int MAX_RETURN_TICKS = 400;

    /**
     * 制导器：弦长制导 / 末端游戏 / 直线冲刺三态统一（见 {@link SwordGuidance}）。
     * 追击时以目标为制导目标，返航时以主人眼睛位置为制导目标。
     */
    private final SwordGuidance guidance = new SwordGuidance(SwordGuidance.Config.sword());

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

    /** 拖尾配色（客户端流光拖尾用，与粒子同色系） */
    public Vector3f getTrailColor() { return this.trailColor; }

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
        this.guidance.reset();   // 新目标 → 制导状态重置
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
            return;   // 客户端只负责渲染（流光拖尾在 FlyingSwordTrailRenderer 里绘制）
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
            returnTicks++;
            Vec3 ownerPos = owner.getEyePosition().subtract(0, 0.2, 0);
            double distance = this.position().distanceTo(ownerPos);
            // 回到主人身边收剑；或追太久仍追不上（主人高速移动）兜底收剑，避免无限期留在场上
            if (distance < 1.0 || returnTicks > MAX_RETURN_TICKS) {
                if (this.returnCallback != null && this.hitCount < MAX_ATTACKS) {
                    this.returnCallback.accept(this.hitCount);
                }
                this.discard();
                return;
            }
            // 返航同样走制导：先掉头（受横向加速度限制，不会瞬间折返），再加速飞回主人
            Vec3 velocity = this.guidance.guide(this.position(), this.getDeltaMovement(), ownerPos);
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

        // 追太久 / 离主人太远 → 返航（原先直接 discard：玩家看到的就是「飞很远然后凭空消失」）
        if (chaseTicks > MAX_CHASE_TICKS || this.distanceTo(owner) > MAX_CHASE_DISTANCE) {
            startReturn();
            return;
        }

        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || isOwnedBy(target, owner)) {
            findAndSetTarget();
            target = getTarget();
            if (target == null) {
                startReturn();
                return;
            }
        }

        if (this.hitCount >= MAX_ATTACKS) {
            startReturn();
            return;
        }

        double distToTarget = this.distanceTo(target);
        if (distToTarget > 64) {
            startReturn();
            return;
        }

        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);

        // ⭐ 命中判定：制导内部做「上一 tick 位置 → 当前位置」线段扫掠，高速穿过目标也不会漏判
        //    （原先 10 tick 才查一次包围盒，速度 1 格/tick 时隔 10 格才查一次 → 穿过去再掉头绕圈）
        boolean contact = this.guidance.hitTest(this.position(), targetPos, hitRadiusFor(target));

        // ⭐ 制导：弦长制导（画扇落点在目标）/ 末端游戏 / 直线冲刺，见 SwordGuidance
        Vec3 velocity = this.guidance.guide(this.position(), this.getDeltaMovement(), targetPos);
        this.setDeltaMovement(velocity);
        this.setPos(this.position().add(velocity));

        ticksSinceLastAttack++;
        if (contact && ticksSinceLastAttack >= ATTACK_INTERVAL) {
            ticksSinceLastAttack = 0;
            attackEntity(owner, target);
            // 命中后重置制导状态（与算法用法一致）：换目标/继续追击都从新状态起算
            this.guidance.reset();
        }

        if (!target.isAlive() && getTarget() == null) {
            startReturn();
        }
    }

    /** 命中判定半径：以目标体型放大（大体积生物不容易漏判） */
    private double hitRadiusFor(LivingEntity target) {
        return Math.max(this.guidance.config().hitRadius, target.getBbWidth() * 0.6);
    }

    /** 转入返航：先掉头飞回主人身边再收剑（不再原地 discard 凭空消失） */
    private void startReturn() {
        if (this.returning) return;
        this.returning = true;
        this.returnTicks = 0;
        this.guidance.reset();
        this.setChaseMode(false);   // 同步给客户端：轨迹立即切回「飞回主人」
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

        // ⭐ 追击模式：每次命中后尝试转移攻击其他目标
        // （16 格内还有别的敌人就换目标，没有则继续攻击当前目标）
        if (this.isChaseMode()) {
            retargetAfterAttack(owner, target);
        }

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

    /**
     * 命中一次后尝试转移目标：以自身为中心 {@link #RETARGET_RANGE} 格内寻找**除当前目标以外**的敌人，
     * 找到就切换过去；找不到则什么都不做（继续攻击当前目标）。
     */
    private void retargetAfterAttack(LivingEntity owner, LivingEntity current) {
        AABB box = this.getBoundingBox().inflate(RETARGET_RANGE);
        List<LivingEntity> others = this.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != owner && e != current && e.isAlive() && e.isAttackable()
                        && !(e instanceof Player) && !isOwnedBy(e, owner));
        if (others.isEmpty()) return;   // 16 格内没有其他目标 → 继续攻击原目标
        others.sort(Comparator.comparingDouble(this::distanceToSqr));
        LivingEntity next = others.get(0);
        this.target = next;
        this.setTargetUUID(next.getUUID().toString());
        this.guidance.reset();   // 换目标 → 制导状态重置（从新目标的几何关系重新起算）
    }

    /**
     * 拖尾粒子 —— <b>已废弃</b>：现在由客户端的「流光拖尾」（{@code FlyingSwordTrailRenderer}，
     * 动态条带 + 自定义流光着色器）承担，粒子会与光带叠在一起显得脏，故停用。
     * 颜色逻辑（{@link #getTrailColor()} + 模式增益）已原样搬到流光拖尾里。
     */
    @Deprecated
    private void spawnTrailParticles() {
        // 保留方法体仅供查阅配色逻辑；不再调用
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
        // 制导状态：转轴（防止读档后转向轴符号翻转）+ 直线模式滞后标志 + 模式
        if (tag.contains("FlyingGuideAxis")) {
            var axisTag = tag.getList("FlyingGuideAxis", net.minecraft.nbt.Tag.TAG_DOUBLE);
            if (axisTag.size() == 3) {
                this.guidance.setLastAxis(new Vec3(axisTag.getDouble(0), axisTag.getDouble(1), axisTag.getDouble(2)));
            }
        }
        this.guidance.setInStraightMode(tag.getBoolean("FlyingGuideStraight"));
        this.guidance.setMode(SwordGuidance.Mode.values()[
                Math.max(0, Math.min(SwordGuidance.Mode.values().length - 1, tag.getInt("FlyingGuideMode")))]);
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
        // 制导状态
        Vec3 axis = this.guidance.getLastAxis();
        if (axis != null && axis.lengthSqr() > 1.0E-8) {
            var axisTag = new net.minecraft.nbt.ListTag();
            axisTag.add(net.minecraft.nbt.DoubleTag.valueOf(axis.x));
            axisTag.add(net.minecraft.nbt.DoubleTag.valueOf(axis.y));
            axisTag.add(net.minecraft.nbt.DoubleTag.valueOf(axis.z));
            tag.put("FlyingGuideAxis", axisTag);
        }
        tag.putBoolean("FlyingGuideStraight", this.guidance.isInStraightMode());
        tag.putInt("FlyingGuideMode", this.guidance.mode().ordinal());
    }
}