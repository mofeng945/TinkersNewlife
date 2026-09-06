package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.TenDivideTechnique;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 十划咒法·弱点实体：刻在目标身上的"第七划"弱点（金色小球）。
 * <p>
 * - 位置由<b>服务端</b>每 tick 驱动：悬停在目标前方 0.3 格、目标身高 70%（7:3 分界）处，
 *   随目标移动/转身即时更新并同步给客户端；
 * - 仅施术者本人能击打它：任何由施术者造成的伤害（近战挥击/箭矢/术式）命中本实体时，
 *   对本体目标结算一次"暴击"（伤害 × 暴击倍率），随后本实体消散（一击即碎）；
 * - 20 秒未被打中、目标死亡/离开、施术者离线/死亡则自动消散。
 * <p>
 * 渲染：客户端 {@code WeakPointRenderer} 手绘金色光点（无模型/无纹理）。
 */
public class WeakPointEntity extends LivingEntity {

    private static final String TAG_TARGET_ID = "tnl_wp_target_id";
    private static final String TAG_OWNER = "tnl_wp_owner";

    /** 存在时长上限（tick）：20 秒 */
    public static final int LIFETIME = 20 * 20;

    /** 目标实体 id（服务端同维度内稳定；重启后失效则弱点自动消散，瞬态状态可接受） */
    private int targetEntityId = -1;
    @Nullable
    private UUID ownerId;

    public WeakPointEntity(EntityType<? extends WeakPointEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
        setNoGravity(true);
    }

    /** 生成：锚定到目标弱点位置 */
    public WeakPointEntity(Level level, LivingEntity target, ServerPlayer owner) {
        this(ModEntities.WEAK_POINT.get(), level);
        this.targetEntityId = target.getId();
        this.ownerId = owner.getUUID();
        Vec3 anchor = anchorOf(target);
        this.moveTo(anchor.x, anchor.y, anchor.z, 0.0F, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return net.minecraft.world.entity.Mob.createMobAttributes();
    }

    /** 弱点锚点：目标前方 0.3 格、身高 70% 处 */
    public static Vec3 anchorOf(LivingEntity target) {
        Vec3 dir = target.getForward();
        if (dir.y != 0) dir = dir.multiply(1, 0, 1).normalize();
        return new Vec3(target.getX() + dir.x * 0.3,
                target.getY() + target.getBbHeight() * 0.7,
                target.getZ() + dir.z * 0.3);
    }

    /** 查找某玩家在某目标身上的弱点实体；无则 null */
    @Nullable
    public static WeakPointEntity find(ServerPlayer owner, LivingEntity target) {
        ServerLevel level = owner.serverLevel();
        for (WeakPointEntity wp : level.getEntitiesOfClass(WeakPointEntity.class,
                target.getBoundingBox().inflate(32.0),
                e -> !e.isRemoved() && owner.getUUID().equals(e.ownerId)
                        && e.targetEntityId == target.getId())) {
            return wp;
        }
        return null;
    }

    @Nullable
    private LivingEntity currentTarget() {
        if (targetEntityId < 0 || !(level() instanceof ServerLevel sl)) return null;
        return sl.getEntity(targetEntityId) instanceof LivingEntity le && le.isAlive() ? le : null;
    }

    // ================= 行为 =================

    @Override
    public void tick() {
        super.tick();
        if (isRemoved()) return;
        // 到期（双端一致清理）
        if (tickCount >= LIFETIME) {
            discard();
            return;
        }
        if (level().isClientSide) return;
        // 服务端驱动：跟随目标弱点位置 + 生命周期校验
        LivingEntity target = currentTarget();
        if (target == null || target.isRemoved()) {
            discard();
            return;
        }
        ServerPlayer owner = serverOwner();
        if (owner == null || !owner.isAlive()) {
            discard();
            return;
        }
        Vec3 anchor = anchorOf(target);
        moveTo(anchor.x, anchor.y, anchor.z, 0.0F, 0.0F);
        if (tickCount % 6 == 0 && level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(),
                    1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    @Nullable
    private ServerPlayer serverOwner() {
        if (ownerId == null || !(level() instanceof ServerLevel sl)) return null;
        return sl.getServer() != null ? sl.getServer().getPlayerList().getPlayer(ownerId) : null;
    }

    // ================= 命中判定 =================

    /** 只有施术者本人造成的伤害能"击中弱点"：对本体结算暴击后本弱点消散 */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return false;
        if (isRemoved()) return false;
        if (!(source.getEntity() instanceof ServerPlayer attacker)) return false;
        if (ownerId == null || !ownerId.equals(attacker.getUUID())) return false;
        LivingEntity target = currentTarget();
        if (target == null) {
            discard();
            return true;
        }
        // 击中弱点 → 对本体目标暴击
        TenDivideTechnique.strikeCrit(attacker, target);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CRIT, getX(), getY(), getZ(),
                    20, 0.25, 0.25, 0.25, 0.05);
        }
        discard();
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    // LivingEntity 抽象方法补全（无实际用途）

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return java.util.List.of();
    }

    // ================= 序列化 =================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(TAG_TARGET_ID, targetEntityId);
        if (ownerId != null) tag.putUUID(TAG_OWNER, ownerId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        targetEntityId = tag.getInt(TAG_TARGET_ID);
        ownerId = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
    }
}
