package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 悠悠球实体
 * <p>
 * 状态机：
 * <ol>
 *   <li><b>FLYING（飞行）</b>：沿发射方向飞行，最远 10 格；触碰生物或抵达最远距离 → 停滞</li>
 *   <li><b>STALLED（停滞）</b>：原地停留 3 秒，期间每帧（tick）对触碰到的实体造成伤害
 *       （每帧伤害 = 玩家总伤害 × 10%）；3 秒后 → 飞回</li>
 *   <li><b>RETURNING（飞回）</b>：飞回发射者，期间触碰到的实体同样受到帧伤</li>
 * </ol>
 */
public class YoYoEntity extends Entity {

    // ==================== 同步数据 ====================
    private static final EntityDataAccessor<Float> DAMAGE =
            SynchedEntityData.defineId(YoYoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> OWNER_UUID =
            SynchedEntityData.defineId(YoYoEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> PHASE =
            SynchedEntityData.defineId(YoYoEntity.class, EntityDataSerializers.INT);
    /** 展示用物品（悠悠球轮） */
    private static final EntityDataAccessor<net.minecraft.world.item.ItemStack> DISPLAY_ITEM =
            SynchedEntityData.defineId(YoYoEntity.class, EntityDataSerializers.ITEM_STACK);
    /** 弓弦部件材质 VariantId（渲染器据此取材质颜色） */
    private static final EntityDataAccessor<String> BOWSTRING_VARIANT =
            SynchedEntityData.defineId(YoYoEntity.class, EntityDataSerializers.STRING);

    // ==================== 常量 ====================
    /** 最大飞行距离（格） */
    public static final double MAX_FLIGHT_DISTANCE = 10.0;
    /** 停滞时长（tick），3 秒 = 60 tick */
    public static final int STALL_DURATION = 60;
    /** 帧伤系数：每帧伤害 = 玩家总伤害 × 10% */
    public static final float DAMAGE_RATIO = 0.10f;
    /** 帧伤间隔（tick）—— 每 2 tick 一次 */
    public static final int HIT_INTERVAL = 2;

    // ==================== 状态 ====================
    public static final int PHASE_FLYING = 0;
    public static final int PHASE_STALLED = 1;
    public static final int PHASE_RETURNING = 2;

    // ==================== 实例状态 ====================
    private Vec3 launchOrigin;
    private Vec3 launchDir;
    private int stallTicks = 0;
    private int hitCooldown = 0;
    private final Set<UUID> hitEntities = new HashSet<>();

    public YoYoEntity(EntityType<? extends Entity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public YoYoEntity(Level level, Player owner, float damage, String bowstringVariant) {
        super(ModEntities.YO_YO.get(), level);
        this.noPhysics = true;
        this.setDamage(damage);
        this.setOwnerUUID(owner.getUUID());
        this.setPhase(PHASE_FLYING);
        this.setBowstringVariant(bowstringVariant);
        this.setPos(owner.getX(), owner.getEyeY() - 0.2, owner.getZ());
        this.launchOrigin = this.position();
        // 展示物品：悠悠球轮
        this.setDisplayItem(new net.minecraft.world.item.ItemStack(com.mofengbaizhi.tinkersnewlife.content.ModItems.YO_YO_WHEEL.get()));
        // 发射方向在 launch() 中由调用方设置
    }

    @Override
    protected void defineSynchedData() {
        this.getEntityData().define(DAMAGE, 0f);
        this.getEntityData().define(OWNER_UUID, "");
        this.getEntityData().define(PHASE, PHASE_FLYING);
        this.getEntityData().define(DISPLAY_ITEM, net.minecraft.world.item.ItemStack.EMPTY);
        this.getEntityData().define(BOWSTRING_VARIANT, "");
    }

    // ==================== Getter/Setter ====================
    public void setDamage(float damage) { this.getEntityData().set(DAMAGE, damage); }
    public float getDamage() { return this.getEntityData().get(DAMAGE); }
    public void setOwnerUUID(UUID uuid) { this.getEntityData().set(OWNER_UUID, uuid != null ? uuid.toString() : ""); }
    public UUID getOwnerUUID() {
        String s = this.getEntityData().get(OWNER_UUID);
        if (s == null || s.isEmpty()) return null;
        try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }
    public void setPhase(int phase) { this.getEntityData().set(PHASE, phase); }
    public int getPhase() { return this.getEntityData().get(PHASE); }
    /** 设置展示物品（渲染用） */
    public void setDisplayItem(net.minecraft.world.item.ItemStack stack) { this.getEntityData().set(DISPLAY_ITEM, stack); }
    public net.minecraft.world.item.ItemStack getDisplayItem() { return this.getEntityData().get(DISPLAY_ITEM); }
    /** 设置弓弦部件材质 VariantId（渲染器据此取材质颜色） */
    public void setBowstringVariant(String variantId) { this.getEntityData().set(BOWSTRING_VARIANT, variantId == null ? "" : variantId); }
    public String getBowstringVariant() { return this.getEntityData().get(BOWSTRING_VARIANT); }

    /**
     * 设置发射方向与速度（由发射者调用）。
     */
    public void launch(double x, double y, double z) {
        Vec3 dir = new Vec3(x, y, z).normalize();
        this.launchDir = dir;
        this.setDeltaMovement(dir.scale(1.2));
    }

    // ==================== 主逻辑 ====================
    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;

        LivingEntity owner = getOwnerEntity();
        if (owner == null || !owner.isAlive()) {
            this.discard();
            return;
        }

        // 帧伤冷却递减
        if (hitCooldown > 0) hitCooldown--;

        switch (getPhase()) {
            case PHASE_FLYING -> tickFlying(owner);
            case PHASE_STALLED -> tickStalled(owner);
            case PHASE_RETURNING -> tickReturning(owner);
        }
    }

    /** 飞行阶段：直线前进，触碰生物或抵达最远距离 → 停滞 */
    private void tickFlying(LivingEntity owner) {
        // 移动
        Vec3 motion = this.getDeltaMovement();
        this.setPos(this.position().add(motion));

        // 触碰生物 → 停滞
        if (damageNearby(owner, false)) {
            enterStalled();
            return;
        }

        // 抵达最远飞行距离 → 停滞
        if (launchOrigin != null && this.distanceToSqr(launchOrigin) >= MAX_FLIGHT_DISTANCE * MAX_FLIGHT_DISTANCE) {
            enterStalled();
            return;
        }

        // 撞到方块 → 停滞（可选，若保留则接触方块也停滞）
        if (!this.level().getBlockState(this.blockPosition()).isAir()) {
            enterStalled();
        }
    }

    /** 停滞阶段：原地停留 3 秒，持续帧伤；时间到 → 飞回 */
    private void tickStalled(LivingEntity owner) {
        stallTicks++;
        damageNearby(owner, true);
        if (stallTicks >= STALL_DURATION) {
            setPhase(PHASE_RETURNING);
            stallTicks = 0;
        }
    }

    /** 飞回阶段：向发射者飞回，期间触碰到的实体也帧伤 */
    private void tickReturning(LivingEntity owner) {
        Vec3 ownerPos = owner.getEyePosition().subtract(0, 0.2, 0);
        Vec3 toOwner = ownerPos.subtract(this.position());
        double dist = toOwner.length();
        if (dist < 0.8) {
            this.discard();
            return;
        }
        // 飞回移动
        Vec3 motion = toOwner.normalize().scale(1.5);
        this.setPos(this.position().add(motion));

        damageNearby(owner, true);
    }

    /** 进入停滞状态 */
    private void enterStalled() {
        if (getPhase() != PHASE_FLYING) return;
        this.setDeltaMovement(Vec3.ZERO);
        setPhase(PHASE_STALLED);
        stallTicks = 0;
    }

    /**
     * 对触碰到的实体造成帧伤。
     * <p>
     * 帧伤：每 HIT_INTERVAL tick 一次，每次伤害 = 玩家总伤害 × 10%；
     * 同一实体在一段停滞/飞回周期内不重复计入（hitEntities 缓存，离开后再进入才重新计入）。
     *
     * @return 是否命中了新实体（用于飞行阶段判定触碰）
     */
    private boolean damageNearby(LivingEntity owner, boolean frameDamage) {
        boolean hitAny = false;
        AABB box = this.getBoundingBox().inflate(0.4);
        List<Entity> entities = this.level().getEntities(this, box,
                e -> e instanceof LivingEntity living
                        && living.isAlive()
                        && living != owner
                        && !(living instanceof Player && ((Player) living).getUUID().equals(getOwnerUUID())));
        for (Entity entity : entities) {
            if (entity instanceof LivingEntity living) {
                UUID id = living.getUUID();
                // 每 HIT_INTERVAL tick 结算一次帧伤
                if (frameDamage && hitCooldown <= 0) {
                    // 计算帧伤：玩家总伤害 × 10%
                    float frameDamageValue = getDamage() * DAMAGE_RATIO;
                    if (frameDamageValue > 0.01f) {
                        living.hurt(this.damageSources().playerAttack((Player) owner), frameDamageValue);
                        living.invulnerableTime = 0; // 确保帧伤持续生效（绕过无敌帧）
                    }
                    hitCooldown = HIT_INTERVAL;
                }
                hitAny = true;
            }
        }
        return hitAny;
    }

    /** 获取发射者实体 */
    private LivingEntity getOwnerEntity() {
        UUID uuid = getOwnerUUID();
        if (uuid == null) return null;
        // 服务端用 ServerLevel.getEntity(UUID)（tick 已在服务端，isClientSide 早退）
        if (this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(uuid);
            return entity instanceof LivingEntity living ? living : null;
        }
        return null;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.setDamage(tag.getFloat("YoYoDamage"));
        if (tag.hasUUID("YoYoOwner")) this.setOwnerUUID(tag.getUUID("YoYoOwner"));
        this.setPhase(tag.getInt("YoYoPhase"));
        this.setBowstringVariant(tag.getString("YoYoBowstringVariant"));
        this.stallTicks = tag.getInt("YoYoStallTicks");
        if (tag.contains("YoYoLaunchX")) {
            this.launchOrigin = new Vec3(tag.getDouble("YoYoLaunchX"), tag.getDouble("YoYoLaunchY"), tag.getDouble("YoYoLaunchZ"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("YoYoDamage", this.getDamage());
        UUID owner = this.getOwnerUUID();
        if (owner != null) tag.putUUID("YoYoOwner", owner);
        tag.putInt("YoYoPhase", this.getPhase());
        tag.putString("YoYoBowstringVariant", this.getBowstringVariant());
        tag.putInt("YoYoStallTicks", this.stallTicks);
        if (this.launchOrigin != null) {
            tag.putDouble("YoYoLaunchX", this.launchOrigin.x);
            tag.putDouble("YoYoLaunchY", this.launchOrigin.y);
            tag.putDouble("YoYoLaunchZ", this.launchOrigin.z);
        }
    }
}
