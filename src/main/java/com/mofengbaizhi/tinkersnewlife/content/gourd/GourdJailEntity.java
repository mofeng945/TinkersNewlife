package com.mofengbaizhi.tinkersnewlife.content.gourd;

import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 狱门疆（放置的咒具实体）：
 * <ul>
 *   <li>空闲形态：检测 5×5 范围停留超 5 秒的生物（含玩家，不含放置者本人）→ 进入封印动画</li>
 *   <li>封印动画 5 秒（100 tick）：本体拆为 8 块扩散至目标并收缩合拢，期间目标被定身不能移动；
 *       动画结束后：玩家 → 传送至狱门疆维度球笼；普通生物 → 记录完整 NBT 后清除实体</li>
 *   <li>已封印形态：不再封印；右键拾取（仅放置者）；天逆鉾右键/雅各布天梯照射 → 释放：
 *       玩家传送回狱门疆位置；生物在狱门疆位置重新生成</li>
 * </ul>
 */
public class GourdJailEntity extends Entity {

    private static final EntityDataAccessor<Boolean> SEALED =
            SynchedEntityData.defineId(GourdJailEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> ANIM =
            SynchedEntityData.defineId(GourdJailEntity.class, EntityDataSerializers.INT);

    public static final int STAY_TICKS = 100;       // 停留 5 秒触发
    public static final int SEAL_TICKS = 100;       // 封印动画 5 秒
    public static final double DETECT_RADIUS = 2.5; // 5×5 范围（水平）

    /** 放置者 UUID（仅放置者可拾取；本人免疫封印） */
    private UUID ownerId = null;
    /** 状态：0 空闲 1 封印动画中 2 已封印 */
    private int state = 0;
    /** 封印动画目标 */
    private UUID sealTargetId = null;
    /** 封印动画锁定的目标位置（动画期间定身） */
    private Vec3 sealTargetPos = null;
    /** 被封印者 UUID（玩家被封印到维度时记录；动画目标） */
    private UUID prisonerId = null;
    /** 球笼坐标（玩家封印） */
    private BlockPos cagePos = null;
    /** 普通生物被封印时保存的完整 NBT（释放时重新生成） */
    private CompoundTag prisonerNbt = null;
    /** 被封印玩家名字（封印时记录，供已封印物品提示显示；生物名由 NBT 推导） */
    private String prisonerName = null;

    /** 空闲形态检测：UUID → 进入范围 gameTime */
    private final Map<UUID, Long> enterTimes = new HashMap<>();

    public GourdJailEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public GourdJailEntity(Level level, Vec3 pos, UUID ownerId) {
        this(ModEntities.GOURD_JAIL.get(), level);
        setPos(pos);
        this.ownerId = ownerId;
        setSealed(false);
    }

    public boolean isSealed() { return entityData.get(SEALED); }
    public void setSealed(boolean sealed) { entityData.set(SEALED, sealed); }
    public int getAnim() { return entityData.get(ANIM); }
    public void setAnim(int t) { entityData.set(ANIM, t); }
    public BlockPos getCagePos() { return cagePos; }
    public void setCagePos(BlockPos pos) { this.cagePos = pos; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getPrisoner() { return prisonerId; }
    public CompoundTag getPrisonerNbt() { return prisonerNbt; }
    public String getPrisonerName() { return prisonerName; }
    public boolean isPlayerPrisoner() { return prisonerId != null && prisonerNbt == null; }
    public int getState() { return state; }
    public void setState(int s) { this.state = s; }
    public void setPrisoner(UUID id) { this.prisonerId = id; }

    /** 从已封印物品恢复封印状态（生物：NBT；玩家：UUID+球笼坐标） */
    public void restoreSealed(UUID owner, CompoundTag nbt) {
        this.ownerId = owner;
        if (nbt == null) return;
        this.prisonerId = nbt.hasUUID(GourdJailHandler.KEY_PRISONER)
                ? nbt.getUUID(GourdJailHandler.KEY_PRISONER) : null;
        this.cagePos = GourdJailHandler.readPos(nbt, GourdJailHandler.KEY_CAGE_POS);
        this.prisonerNbt = nbt.contains(GourdJailHandler.KEY_MOB_NBT)
                ? nbt.getCompound(GourdJailHandler.KEY_MOB_NBT) : null;
        this.prisonerName = nbt.contains(GourdJailHandler.KEY_PRISONER_NAME, net.minecraft.nbt.Tag.TAG_STRING)
                ? nbt.getString(GourdJailHandler.KEY_PRISONER_NAME) : null;
        setState(2);
        setSealed(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(SEALED, false);
        entityData.define(ANIM, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            tickClientFx();
            return;
        }
        ServerLevel server = (ServerLevel) level();
        switch (state) {
            case 0 -> tickIdle(server);
            case 1 -> tickSealing(server);
            case 2 -> {} // 已封印：无操作
        }
    }

    /**
     * 客户端视觉效果：使用同步数据（SEALED / ANIM）判断状态。
     * 注意：本地 state 字段不会同步到客户端（恒为 0），判断必须基于 entityData。
     */
    private void tickClientFx() {
        if (isSealed()) {
            // 已封印：金色流转粒子
            if (random.nextInt(4) == 0) {
                level().addParticle(net.minecraft.core.particles.ParticleTypes.PORTAL,
                        getX() + (random.nextDouble() - 0.5) * 0.9, getY() + random.nextDouble() * 1.0,
                        getZ() + (random.nextDouble() - 0.5) * 0.9, 0, 0.03, 0);
            }
        } else if (getAnim() > 0 && getAnim() < SEAL_TICKS) {
            // 封印动画中：暗紫收束粒子点缀
            if (random.nextInt(2) == 0) {
                level().addParticle(net.minecraft.core.particles.ParticleTypes.PORTAL,
                        getX() + (random.nextDouble() - 0.5) * 1.4, getY() + random.nextDouble() * 1.4,
                        getZ() + (random.nextDouble() - 0.5) * 1.4, 0, 0, 0);
            }
        }
    }

    /** 空闲：检测停留超时生物（不含放置者本人） */
    private void tickIdle(ServerLevel server) {
        long now = server.getGameTime();
        Set<UUID> seen = new HashSet<>();
        List<LivingEntity> inRange = server.getEntitiesOfClass(LivingEntity.class,
                new AABB(getX() - DETECT_RADIUS, getY() - 2, getZ() - DETECT_RADIUS,
                        getX() + DETECT_RADIUS, getY() + 2, getZ() + DETECT_RADIUS),
                e -> e.isAlive() && !e.isSpectator());
        for (LivingEntity e : inRange) {
            // 放置者本人免疫
            if (ownerId != null && e.getUUID().equals(ownerId)) continue;
            seen.add(e.getUUID());
            Long enter = enterTimes.putIfAbsent(e.getUUID(), now);
            if (enter == null) continue;
            if (now - enter >= STAY_TICKS) {
                // 开始封印动画：锁定目标 + 定身
                sealTargetId = e.getUUID();
                sealTargetPos = e.position();
                enterTimes.clear();
                state = 1;
                setAnim(0);
                if (e instanceof ServerPlayer sp) {
                    sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.tinkersnewlife.gourd.sealed"), true);
                }
                return;
            }
        }
        enterTimes.keySet().removeIf(id -> !seen.contains(id));
    }

    /** 封印动画：5 秒，期间目标定身不能移动 */
    private void tickSealing(ServerLevel server) {
        int anim = getAnim() + 1;
        setAnim(anim);
        // 目标定身
        if (sealTargetId != null) {
            Entity target = server.getEntity(sealTargetId);
            if (target instanceof LivingEntity living && living.isAlive()) {
                living.setNoGravity(true);
                living.setDeltaMovement(Vec3.ZERO);
                if (sealTargetPos != null) {
                    living.teleportTo(sealTargetPos.x, sealTargetPos.y, sealTargetPos.z);
                }
                // 全程暗紫收束粒子（封印感官：目标被拉扯向狱门疆）
                double tx = living.getX(), ty = living.getY() + living.getBbHeight() / 2, tz = living.getZ();
                if (anim % 3 == 0) {
                    server.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                            tx, ty, tz, 4, 0.5, 0.5, 0.5, 0.0);
                }
                // 后 1/3：剧烈收缩粒子
                if (anim > SEAL_TICKS * 2 / 3 && anim % 2 == 0) {
                    server.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                            tx, ty, tz, 12, 0.2, 0.2, 0.2, 0.05);
                }
            } else {
                // 目标死亡/消失：取消封印（恢复正常重力）
                Entity lost = target; // 可能为 null（已卸载）
                state = 0;
                setAnim(0);
                sealTargetId = null;
                if (lost instanceof LivingEntity le && le.isAlive()) le.setNoGravity(false);
                return;
            }
        }
        // 动画完成：执行真正封印
        if (anim >= SEAL_TICKS) {
            finishSeal(server);
        }
    }

    /** 动画结束：玩家 → 维度球笼；普通生物 → 记录 NBT 并清除实体 */
    private void finishSeal(ServerLevel server) {
        Entity target = sealTargetId != null ? server.getEntity(sealTargetId) : null;
        boolean isPlayer = target instanceof ServerPlayer;
        if (isPlayer) {
            BlockPos cage = GourdJailHandler.sealPlayerToDimension(server.getServer(), (ServerPlayer) target);
            if (cage == null) { state = 0; setAnim(0); return; }
            cagePos = cage;
            prisonerId = sealTargetId;
            // 记录玩家名字：已封印物品 tooltip 只显示名字，客户端无法按 UUID 反查
            prisonerName = target.getName().getString();
        } else if (target instanceof LivingEntity living) {
            // 记录完整 NBT（含实体类型 id）后清除实体
            prisonerNbt = new CompoundTag();
            living.saveWithoutId(prisonerNbt);
            // saveWithoutId 不写 "id"，loadEntityRecursive 需要它才能识别类型
            prisonerNbt.putString("id", net.minecraft.world.entity.EntityType.getKey(living.getType()).toString());
            prisonerId = living.getUUID();
            if (isBossLike(living)) {
                // ⭐ Boss 封印 = 击败：结算对应 Boss 状态机（末地龙战等），避免"卡消失"后被判定战斗未结束而重刷一条
                sealAsDefeat((ServerLevel) server, living);
            } else {
                // ⭐ 用清除逻辑链（⭐ §1185 ✓）—— ⭐ 比单一句 `remove` 更能对付"防清除" ✓
                forcePurge(living);
            }
            // 无需球笼坐标（生物无维度）
        } else if (target != null) {
            // 其他实体：清除（无恢复）
            target.remove(Entity.RemovalReason.DISCARDED);
            prisonerId = target.getUUID();
        }
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.PLAYERS, 1.0F, 1.2F);
        state = 2;
        setSealed(true);
        sealTargetId = null;
        sealTargetPos = null;
    }

