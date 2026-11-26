package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 「兵士佩刀」的<b>刀光</b>（§810／§811）—— 仿拔刀剑那种<b>弧形斩击面片</b>的载体实体 ✓。
 *
 * <h2>为什么做成实体而不是粒子</h2>
 * 用户口径：<b>「我要的是刀光光效，不是横扫粒子」</b> ✓ ⇒ {@code ParticleTypes.SWEEP_ATTACK}（原版横扫弧）
 * 是粒子 ✗ 做不到拔刀剑那种"贴图弧面划过"的观感 ✓。本仓既有的同类做法是
 * {@code FlyingSwordTrailRenderer}（自己组网格 ＋ 顶点色 ＋ <b>原版 shader getter</b> ✓ 见该类注释里
 * "自带着色器在装了光影包的整合包里整条看不见 ✗"的教训 ✓）⇒ 这里沿用同一套思路 ✓：
 * <ul>
 *   <li><b>服务端</b>只做一件事 ✓：在目标身上生成这个实体（带自转/大小/镜像/寿命/颜色 ✓）；</li>
 *   <li><b>客户端</b>{@code SoldierSlashRenderer} 拼一条<b>两端收尖的弧带</b> ✓ ＋ 弧光贴图
 *       {@code textures/entity/soldier_slash.png} ✓（**新文件** ✓）＋ 顶点色染成灰色 ✓ ⇒ 就是"刀光" ✓。</li>
 * </ul>
 *
 * <h2>§811 修正：朝向不再随机（用户反馈"刀光重叠"✗）</h2>
 * 原来给每道刀光随机一个 {@code yaw} ✗ ⇒ 弧面会**侧对镜头**、退化成一条细线 ✗，
 * 几道叠在一起就糊成一团 ✗（就是用户说的"重叠" ✗）。
 * 现在：<b>朝向由客户端按相机算</b>（弧面永远正对镜头 ✓ 见 {@code SoldierSlashRenderer}），
 * 实体只负责带<b>平面内自转 {@code ROLL}</b> ✓ ＋ {@code MIRROR}（左右镜像 ✓）
 * ⇒ 每一段的倾角/方向都不一样 ✓ 一眼能看出是"连续几刀" ✓。
 *
 * <h2>字段（都走 {@link SynchedEntityData} ⇒ 客户端自己就能画 ✓ 不需要额外网络包 ✓）</h2>
 * <ul>
 *   <li>{@code LIFE}：寿命（tick ✓ 到点自己 {@code discard()} ✓ 只做视觉 ⇒ 不需要存档 ✓）；</li>
 *   <li>{@code DELAY}：延迟出现（tick ✓）；</li>
 *   <li>{@code SCALE}：弧的大小 ✓（每段不同 ⇒ 叠在一起也分得清 ✓）；</li>
 *   <li>{@code ROLL}：<b>平面内自转</b> ✓——正对镜头后，这个角就是玩家看到的"这一刀的角度" ✓；</li>
 *   <li>{@code MIRROR}：左右镜像 ✓（奇数段镜像 ⇒ 与偶数段"从另一侧切进来" ✓ 更不像同一刀 ✓）；</li>
 *   <li>{@code TINT}：0xRRGGBB 顶点色 ✓（唐横刀是灰色 {@code 0xC9CFD9} ✓ 以后别的武器想换色也能用 ✓）。</li>
 * </ul>
 *
 * <p>实体本身<b>不移动、不碰撞、不可选、不可攻击</b> ✓（{@code noPhysics} ＋ {@code isPickable=false} ✓），
 *  并且 {@code noSave()} 注册 ⇒ 重进世界不会留下悬挂的光效 ✓。
 */
public class SoldierSlashEntity extends Entity {

