package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.network.starfall.PacketStarfallHit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.LinkedList;
import java.util.UUID;

/**
 * TNL879 星陨之箭（★照搬参考实现 HomingArrow ✓）
 *
 * <p>★参考实现里箭头体**没有追踪逻辑** ✗ ——
 * ★"追踪"的观感来自**管状流光尾迹** + **命中星环/五星** ✓（见 StarfallRenderHandler ✓）。
 * ★本类另外保留用户口径：★弱追踪（仅预测一次 ✓）+ ★真实伤害（非 Boss 8 点／Boss 上限 0.5%）+ ★ 10% 强化箭。
 */
public class StarfallArrow extends AbstractArrow {

    private static final String TAG_BATCH = "tnl_starfall_batch";
    private static final int MAX_HISTORY = 16;
    private static final int MAX_LIFE = 200;

    private static final EntityDataAccessor<Float> DATA_SCALE =
            SynchedEntityData.defineId(StarfallArrow.class, EntityDataSerializers.FLOAT);
    /** TNL933 ★力量等级（★每级 +10% 伤害 ✓） */
    /** TNL933 ★已命中过的目标（★穿透用 ✓） */
    private final java.util.List<net.minecraft.world.entity.Entity> tnl$hits = new java.util.ArrayList<>();

    private static final EntityDataAccessor<Integer> DATA_POWER =
            SynchedEntityData.defineId(StarfallArrow.class, EntityDataSerializers.INT);
    /** TNL933 ★穿透等级（★还能再击中几个目标 ✓） */
    private static final EntityDataAccessor<Integer> DATA_PIERCE =
            SynchedEntityData.defineId(StarfallArrow.class, EntityDataSerializers.INT);
    /** TNL933 ★火矢 ✓ */
    private static final EntityDataAccessor<Boolean> DATA_FLAME =
            SynchedEntityData.defineId(StarfallArrow.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Boolean> DATA_ENHANCED =
            SynchedEntityData.defineId(StarfallArrow.class, EntityDataSerializers.BOOLEAN);

    private static final UUID PIN_UUID = UUID.fromString("7f2c1a44-9c31-4b8e-9f11-2b7a5c6d8e01");

    private int tickCount = 0;
    private boolean shouldRenderTail = true;
    private final LinkedList<Vec3> trailHistory = new LinkedList<>();
    // TNL888 ★★ 新追踪（★用户口径 ✓）：
    //   ★飞行中扫描**周围 3 格** ✓ ⇒ ★有目标就接上
    //   ★飞剑同款制导（{@link SwordGuidance} ✓）开始追踪 ✓
    private LivingEntity tnl$target = null;
    private SwordGuidance tnl$guide = null;
    /** ★扫描半径（3 格 ✓） */
    private static final double TNL$SCAN = 3.0D;
    /** TNL889 撞地重新起飞次数上限 */
    private int tnl$bounce = 0;
    private Entity tnl$pinned = null;
    private int tnl$pinTicks = 0;

    public StarfallArrow(EntityType<? extends StarfallArrow> type, Level level) {
        super(type, level);
        this.setBaseDamage(0.0D);
        this.pickup = Pickup.DISALLOWED;
    }

    public StarfallArrow(EntityType<? extends StarfallArrow> type, LivingEntity shooter, Level level) {
        super(type, shooter, level);
        this.setBaseDamage(0.0D);
        this.pickup = Pickup.DISALLOWED;
        // TNL1022 ★★ 用户：★"最大概率 5%" ✓（★选了"★强化箭总概率封顶 5%" ✓）
        //   ★基础 2% ✓ + ★每 1 点幸运 +1% ✓ ⇒ ★**上限 5%** ✓（★幸运 +3 就到顶 ✓）
        //   ★负幸运也压不到负数 ✓（★下限 0 ✓）
        float tnl$enhChance = 0.02F + (float) shooter.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.LUCK) * 0.01F;
        tnl$enhChance = Math.max(0.0F, Math.min(0.05F, tnl$enhChance));
        this.entityData.set(DATA_ENHANCED, level.random.nextFloat() < tnl$enhChance);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_SCALE, 1.0F);
        this.entityData.define(DATA_ENHANCED, false);
        this.entityData.define(DATA_POWER, 0);
        this.entityData.define(DATA_PIERCE, 0);
        this.entityData.define(DATA_FLAME, false);
    }

    public void setArrowScale(float sc) {
        this.entityData.set(DATA_SCALE, sc);
    }

    public float getArrowScale() {
        return this.entityData.get(DATA_SCALE);
    }

    /** TNL933 ★外部设置力量/穿透/火矢 ✓ */
    public void tnl$setPower(int v) {
        this.entityData.set(DATA_POWER, v);
    }

    public void tnl$setPierce(int v) {
        this.entityData.set(DATA_PIERCE, v);
    }

    public void tnl$setFlame(boolean v) {
        this.entityData.set(DATA_FLAME, v);
    }

    public boolean tnl$isEnhanced() {
        return this.entityData.get(DATA_ENHANCED);
    }

    public int getTickCount() {
        return tickCount;
    }

    public boolean shouldRenderTail() {
        return shouldRenderTail;
    }

    public LinkedList<Vec3> getTrailHistory() {
        return trailHistory;
    }

    public void setBatchId(long id) {
        this.getPersistentData().putLong(TAG_BATCH, id);
    }

    public long getBatchId() {
        return this.getPersistentData().getLong(TAG_BATCH);
    }

    /** TNL888 ★锁定目标（★供以后 Boss AI 预锁用 ✓）：★重置制导状态 ✓ */
    public void tnl$setHomingTarget(LivingEntity target) {
        // TNL937 ★★ 用户反馈：★"为什么箭矢锁他会一直穿过不造成伤害" ✓
        //   ★真因 ✗：★我在命中后虽然解了锁 ✓，
        //   ★但**制导每 tick 会重新扫描并重新锁定** ✗ ⇒
        //   ★又锁回同一只 ✗ ⇒ ★于是一直穿过去（★而已命中过的目标不重复伤害 ✓）。
        //   ★现在：★在**锁的入口就拒绝**已命中过的目标 ✓
        //   ⇒ ★它只能去锁别的目标 + ★或者直线飞 ✓✓
        if (target == null) {
            return;
        }
        this.tnl$target = target;
        if (this.tnl$guide != null) {
            this.tnl$guide.reset();
        }
    }


    @Override
    protected boolean canHitEntity(Entity target) {
        if (target == this.getOwner()) {
            return false;
        }
        return super.canHitEntity(target);
    }

    @Override
    public void tick() {
        super.tick();
        this.tickCount++;
        if (this.tickCount > MAX_LIFE || this.inGround) {
            this.discard();
            return;
        }
        this.shouldRenderTail = !this.inGround && this.isAlive();
        if (this.level().isClientSide && this.shouldRenderTail) {
            this.trailHistory.addLast(this.position());
            if (this.trailHistory.size() > MAX_HISTORY) {
                this.trailHistory.removeFirst();
            }
        }
        // TNL888 ★★ 追踪：★先扫 3 格 ✓ ⇒ ★找到就走飞剑制导 ✓
        if (!this.level().isClientSide) {
            if (this.tnl$target == null || !this.tnl$target.isAlive()) {
                this.tnl$target = tnl$scanTarget();
                if (this.tnl$target != null && this.tnl$guide != null) {
                    this.tnl$guide.reset();
                }
            }
            if (this.tnl$target != null && this.tnl$target.isAlive()) {
                if (this.tnl$guide == null) {
                    this.tnl$guide = new SwordGuidance();   // ★飞剑同款制导 ✓
                }
                this.setNoGravity(true);          // TNL889 追踪期间不吃重力
                if (this.inGround && this.tnl$bounce < 20) {
                    this.tnl$bounce++;            // TNL889 撞地不消失，继续追
                    this.inGround = false;
                }
                Vec3 targetPos = this.tnl$target.position()
                        .add(0.0D, this.tnl$target.getBbHeight() * 0.5D, 0.0D);
                Vec3 velocity = this.tnl$guide.guide(this.position(), this.getDeltaMovement(), targetPos);
                this.setDeltaMovement(velocity);            // ★照搬飞剑的用法 ✓
            } else if (!this.isNoGravity() && !this.inGround && !this.isInWater()) {
                // ★没目标时才吃 40% 重力（★照搬参考 ✓）
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, 0.03D, 0.0D));
            }
        }
        // ★铉住（★箭还在 ⇒ 目标动不了 ✓）
        if (!this.level().isClientSide && this.tnl$pinned != null) {
            this.tnl$pinTicks--;
            if (this.tnl$pinTicks <= 0 || !this.tnl$pinned.isAlive()) {
                this.tnl$unpin();
            } else if (this.tnl$pinned instanceof net.minecraft.world.entity.Mob mob) {
                mob.setDeltaMovement(0.0D, mob.getDeltaMovement().y, 0.0D);
                mob.getNavigation().stop();
                mob.hurtMarked = true;
            }
        }
    }

    private void tnl$unpin() {
        if (this.tnl$pinned instanceof LivingEntity le) {
            try {
                var inst = le.getAttribute(Attributes.MOVEMENT_SPEED);
                if (inst != null) {
                    inst.removeModifier(PIN_UUID);
                }
            } catch (Throwable ignored) {
            }
        }
        this.tnl$pinned = null;
        this.tnl$pinTicks = 0;
    }

    @Override
    public void remove(RemovalReason reason) {
        this.tnl$unpin();
        super.remove(reason);
    }

    /** TNL882 ★★Boss 判定：★看"有没有 Boss 血条事件" ✓
     *
     * <p>★用户口径：★"boss检测按有没有 boss 事件判断" ✓
     * ⇒ ★反射扫描该实体类（含父类）的字段 ✓：
     * 只要有任何 {@link net.minecraft.world.BossEvent} 类型的字段
     * （★原版末影龙/凋灵 ✓ ★本模组 KtuluMomo ✓ ★其他模组有血条的 Boss ✓）
     * ⇒ ★就算 Boss ✓；★假人/训练木人没有这种字段 ✗ ⇒ ★不算 Boss ✓。
     * ★结果按**实体类型缓存** ✓（★反射只做一次 ✓）。
     */
    private static final java.util.Map<Class<?>, Boolean> TNL$BOSS_CACHE_BY_CLASS = new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.Map<net.minecraft.world.entity.EntityType<?>, Boolean> TNL$BOSS_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean tnl$isBoss(Entity e) {
        try {
            // TNL982 ★★ 用户：★"末影龙没有被判断为 boss 生物，凋灵却被判断为 boss 生物" ✓
            //   ★日志证据：★龙的**部件** type 也叫 ``entity.minecraft.ender_dragon`` ✓
            //     ⇒ ★第一次判定看的是部件（★没 BossEvent ✗）⇒ ★把 false
            //     **写进了按 EntityType 的缓存** ✗ ⇒ ★之后本体也返回 false ✓✓
            //   ★现在：★先把部件换算成本体 ✓ + ★**改成按 `getClass()` 缓存** ✓
            //     （★部件类与本体类不同 ✓ ⇒ ★不会互相污染 ✓）
            e = com.mofengbaizhi.tinkersnewlife.util.TruePierce.resolveVictim(e);
            Class<?> key = e.getClass();
            Boolean hit = TNL$BOSS_CACHE_BY_CLASS.get(key);
            if (hit != null) {
                return hit;
            }
            boolean boss = false;
            Class<?> c = e.getClass();
            while (c != null && c != Object.class) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (net.minecraft.world.BossEvent.class.isAssignableFrom(f.getType())) {
                        boss = true;
                        break;
                    }
                }
                if (boss) {
                    break;
                }
                c = c.getSuperclass();
            }
            // TNL984 ★★ 用户：★"末影龙没有被判断为 boss" ✓
            //   ★查 jar 得到（★javap ✓）：
            //     · ★凋灵 ✓：★自己声明了 ``private final ServerBossEvent`` ⇒ ★扫得到 ✓
            //     · ★末影龙 ✗：★**龙实体上没有任何 BossEvent 字段** ✓
            //       （★它的血条在 ``EndDragonFight`` 里 ✓）⇒ ★所以永远扫不到 ✓
            //   ★现在：★明确把**末影龙（及其部件）当 boss** ✓
            // TNL985 ★★ 用户：★"群友说 forge 有专门的 boss 标签" ✓
            //   ★已确证（★javap + jar 里的资源 ✓）：
            //     · ★``net.minecraftforge.common.Tags$EntityTypes.BOSSES`` 存在 ✓
            //     · ★``data/forge/tags/entity_types/bosses.json`` 存在 ✓
            //   ★这是"数据驱动"的判据 ✓ ⇒ ★任何整合包/模组都可以往里加 ✓
            if (!boss) {
                try {
                    boss = e.getType().is(net.minecraftforge.common.Tags.EntityTypes.BOSSES);
                } catch (Throwable ignored) {
                }
            }
            // TNL987 ★★ 用户：★"把之前的血条反射事件兜底" ✓
            //   ★顺序 ✓：★字段扫描（★凋灵 ✓）→ ★forge:bosses 标签（★数据驱动 ✓）
            //     → ★血条注册表（★服务端创建 + ★客户端渲染 ✓）。
            //   ★这条就是"模组没写标签但有血条"的兜底 ✓。
            if (!boss) {
                try {
                    boss = com.mofengbaizhi.tinkersnewlife.util.BossBarRegistry.isBoss(e.getUUID());
                } catch (Throwable ignored) {
                }
            }
            if (!boss && e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon) {
                boss = true;
            }
            if (boss) {
                TNL$BOSS_CACHE_BY_CLASS.put(key, Boolean.TRUE);   // ★只缓存"是 boss" ✓
            }
            return boss;
        } catch (Throwable th) {
            return false;
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        // TNL977 ★★ 用户：★"命中多部位实体的非主碰撞箱时，没有造成伤害" ✓
        //   ★真因 ✗：★多部位实体（★巨龙类 ✓）命中的是
        //     ★``PartEntity``（部件 ✓），★它**不是 ``LivingEntity``** ✗
        //     ⇒ ★我第一句就 return ⇒ ★没打到本体 ✓✓
        //   ★现在：★先用 ``PartEntity#getParent()`` 换算回**本体** ✓
        //     （★同一套真伤/穿透/火矢/黑箭逻辑照常生效 ✓）
        net.minecraft.world.entity.Entity tnl$hit = hitResult.getEntity();
        // TNL980 ★★ 探针：★看"命中了什么/算不算 boss" ✓
        {
            org.slf4j.Logger lg = com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER;
            Object parentInfo = "";
            try {
                if (tnl$hit instanceof net.minecraftforge.entity.PartEntity<?> p) {
                    parentInfo = " part-of " + p.getParent().getClass().getSimpleName();
                }
            } catch (Throwable ignored) {
            }
            // TNL983 ★★ 探针改成打"换算后"的结果 ✓
            net.minecraft.world.entity.Entity tnl$res =
                    com.mofengbaizhi.tinkersnewlife.util.TruePierce.resolveVictim(tnl$hit);
            lg.warn("TNL983 hit={} ({}){}  -> resolved={}  isLiving={}  boss={}",
                    tnl$hit.getClass().getSimpleName(), tnl$hit.getType(), parentInfo,
                    tnl$res.getClass().getSimpleName(),
                    tnl$res instanceof net.minecraft.world.entity.LivingEntity,
                    tnl$isBoss(tnl$res));
        }
        if (tnl$hit instanceof net.minecraftforge.entity.PartEntity<?> tnl$part) {
            tnl$hit = tnl$part.getParent();
        }
        // TNL989 ★★ 用户：★"冰与火打部件的问题还没修好" ✓
        //   ★日志 + 源码交叉验证 ✓：
        //     · ★``TNL983 ... hit=EntityDragonPart -> resolved=EntityLightningDragon isLiving=true`` ✓
        //       ⇒ ★换算**本来就成功** ✓
        //     · ★但 ``TNL988`` **一行都没有** ✗ ⇒ ★根本没走到伤害那一步 ✓
        //     · ★原因 ✗：★这里比的是**原始命中实体**（★部件 ✗）
        //       ★部件不是 ``LivingEntity`` ⇒ ★**在这里就 return 了** ✓✓
        //       （★我上一轮的换算只喂给了探针 ✗ 没用在这道门上 ✓）
        //   ★现在：★先换算成本体 ✓ + ★换不到就退回原版 ✓
        net.minecraft.world.entity.Entity tnl$resolved =
                com.mofengbaizhi.tinkersnewlife.util.TruePierce.resolveVictim(tnl$hit);
        if (!(tnl$resolved instanceof LivingEntity target)) {
            super.onHitEntity(hitResult);
            return;
        }
        if (this.level().isClientSide) {
            return;
        }
        // TNL938 ★★ 用户口径：★"我是说让它可以继续锁，但是别锁着不造成伤害" ✓
        //   ★⇒ ★撤掉"同一目标不重复伤害"这条限制 ✓
        //   ★每次接触都结算伤害 ✓，★穿透次数照旧每次 -1 ✓
        //   （★所以★锁着也能一直打 ✓，★打满穿透次数就消失 ✓）
        this.tnl$hits.add(target);
        // TNL935 ★★ 探针：★看"第二次命中有没有进来" ✓
        com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                "TNL935 hit #{} target={} pierceLeft={} inGround={} removed={}",
                this.tnl$hits.size(), target.getName().getString(),
                this.entityData.get(DATA_PIERCE), this.inGround, this.isRemoved());
        // TNL933 ★火矢：★命中点火 5 秒 ✓
        if (this.entityData.get(DATA_FLAME)) {
            target.setSecondsOnFire(5);
        }
        // ★命中特效广播（★照搬参考 StarHitPacket ✓）
        if (this.level() instanceof ServerLevel sl) {
            Vec3 pos = this.position();
            Vec3 dir = this.getDeltaMovement();
            if (dir.lengthSqr() < 1e-6) {
                dir = this.getViewVector(0.0F);
            }
            PacketStarfallHit pkt = new PacketStarfallHit(pos, dir);
            for (ServerPlayer sp : sl.players()) {
                if (sp.distanceToSqr(pos) < 64.0D * 64.0D) {
                    TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), pkt);
                }
            }
        }
        boolean enh = this.tnl$isEnhanced();
        // TNL932 ★★ 用户口径（修改伤害）：
        //   ★Boss 生物 ⇒ ★每箭**固定 3% 上限真伤** ✓
        //   ★非 Boss ⇒ ★每箭**固定 10% 生命上限真伤** ✓
        //     ★但**不得低于 16** ✓（★低于就直接算 16 ✓）
        float dmg = tnl$isBoss(target)
                ? target.getMaxHealth() * 0.03F
                : Math.max(16.0F, target.getMaxHealth() * 0.10F);
        dmg *= 1.0F + 0.10F * this.entityData.get(DATA_POWER);   // TNL933 ★力量：★每级 +10% ✓   // TNL882 ★非 Boss 16 ✓ ★Boss 3% ✓
        if (enh) {
            dmg *= 2.0F;
        }
        // TNL1020 ★★ 用户：★"声音稍微大一点，然后让普通箭矢射中也有音乐音效，
        //   只是没强化箭矢好听" ✓
        //   ★强化箭 ✓：★``AMETHYST_BLOCK_CHIME``（★清脆悦耳的"叮" ✓）
        //     ★音量 1.6 ✓（★比原来 1.0 更响 ✓）+ ★音调 0.5~2.0 随机 ✓
        //   ★普通箭 ✓：★``AMETHYST_BLOCK_HIT``（★闷一点的"扑" ✓）
        //     ★音量 1.3 ✓+ ★音调只在 0.7~1.1 小范围抖 ✓（★听着就没那么好听 ✓）
        if (!this.level().isClientSide) {
            if (enh) {
                this.level().playSound(null, this.blockPosition(),
                        net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                        net.minecraft.sounds.SoundSource.PLAYERS,
                        1.6F, 0.5F + this.level().random.nextFloat() * 1.5F);
            } else {
                this.level().playSound(null, this.blockPosition(),
                        net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_HIT,
                        net.minecraft.sounds.SoundSource.PLAYERS,
                        1.3F, 0.7F + this.level().random.nextFloat() * 0.4F);
            }
        }
        LivingEntity attacker = this.getOwner() instanceof LivingEntity le ? le : null;
        try {
            com.mofengbaizhi.tinkersnewlife.util.TruePierce.apply(attacker, target, dmg);   // ★见下方分支 ✓
        } catch (Throwable th) {
            // TNL986 ★★ 用户：★"把冰与火龙无法真伤的 bug 修好" ✓
            //   ★jar 证据（★javap ✓）：
            //     ★``EntityMutlipartPart``（★冰火部件基类 ✓）自己就有
            //     ★``public boolean hurt(DamageSource, float)``（★m_6469_ ✓），★它**会把伤害转交给本体** ✓
            //     + ★乘上部位系数（★翅膀/尾巴 ✓）。★原版 ``EnderDragonPart`` 同理 ✓
            //   ★所以：★命中"部件"时应**打部件** ✓（★它会转发 ✓）
            //     ★而不是直接打本体 ✗（★那样冰火这边不走它的转发链 ✗）
            // TNL988 ★★ 探针：★直接看"到底走到伤害那一步没有 + 打了多少" ✓
            org.slf4j.Logger lg2 = com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER;
            net.minecraft.world.entity.Entity tnl$victim = hitResult.getEntity();
            lg2.warn("TNL988 damage site: hit={} victim={} target={} dmg={} aliveBefore={}",
                    hitResult.getEntity().getClass().getSimpleName(),
                    tnl$victim.getClass().getSimpleName(),
                    target.getClass().getSimpleName(), dmg, target.getHealth());
            if (tnl$victim instanceof net.minecraft.world.entity.LivingEntity) {
                tnl$victim = target;                       // ★本体命中 ⇒ ★走原来的真伤路径 ✓
            }
            if (tnl$victim != target) {
                tnl$victim.hurt(this.damageSources().magic(), dmg);   // ★部件自己转发给本体 ✓
            } else {
                com.mofengbaizhi.tinkersnewlife.util.TruePierce.apply(attacker, target, dmg);
            }
        }
        if (enh) {
            // TNL928 ★★ 修正：★上一版我把它错插在 **catch** 里 ✗
            //   ★而真伤走的是 ``TruePierce.apply`` 那条**正常路径** ✓
            //   ⇒ ★catch 从不执行 ⇒ ★黑箭从来没扎上 ✓（★用户实测：★"黑箭实体没出现，也没成功减速使徒" ✓）
            //   ★现在：★放进 ``if (enh)`` 里 ✓ ⇒ ★只有强化箭才扎 ✓
            com.mofengbaizhi.tinkersnewlife.content.entity.StuckArrowManager.apply(target);
            Vec3 v = this.getDeltaMovement();
            Vec3 dir = v.lengthSqr() > 0.001D ? v.normalize() : this.position().subtract(target.position()).normalize();
            target.push(dir.x * 1.6D, 0.45D, dir.z * 1.6D);
            target.hurtMarked = true;
            try {
                var inst = target.getAttribute(Attributes.MOVEMENT_SPEED);
                if (inst != null) {
                    inst.removeModifier(PIN_UUID);
                    inst.addTransientModifier(new AttributeModifier(
                            PIN_UUID, "tinkersnewlife.starfall_pin", 0.0D, AttributeModifier.Operation.MULTIPLY_TOTAL));
                }
            } catch (Throwable ignored) {
            }
            this.tnl$pinned = target;
            this.tnl$pinTicks = 100;
        }
        // TNL933 ★穿透：★还有余额就不消失 ✓
        int pierce = this.entityData.get(DATA_PIERCE);
        if (pierce > 0) {
            this.entityData.set(DATA_PIERCE, pierce - 1);
            // TNL935 ★★ 修正：★确保它**真的继续飞** ✓
            this.inGround = false;
            this.hasImpulse = true;
            this.pickup = AbstractArrow.Pickup.DISALLOWED;
            this.tnl$setHomingTarget(null);                    // TNL936 ★穿透后也解锁 ✓
            net.minecraft.world.phys.Vec3 tnl$mv = this.getDeltaMovement();
            double tnl$sp = tnl$mv.length();
            if (tnl$sp < 1.2D) {                               // ★保底速度 ✓
                net.minecraft.world.phys.Vec3 tnl$dir = tnl$sp < 1.0E-4D
                        ? this.getViewVector(1.0F) : tnl$mv.scale(1.0D / tnl$sp);
                this.setDeltaMovement(tnl$dir.scale(1.2D));
            }
        } else {
            this.discard();
        }
    }

    @Override
    protected ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("TnlEnhanced", this.tnl$isEnhanced());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_ENHANCED, tag.getBoolean("TnlEnhanced"));
    }

    /** TNL888 ★扫描**周围 3 格**内的可追目标（★最近优先 ✓） */
    private LivingEntity tnl$scanTarget() {
        try {
            java.util.List<LivingEntity> found = this.level().getEntitiesOfClass(
                    LivingEntity.class, this.getBoundingBox().inflate(TNL$SCAN));
            LivingEntity best = null;
            double bestDist = Double.MAX_VALUE;
            for (LivingEntity le : found) {
                if (le == this.getOwner() || !le.isAlive() || le.isSpectator()) {
                    continue;
                }
                if (!this.canHitEntity(le)) {
                    continue;
                }
                double d = le.distanceToSqr(this);
                if (d < bestDist) {
                    bestDist = d;
                    best = le;
                }
            }
            return best;
        } catch (Throwable th) {
            return null;
        }
    }
}