    /** Boss 判定：末影龙/凋灵，以及高血量（≥300）怪物兜底（覆盖多数模组 Boss） */
    private static boolean isBossLike(LivingEntity e) {
        if (e instanceof EnderDragon || e instanceof WitherBoss) return true;
        return e instanceof Mob && e.getMaxHealth() >= 300.0;
    }

    /**
     * 封印即击败：Boss 被封印时结算其状态机，避免"实体被移除但战斗未结束"导致原处重刷。
     * 末影龙 → 直接通知末地龙战已击杀（开传送门/出龙蛋，不再补刷）；
     * 其余 Boss → 走完整死亡结算（触发事件/掉落/各自的击败逻辑）。
     */
    private static void sealAsDefeat(ServerLevel jailLevel, LivingEntity living) {
        if (living instanceof EnderDragon dragon) {
            // 通知末地龙战已击杀（方法名跨映射/版本可能不同，反射按候选名调用，失败静默）
            try {
                MinecraftServer ms = jailLevel.getServer();
                ServerLevel end = ms.getLevel(Level.END);
                if (end != null) {
                    Object fight = invokeAny(end, null, "dragonFight", "getDragonFight");
                    if (fight != null) {
                        invokeAny(fight, dragon, "setDragonKilled", "onDragonKilled", "setDragonDead");
                    }
                }
            } catch (Throwable ignored) {
            }
            // ⭐ 用清除逻辑链（⭐ §1185 ✓ ⭐ 见 {@link #forcePurge} ✓）
            forcePurge(dragon);
            return;
        }
        // ⭐⭐ §1184 **改成「从实体列表清除」，不再走死亡** ✗
        //   （⭐ 用户口径 ✓ 2026-10-10：「**再把狱门疆，咒灵操术，式神相关的收回动作从 die() 改成
        //     实体列表清除对应实体**」＋「**不是普通 remove，是列表清除**」✓）
        //
        //   ⚠ 原来这里是 ⭐ `living.hurt(genericKill(), Float.MAX_VALUE)` ✗
        //   —— ⭐ 那是「**用伤害把它打死**」✗ ⇒ ⭐ 会触发死亡事件／掉落／死亡信息／击杀统计 ✓
        //   ⭐ 现在改成 ⭐ **`remove(Entity.RemovalReason.DISCARDED)`** ✗
        //   ⇒ ⭐ 实体从 ⭐ `PersistentEntitySectionManager`（⭐ 服务端的实体跟踪表 ✓）里**直接摘掉** ✓
        //   ⇒ ⭐ **不发死亡事件、不掉落、不计击杀** ✓ ✓（⭐ 这就是「**列表清除**」的正规入口 ✓
        //     ⚠ 1.20.1 里没有别的公开 API 能做到这件事 ✓）。
        //
        //   <h3>⚠ 代价（⭐ 如实 ✓ 用户已明确要这样 ✓）</h3>
        //   ⚠ 原来用死亡是为了 ⭐ **通知 Boss 自己的状态机** ✗
        //   （⭐ 原注释：「⭐ 避免"卡消失"后被判定战斗未结束而重刷一条 ✓」✓）
        //   ⇒ ⭐ 现在直接摘除 ⇒ ⚠ ⭐ 这类 Boss 可能被它自己的重刷逻辑**再刷一条** ✗
        //   ⭐ 但「**封印即击败**」的语义下这可接受 ✓ ⭐ 且用户明确要求 ✓。
        //   ⚠ 末影龙那条**一直是单独处理**的（⭐ 反射调 `setDragonKilled` ✓）⇒ ⭐ 不受影响 ✓。
        // ⭐ 用清除逻辑链（⭐ §1185 ✓ ⭐ 见 {@link #forcePurge} ✓）——
        //   ⭐ 它会 ⭐ 抹血 → 摘除 → 抹血 → remove → kill → 摘除 → 抹血 ✓
        //   ⚠ 且 ⭐ **保证 `kill()` 在 `remove` 之后** ✗ ⇒ ⭐ `hurt()` 早退 ⇒ ⭐ **不 `die()` ⇒ 不掉战利品** ✓ ✓
        forcePurge(living);
    }