    private static final EntityDataAccessor<Integer> LIFE_TICKS =
            SynchedEntityData.defineId(SoldierSlashEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> START_DELAY =
            SynchedEntityData.defineId(SoldierSlashEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SLASH_SCALE =
            SynchedEntityData.defineId(SoldierSlashEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SLASH_ROLL =
            SynchedEntityData.defineId(SoldierSlashEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> SLASH_TINT =
            SynchedEntityData.defineId(SoldierSlashEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SLASH_MIRROR =
            SynchedEntityData.defineId(SoldierSlashEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * §1042 <b>施法者位置</b>（世界坐标 ＋ 是否已设置）—— 用户口径：
     * 「<b>剑气弧面应当凹面自玩家方向，凸面远离玩家</b>」✓。
     * <p>渲染时用它把弧带沿"朝玩家"方向弯成<b>球冠</b> ⇒ 凹面朝玩家 ✓ 凸面朝外 ✓
     * （几何与前后距离对照见 {@code SoldierSlashRenderer} 的 §1042 说明 ✓）。
     * <p>没设置时渲染退回"朝观察者" ✓ —— 施法者自己看时两者一致 ✓。
     */
    private static final EntityDataAccessor<Float> CASTER_X =
            SynchedEntityData.defineId(SoldierSlashEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> CASTER_Y =
            SynchedEntityData.defineId(SoldierSlashEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> CASTER_Z =
            SynchedEntityData.defineId(SoldierSlashEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> CASTER_SET =
            SynchedEntityData.defineId(SoldierSlashEntity.class, EntityDataSerializers.BOOLEAN);

    public SoldierSlashEntity(EntityType<? extends SoldierSlashEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    /**
     * 生成一道刀光 ✓。
     *
     * @param level      所在世界 ✓
     * @param pos        世界坐标（一般放目标身体中心 ✓）
     * @param roll       平面内自转（正对镜头后 = 玩家看到的这一刀角度 ✓）
     * @param scale      大小倍率 ✓
     * @param lifeTicks  寿命（tick ✓ 一般 5 ✓）
     * @param startDelay 延迟出现（tick ✓）
     * @param tint       0xRRGGBB 顶点色 ✓（灰色 = 0xC9CFD9 ✓）
     * @param casterPos  §1042 施法者位置 ✓（渲染据此把弧面弯成"凹面朝玩家"✓；传 null 则退回"朝观察者"✓）
     */
    public SoldierSlashEntity(Level level, Vec3 pos, float roll, float scale,
                              int lifeTicks, int startDelay, int tint, Vec3 casterPos) {
        this(ModEntities.SOLDIER_SLASH.get(), level);
        this.setPos(pos.x, pos.y, pos.z);
        this.setCasterPos(casterPos);
        this.setLifeTicks(lifeTicks);
        this.setStartDelay(startDelay);
        this.setSlashScale(scale);
        this.setSlashRoll(roll);
        this.setSlashTint(tint);
    }

    @Override
    protected void defineSynchedData() {
        this.getEntityData().define(LIFE_TICKS, 5);
        this.getEntityData().define(START_DELAY, 0);
        this.getEntityData().define(SLASH_SCALE, 1.0F);
        this.getEntityData().define(SLASH_ROLL, 0.0F);
        this.getEntityData().define(SLASH_TINT, 0xC9CFD9);
        this.getEntityData().define(SLASH_MIRROR, false);
        this.getEntityData().define(CASTER_X, 0.0F);
        this.getEntityData().define(CASTER_Y, 0.0F);
        this.getEntityData().define(CASTER_Z, 0.0F);
        this.getEntityData().define(CASTER_SET, false);
    }

    // ==================== 字段读写 ====================
    public void setLifeTicks(int v) { this.getEntityData().set(LIFE_TICKS, v); }
    public int getLifeTicks() { return this.getEntityData().get(LIFE_TICKS); }
    public void setStartDelay(int v) { this.getEntityData().set(START_DELAY, v); }
    public int getStartDelay() { return this.getEntityData().get(START_DELAY); }
    public void setSlashScale(float v) { this.getEntityData().set(SLASH_SCALE, v); }
    public float getSlashScale() { return this.getEntityData().get(SLASH_SCALE); }
    public void setSlashRoll(float v) { this.getEntityData().set(SLASH_ROLL, v); }
    public float getSlashRoll() { return this.getEntityData().get(SLASH_ROLL); }
    public void setSlashTint(int v) { this.getEntityData().set(SLASH_TINT, v); }
    public int getSlashTint() { return this.getEntityData().get(SLASH_TINT); }
    public void setMirrored(boolean v) { this.getEntityData().set(SLASH_MIRROR, v); }
    public boolean isMirrored() { return this.getEntityData().get(SLASH_MIRROR); }

    /** §1042 记录施法者位置 ⇒ 客户端渲染时把弧带朝玩家弯（凹面朝玩家 ✓）；传 null = 未设置 ✓ */
    public void setCasterPos(Vec3 pos) {
        if (pos == null) {
            this.getEntityData().set(CASTER_SET, false);
            return;
        }
        this.getEntityData().set(CASTER_X, (float) pos.x);
        this.getEntityData().set(CASTER_Y, (float) pos.y);
        this.getEntityData().set(CASTER_Z, (float) pos.z);
        this.getEntityData().set(CASTER_SET, true);
    }

    /** §1042 施法者位置 ✓（未设置时返回 null ⇒ 渲染退回"朝观察者"✓） */
    public Vec3 getCasterPos() {
        if (!this.getEntityData().get(CASTER_SET)) return null;
        return new Vec3(this.getEntityData().get(CASTER_X),
                this.getEntityData().get(CASTER_Y),
                this.getEntityData().get(CASTER_Z));
    }

    @Override
    public void tick() {
        super.tick();
        // 纯视觉 ⇒ 服务端到点收掉即可 ✓ 客户端自己按 tickCount 淡出 ✓（服务端不发多余包 ✓）
        if (!this.level().isClientSide
                && this.tickCount > this.getStartDelay() + this.getLifeTicks() + 2) {
            this.discard();
        }
    }

    /** 弧面比碰撞箱大得多 ⇒ 交给渲染剔除时用放大后的箱子 ✓ 免得贴脸时整片刀光被裁掉 ✗ */
    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(2.5D);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return distanceSqr < 128.0D * 128.0D;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.setLifeTicks(tag.getInt("life"));
        this.setStartDelay(tag.getInt("delay"));
        this.setSlashScale(tag.getFloat("scale"));
        this.setSlashRoll(tag.getFloat("roll"));
        this.setSlashTint(tag.getInt("tint"));
        this.setMirrored(tag.getBoolean("mirror"));
        if (tag.getBoolean("casterSet")) {
            this.setCasterPos(new Vec3(tag.getFloat("casterX"), tag.getFloat("casterY"), tag.getFloat("casterZ")));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("life", this.getLifeTicks());
        tag.putInt("delay", this.getStartDelay());
        tag.putFloat("scale", this.getSlashScale());
        tag.putFloat("roll", this.getSlashRoll());
        tag.putInt("tint", this.getSlashTint());
        tag.putBoolean("mirror", this.isMirrored());
        Vec3 caster = this.getCasterPos();
        tag.putBoolean("casterSet", caster != null);
        if (caster != null) {
            tag.putFloat("casterX", (float) caster.x);
            tag.putFloat("casterY", (float) caster.y);
            tag.putFloat("casterZ", (float) caster.z);
        }
    }
}