    /**
     * ⭐⭐ §1185 <b>实体清除逻辑链</b>（⭐ 用户口径 ✓ 2026-10-10 逐字 ✓）：
     * <blockquote>
     * ⭐ 「**清除逻辑链：血量设置0，随后清除实体，随后血量设置0，随后remove，再kill，
     * 再清除实体，再血量设置0（不调用die产生战利品）**」✓
     * </blockquote>
     *
     * <h2>⭐ 设计意图（⭐ 多层冗余交叉覆盖 ✗）</h2>
     * ⭐ 各模组的"防清除"手法不一样 ✗（⭐ 覆写 `remove` ✓ ⭐ 覆写 `isRemoved` ✓ ⭐ `tick` 自愈 ✓
     * ⭐ Boss 状态机重刷 ✓）⇒ ⭐ **单一步骤都可能被拦** ✗ ⇒ ⭐ 交替用多种手段**叠着来** ✓
     * ⭐ 总有一步能生效 ✓ ✓。
     *
     * <h2>⚠⚠ 关键：`kill()` 必须在 `remove` **之后**（⭐ 否则会掉战利品 ✗）</h2>
     * ⚠ `LivingEntity#kill()` 就是 ⭐ `hurt(damageSources().genericKill(), Float.MAX_VALUE)` ✗
     * ⇒ ⭐ 它会**正常触发 `die()`** ✗ ⇒ ⭐ 产生战利品／经验／死亡信息 ✓
     * ⭐ 而用户明确要 ⭐「**不调用 die 产生战利品**」✓
     * ⇒ ⭐ 解法：⭐ 让 `kill()` 落在 ⭐ **`remove(DISCARDED)` 成功之后** ✗
     * ⭐ 那时 `isRemoved() == true` ✗ ⭐ `hurt()` **第一步就早退** ✓ ⭐ **不会 `die()`** ✓ ✓。
     * <p>⚠ **兜底**：⭐ 万一 `remove` 被别人的覆写拦掉 ✗ ⇒ ⭐ `kill()` 就会真把它打死 ✗
     * ⇒ ⭐ 所以**先打上** {@code KEY_SUPPRESS_LOOT} ✗ ⇒ ⭐ 就算真死了也**不掉战利品** ✓ ✓
     * （⭐ 那套抑制机制本仓已有 ✓ ⭐ `BossFightHandler` 也在用 ✓）。
     *
     * <h2>⚠ 关于第 ④ 步「remove」</h2>
     * ⭐ 1.20.1 里公开 API 只有 ⭐ `remove(RemovalReason)`（⭐ `discard()` 即它 ✓）
     * ⇒ ⭐ 这里**连续调两次** ✗ ⭐ 因为有的模组只在**第一次**放行／⭐ 第二次才认 ✓。
     *
     * @param living 要清除的实体
     */
    public static void forcePurge(net.minecraft.world.entity.LivingEntity living) {
        if (living == null || living.level().isClientSide) {
            return;
        }
        // ⭐ ⓪ 先打"抑制掉落"标记 ✗ —— ⭐ 保证万一步骤 ⑤ 真打死它也不掉战利品 ✓
        try {
            living.getPersistentData().putBoolean(GourdJailHandler.KEY_SUPPRESS_LOOT, true);
            com.mofengbaizhi.tinkersnewlife.content.curse.CurseDeath.mark(living);
        } catch (Throwable ignored) {
        }
        // ⭐ ① 血量设置 0 ✓
        try {
            living.setHealth(0.0F);
        } catch (Throwable ignored) {
        }
        // ⭐ ② 清除实体 ✓
        try {
            living.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        } catch (Throwable ignored) {
        }
        // ⭐ ③ 随后血量设置 0 ✓（⭐ 防上一步的回调里被回血 ✓）
        try {
            living.setHealth(0.0F);
        } catch (Throwable ignored) {
        }
        // ⭐ ④ 随后 remove ✓（⭐ 再摘一次 ✗ 有的模组只在第二次才认 ✓）
        try {
            if (!living.isRemoved()) {
                living.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            }
        } catch (Throwable ignored) {
        }
        // ⭐ ⑤ 再 kill ✓ —— ⚠ 只有"已经摘掉"时才安全 ✗
        //    ⭐ 已 removed ⇒ `hurt()` 早退 ⇒ ⭐ **不会 `die()`** ✓ ✓
        //    ⚠ 未被拦的话会真打死 ⇒ ⭐ 但 ⓪ 已抑制掉落 ✓
        try {
            living.kill();
        } catch (Throwable ignored) {
        }
        // ⭐ ⑥ 再清除实体 ✓
        try {
            living.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        } catch (Throwable ignored) {
        }
        // ⭐ ⑦ 再血量设置 0 ✓（⭐ 收尾 ✗ ⭐ **全程不主动调 `die()`** ✓ ⇒ ⭐ 不产生战利品 ✓）
        try {
            living.setHealth(0.0F);
        } catch (Throwable ignored) {
        }
        // ⭐⭐ ⑧ **栈尾最终校验**（⭐ 用户口径 ✓ 2026-10-10：「**能不能再栈尾再检测一次清除实体**」✓）
        //   ⭐ 逻辑：⭐ 走到这里若 ⭐ **实体还在** ✗ ⇒ ⭐ **再摘一次** ✓ ＋ ⭐ **反射兜 `setRemoved`** ✓
        //   ⚠ 反射是因为 ⭐ `Entity#setRemoved(RemovalReason)` 的可见性跨版本不稳 ✗
        //     ⭐ 反射能拿到就调 ✓ ⭐ 拿不到就跳过 ✓（⭐ 反正前面 ②④⑥ 已经调过两次 `remove` ✓）
        //   ⚠ 若**做完这一切它还在** ✗ ⇒ ⭐ 说明被某个模组**死死护住** ✓
        //     ⇒ ⭐ **记一条日志** ✗ ⭐ 便于实机直接定位是哪个实体/模组 ✓（⭐ 不刷屏：⭐ 每个实体只报一次 ✓）。
        try {
            if (!living.isRemoved()) {
                living.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            }
            if (!living.isRemoved()) {
                // ⭐ 反射兜底：⭐ 直接调 `setRemoved`（⭐ 绕开可能被覆写的 `remove` ✓）
                try {
                    java.lang.reflect.Method m = net.minecraft.world.entity.Entity.class
                            .getDeclaredMethod("setRemoved",
                                    net.minecraft.world.entity.Entity.RemovalReason.class);
                    m.setAccessible(true);
                    m.invoke(living, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                } catch (Throwable ignored) {
                    // ⭐ 拿不到就跳过 ✓（⭐ 前面已经试过三次 `remove` ✓）
                }
            }
            if (!living.isRemoved()) {
                try {
                    living.setHealth(0.0F);
                } catch (Throwable ignored) {
                }
                // ⚠ **只报一次** ✗（⭐ 用持久标记去重 ✓ 免得每 tick 刷屏 ✓）
                if (!living.getPersistentData().getBoolean("tnl_purge_failed_logged")) {
                    living.getPersistentData().putBoolean("tnl_purge_failed_logged", true);
                    com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                            "[狱门疆] 清除链走完仍未移除实体：{}（{}）UUID={} ⇒ 疑似被某个模组的防清除逻辑护住 ✓",
                            living.getName().getString(),
                            net.minecraft.world.entity.EntityType.getKey(living.getType()).toString(),
                            living.getUUID());
                }
            }
        } catch (Throwable ignored) {
            // ⭐ 校验本身出错也不能连累玩法 ✗
        }
    }

    /** 反射调用：按候选方法名在 target 上找方法并调用（无参或带单参数），失败返回 null */
    private static Object invokeAny(Object target, Object arg, String... names) throws ReflectiveOperationException {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            for (String name : names) {
                try {
                    java.lang.reflect.Method m = arg == null
                            ? clazz.getDeclaredMethod(name)
                            : clazz.getDeclaredMethod(name, arg.getClass());
                    m.setAccessible(true);
                    return m.invoke(target, arg == null ? new Object[0] : new Object[]{arg});
                } catch (NoSuchMethodException ignored) {
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    /** 释放：玩家 → 清除球笼 + 传送回狱门疆位置；生物 → 狱门疆位置重新生成；随后狱门疆碎裂消失 */
    public void releasePrisonerAndDestroy() {
        if (level().isClientSide || state != 2) {
            breakGlassFx();
            discard();
            return;
        }
        ServerLevel server = (ServerLevel) level();
        Vec3 at = position().add(0, 0.125, 0);
        if (isPlayerPrisoner() && prisonerId != null) {
            GourdJailHandler.releasePlayerFromDimension(server.getServer(), cagePos, prisonerId, server, at);
        } else if (prisonerNbt != null) {
            // 生物：重新生成（移除旧 UUID，让系统分配新 UUID，避免与已删除实体残留冲突）
            CompoundTag reviveTag = prisonerNbt.copy();
            reviveTag.remove("UUID");
            reviveTag.remove("UUIDMost");
            reviveTag.remove("UUIDLeast");
            Entity revived = EntityType.loadEntityRecursive(reviveTag, server, e -> e);
            if (revived != null) {
                revived.moveTo(at.x, at.y, at.z, random.nextFloat() * 360, 0);
                if (revived instanceof Mob mob) mob.setNoGravity(false);
                server.addFreshEntity(revived);
                if (revived instanceof ServerPlayer sp) {
                    sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.tinkersnewlife.gourd.unsealed"), true);
                }
            }
        }
        breakGlassFx();
        discard();
    }

    /** 狱门疆碎裂：玻璃破碎音效 + 玻璃方块破坏粒子 */
    private void breakGlassFx() {
        if (level().isClientSide) return;
        ServerLevel server = (ServerLevel) level();
        server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                        net.minecraft.core.particles.ParticleTypes.BLOCK,
                        net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState()),
                getX(), getY() + 0.125, getZ(), 24, 0.35, 0.35, 0.35, 0.15);
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK,
                SoundSource.BLOCKS, 1.0F, 0.9F + random.nextFloat() * 0.3F);
    }

    // 小实体：无碰撞推动、不可被攻击，但可被选中交互
    @Override public boolean isPushable() { return false; }
    @Override public boolean isPickable() { return true; }
    @Override public boolean isAttackable() { return false; }

    // ============================================================
    //  持久化：狱门疆实体必须随区块保存，否则 chunk 卸载后
    //  sealed 状态与囚犯 NBT 全部丢失（释放无从谈起）
    // ============================================================

    private static final String TAG_OWNER = "Owner";
    private static final String TAG_STATE = "State";
    private static final String TAG_CAGE = "CagePos";
    private static final String TAG_PRISONER = "PrisonerId";
    private static final String TAG_MOB_NBT = "MobNbt";
    private static final String TAG_PRISONER_NAME = "PrisonerName";

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) tag.putUUID(TAG_OWNER, ownerId);
        tag.putInt(TAG_STATE, state);
        if (cagePos != null) tag.putLong(TAG_CAGE, cagePos.asLong());
        if (prisonerId != null) tag.putUUID(TAG_PRISONER, prisonerId);
        if (prisonerNbt != null) tag.put(TAG_MOB_NBT, prisonerNbt);
        if (prisonerName != null) tag.putString(TAG_PRISONER_NAME, prisonerName);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ownerId = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
        state = tag.getInt(TAG_STATE);
        cagePos = tag.contains(TAG_CAGE) ? BlockPos.of(tag.getLong(TAG_CAGE)) : null;
        prisonerId = tag.hasUUID(TAG_PRISONER) ? tag.getUUID(TAG_PRISONER) : null;
        prisonerNbt = tag.contains(TAG_MOB_NBT) ? tag.getCompound(TAG_MOB_NBT) : null;
        prisonerName = tag.contains(TAG_PRISONER_NAME, net.minecraft.nbt.Tag.TAG_STRING)
                ? tag.getString(TAG_PRISONER_NAME) : null;
        setSealed(state == 2);
        setAnim(0);
        // 若加载时处于动画中途（异常卸载），重置为空闲态等待新目标
        if (state == 1) {
            state = 0;
            sealTargetId = null;
            sealTargetPos = null;
        }
    }
}
