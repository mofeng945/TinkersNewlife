package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.PuppetUtil;
import com.mofengbaizhi.tinkersnewlife.content.item.SpearItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>长矛的冲锋与戳刺</b> —— §836 版：把原版 1.21.11 的 {@code KineticWeapon} / {@code PiercingWeapon}
 * 逐条移植到 1.20.1（真源码来自官方未混淆客户端 ✓ 反编译件在 {@code build/tmp-mcsrc/mc1211/src/} ✓）。
 *
 * <h2>冲锋：原版 {@code KineticWeapon#damageEntities} 的等价实现</h2>
 * <ol>
 *   <li>{@code ticksUsed = 使用总时长 - 剩余时长} ✓，<b>小于 {@link #DELAY_TICKS} 一律不生效</b> ✓
 *       （＝原版 {@code delayTicks} ✓ 0.6 秒的"蓄力前摇" ✓ 然后才轮到三个阶段 ✓）；</li>
 *   <li>速度口径照原版 {@code KineticWeapon.getMotion}：{@code 已知速度 × 20} ✓
 *       （非玩家的乘客取**根载具** ✓ ＝骑马冲锋算马的速度 ✓）；
 *       {@code 攻击者速度投影 = 视线 · 我的运动} ✓、{@code 相对速度 = max(0, 我 - 目标)} ✓；</li>
 *   <li>三个阶段是**三个时间窗**，各自带速度门槛 ✓（原版用 {@code Condition(maxDurationTicks, minSpeed, minRelativeSpeed)} ✓）：
 *       <b>Engaged</b>＝下马窗（攻击者速度过线 ⇒ 把骑乘者挑下来 ✓）→ <b>Tired</b>＝击退窗 ✓ → <b>Disengaged</b>＝伤害窗 ✓
 *       ⇒ 按住越久，能做的事越少 ✓ 窗口全过之后冲锋彻底无效（要松手重来 ✓）；</li>
 *   <li>每个目标有 <b>10 tick 的"刚被戳过"记忆</b> ✓（原版 {@code contactCooldownTicks} ✓ 不是"一次冲锋只打一次" ✗）；</li>
 *   <li>伤害是<b>加法</b>不是乘法 ✓：{@code 攻击力 + floor(相对速度 × damageMultiplier)} ✓（原版就这么写 ✓）；</li>
 *   <li>射程查询：从 {@code 眼睛 + 视线×最小距离} 到 {@code 眼睛 + 视线×(最大距离 + 前向速度)} ✓，
 *       中途被方块挡住就截断 ✓（原版 {@code ClipContext.Block.COLLIDER} ✓），命中盒再放宽 0.125 ✓。</li>
 * </ol>
 *
 * <h2>戳刺（原版 {@code PiercingWeapon}）</h2>
 * 原版一次左键会沿射程戳到**所有**目标 ✓（不是只打点中的那个 ✓）。
 * 1.20.1 没有这套管线 ⇒ 我们保留原版（Forge）那次单体攻击 ✓（附魔/锋利/击退等原样走 ✗ 不动它 ✓），
 * 再**额外**把射程内<b>其它</b>目标按同一口径戳一遍 ✓（点中的那个不重复打 ✓ 用同一份"10 tick 记忆"去重 ✓）。
 * 另外照原版：<b>贴身（&lt; 2.0 格）打不到</b> ✓、<b>没蓄满力不能出刀</b> ✓（原版 {@code minimum_attack_charge = 1.0} ✓）。
 *
 * <p>⚠ 音效沿用<b>原版已有的三叉戟音效</b> ✓（原版长矛自己的 {@code item.spear.*} 有版权 ✗ 不能打包 ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpearCombatHandler {

    // ============================================================
    //  原版参数（**铁矛那一档** ✓ 来自 Items.java 的 .spear(...) 调用 ✓）
    //    iron_spear = spear(IRON, 0.95f, 0.95f, 0.6f, 2.5f, 8.0f, 6.75f, 5.1f, 11.25f, 4.6f)
    //    ⇒ attackDuration 0.95s / damageMultiplier 0.95 / delay 0.6s
    //      dismount 2.5s 门槛 8.0 ｜ knockback 6.75s 门槛 5.1 ｜ damage 11.25s 门槛(相对) 4.6
    // ============================================================

    /** 前摇：这段时间内冲锋一点效果都没有 ✓（原版 delayTicks ＝ delay×20 ✓） */
    public static final int DELAY_TICKS = 12;
    /** 同一目标两次被戳的最小间隔 ✓（原版 contactCooldownTicks ＝ 10 ✓） */
    public static final int CONTACT_COOLDOWN_TICKS = 10;

    /** 下马窗：2.5 秒（原版 dismountTime×20 ✓）/ 攻击者速度门槛 8.0 ✓ */
    public static final int DISMOUNT_WINDOW = 50;
    public static final float DISMOUNT_MIN_SPEED = 8.0F;
    /** 击退窗：6.75 秒 ✓ / 攻击者速度门槛 5.1 ✓ */
    public static final int KNOCKBACK_WINDOW = 135;
    public static final float KNOCKBACK_MIN_SPEED = 5.1F;
    /** 伤害窗：11.25 秒 ✓ / **相对**速度门槛 4.6 ✓ */
    public static final int DAMAGE_WINDOW = 225;
    public static final float DAMAGE_MIN_RELATIVE_SPEED = 4.6F;

    /** 冲锋伤害倍率 ✓（原版 damageMultiplier ✓ 铁 = 0.95 ✓） */
    public static final float DAMAGE_MULTIPLIER = 0.95F;
    /** 原版：速度口径 ×20 ✓（块/tick → "每秒" 量级 ✓） */
    public static final double SPEED_SCALE = 20.0D;
    /** 原版给玩家的判定系数 1.0 ✓（怪物 0.2 ✓ 我们只服务玩家 ✓） */
    public static final double ACTION_FACTOR = 1.0D;

    /** 原版 {@code AttackRange(2.0, 4.5, 2.0, 6.5, 0.125, 0.5)} ✓ */
    public static final double MIN_RANGE = 2.0D;
    public static final double MAX_RANGE = 4.5D;
    public static final double MIN_RANGE_CREATIVE = 2.0D;
    public static final double MAX_RANGE_CREATIVE = 6.5D;
    public static final double HITBOX_MARGIN = 0.125D;
    /** 一次结算最多处理多少目标（原版没上限 ✓ 我们加一道保险 ✗ 防极端卡顿 ✓） */
    private static final int MAX_TARGETS = 16;

    /** 玩家 → （目标 → 最后一次被戳的游戏刻）✓ 等价于原版的 {@code recentKineticEnemies} ✓ */
    private static final Map<UUID, Map<UUID, Long>> RECENT_STABBED = new ConcurrentHashMap<>();

    private SpearCombatHandler() {}

    // ============================================================
    //  冲锋状态
    // ============================================================

    public static void startCharge(ServerPlayer player) {
        RECENT_STABBED.put(player.getUUID(), new ConcurrentHashMap<>());
    }

    public static void stopCharge(ServerPlayer player) {
        RECENT_STABBED.remove(player.getUUID());
    }

    /**
     * 冲锋每 tick 的结算（由 {@code SpearItem#onUseTick} 调用 ✓ 只在服务端 ✓）。
     *
     * @param ticksRemaining 使用剩余刻（原版同一口径 ✓）
     * @param useDuration    使用总时长（72000 ✓）
     */
    public static void tickCharge(ServerPlayer player, ItemStack stack, int ticksRemaining, int useDuration) {
        Map<UUID, Long> stabbed = RECENT_STABBED.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>());

        int ticksUsed = useDuration - ticksRemaining;
        if (ticksUsed < DELAY_TICKS) return;            // 前摇内：什么都不做 ✓
        ticksUsed -= DELAY_TICKS;

        if (ticksUsed > DAMAGE_WINDOW) return;          // 伤害窗也过了 ⇒ 力竭：等松手重来 ✓

        Vec3 look = player.getLookAngle();
        double attackerSpeed = look.dot(motionOf(player));

        boolean affected = false;
        for (LivingEntity target : targetsAlong(player, look)) {
            if (wasRecentlyStabbed(player, target)) continue;
            rememberStabbed(player, stabbed, target);

            double targetSpeed = look.dot(motionOf(target));
            double relativeSpeed = Math.max(0.0D, attackerSpeed - targetSpeed);

            boolean dismount = ticksUsed <= DISMOUNT_WINDOW
                    && attackerSpeed >= DISMOUNT_MIN_SPEED * ACTION_FACTOR;
            boolean knockback = ticksUsed <= KNOCKBACK_WINDOW
                    && attackerSpeed >= KNOCKBACK_MIN_SPEED * ACTION_FACTOR;
            boolean damage = ticksUsed <= DAMAGE_WINDOW
                    && relativeSpeed >= DAMAGE_MIN_RELATIVE_SPEED * ACTION_FACTOR;
            if (!dismount && !knockback && !damage) continue;

            // 原版：伤害 = 攻击力（基值） + floor(相对速度 × 倍率) ✓ 加法 ✓
            float dealt = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE)
                    + (float) Mth.floor(relativeSpeed * DAMAGE_MULTIPLIER);
            affected |= stab(player, stack, target, dealt, damage, knockback, dismount, look);
        }

        if (affected && player.level() instanceof ServerLevel server) {
            // 原版：命中后广播实体事件 2 ＝ 暴击粒子 ✓
            server.broadcastEntityEvent(player, (byte) 2);
        }
    }

    // ============================================================
    //  戳刺（左键）：贴身打不到 ✓ ＋ 射程内其它目标也一并戳到 ✓
    // ============================================================

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof SpearItem)) return;
        if (!(event.getTarget() instanceof LivingEntity clicked)) return;

        // 原版 minimum_attack_charge = 1.0 ⇒ 没蓄满力这一刀根本不出去 ✓
        if (player.getAttackStrengthScale(0.5F) < 1.0F) {
            event.setCanceled(true);
            return;
        }

        // 原版 AttackRange 的最小距离 ⇒ 贴太近打不到 ✓
        double minRange = player.isCreative() ? MIN_RANGE_CREATIVE : MIN_RANGE;
        if (player.getEyePosition().distanceTo(clicked.getBoundingBox().getCenter()) < minRange) {
            event.setCanceled(true);
            return;
        }

        // 点中的目标交给原版那一次攻击 ✓（附魔/暴击/横扫都原样走 ✓）；
        // 射程内**其它**目标按原版 PiercingWeapon 的口径补一遍 ✓（点中的那个不重复打 ✓）
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        Map<UUID, Long> stabbed = RECENT_STABBED.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>());
        rememberStabbed(serverPlayer, stabbed, clicked);

        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        Vec3 look = player.getLookAngle();
        boolean extra = false;
        for (LivingEntity target : targetsAlong(player, look)) {
            if (target == clicked) continue;
            if (wasRecentlyStabbed(serverPlayer, target)) continue;
            rememberStabbed(serverPlayer, stabbed, target);
            extra |= stab(serverPlayer, stack, target, damage, true, true, false, look);
        }
        if (extra) serverPlayer.level().broadcastEntityEvent(serverPlayer, (byte) 2);
    }

    // ============================================================
    //  射程查询（原版 ProjectileUtil#getHitEntitiesAlong 的等价实现）
    // ============================================================

    /** 沿视线取射程内的目标 ✓（起点＝最小距离 ✓ 终点＝最大距离 ＋ 前向速度 ✓ 方块挡则截断 ✓） */
    private static List<LivingEntity> targetsAlong(Player player, Vec3 look) {
        double minRange = player.isCreative() ? MIN_RANGE_CREATIVE : MIN_RANGE;
        double maxRange = player.isCreative() ? MAX_RANGE_CREATIVE : MAX_RANGE;

        Vec3 eye = player.getEyePosition();
        Vec3 from = eye.add(look.scale(minRange));
        double forward = Math.max(0.0D, player.getDeltaMovement().dot(look));   // 原版 getKnownMovement().dot(look) ✓
        Vec3 to = eye.add(look.scale(maxRange + forward));

        // 方块遮挡：原版用 Block.COLLIDER ✓ 撞到就把终点收到撞点 ✓
        BlockHitResult blockHit = player.level().clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            to = blockHit.getLocation();
        }

        AABB box = new AABB(from, to).inflate(HITBOX_MARGIN + 1.0D);
        List<LivingEntity> found = new ArrayList<>();
        for (LivingEntity candidate : player.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> canHitEntity(player, e))) {
            AABB hitbox = candidate.getBoundingBox().inflate(HITBOX_MARGIN + candidate.getPickRadius());
            if (hitbox.clip(from, to).isEmpty()) continue;      // 线段没穿过命中盒 ⇒ 不算 ✓
            found.add(candidate);
        }
        found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(eye)));
        if (found.size() > MAX_TARGETS) found = found.subList(0, MAX_TARGETS);
        return found;
    }

    /** 原版 {@code PiercingWeapon#canHitEntity} 的等价 ✓ ＋ 本模组"自己人不误伤"的口径（§832／§833 ✓） */
    private static boolean canHitEntity(Player attacker, LivingEntity target) {
        if (target == attacker || !target.isAlive() || target.isInvulnerable()) return false;
        if (target.isSpectator()) return false;
        if (attacker.isPassengerOfSameVehicle(target)) return false;
        if (target instanceof Player other && !attacker.canHarmPlayer(other)) return false;
        if (attacker instanceof ServerPlayer sp && PuppetUtil.isAllyOf(target, sp)) return false;
        return true;
    }

    /** 原版 {@code KineticWeapon#getMotion}：非玩家的乘客取根载具 ⇒ 骑马冲锋按马的速度算 ✓ */
    private static Vec3 motionOf(Entity entity) {
        Entity source = entity;
        if (!(source instanceof Player) && source.isPassenger()) {
            source = source.getRootVehicle();
        }
        return source.getDeltaMovement().scale(SPEED_SCALE);
    }

    // ============================================================
    //  结算（原版 LivingEntity#stabAttack 的等价实现）
    // ============================================================

    private static boolean stab(ServerPlayer player, ItemStack stack, LivingEntity target,
                                float damage, boolean dealsDamage, boolean dealsKnockback,
                                boolean dismounts, Vec3 look) {
        boolean affected = dealsKnockback;
        boolean dealtDamage = false;
        if (dealsDamage) {
            dealtDamage = target.hurt(player.damageSources().playerAttack(player), damage);
            affected |= dealtDamage;
        }
        if (dealsKnockback) {
            // 原版 causeExtraKnockback(0.4 + 击退属性) ⇒ 在原有动量**之上**沿视线推 ✓
            double strength = 0.4D + player.getAttributeValue(Attributes.ATTACK_KNOCKBACK);
            Vec3 push = new Vec3(look.x, 0.0D, look.z).normalize().scale(strength);
            target.push(push.x, 0.1D, push.z);
            target.hurtMarked = true;
        }
        if (dismounts && target.isPassenger()) {
            affected = true;
            target.stopRiding();
        }
        if (affected) {
            stack.hurtEnemy(target, player);          // 每次有效命中扣 1 点耐久 ✓（原版 weaponItem.hurtEnemy ✓）
            player.setLastHurtMob(target);
            if (player.level() instanceof ServerLevel server) {
                Vec3 mid = target.getBoundingBox().getCenter();
                server.sendParticles(ParticleTypes.CRIT, mid.x, mid.y, mid.z, 6, 0.2D, 0.2D, 0.2D, 0.1D);
                server.playSound(null, target.blockPosition(), SoundEvents.TRIDENT_HIT,
                        SoundSource.PLAYERS, 1.0F, dealsDamage ? 1.0F : 1.2F);
            }
        }
        return affected;
    }

    // ============================================================
    //  "刚被戳过"记忆（原版 recentKineticEnemies ✓ 10 tick ✓）
    // ============================================================

    private static boolean wasRecentlyStabbed(ServerPlayer player, Entity target) {
        Map<UUID, Long> stabbed = RECENT_STABBED.get(player.getUUID());
        if (stabbed == null) return false;
        Long at = stabbed.get(target.getUUID());
        return at != null && player.level().getGameTime() - at < CONTACT_COOLDOWN_TICKS;
    }

    /** 记下"这一刻戳过它" ✓（原版 rememberStabbedEntity 记的就是 getGameTime ✓） */
    private static void rememberStabbed(ServerPlayer player, Map<UUID, Long> stabbed, Entity target) {
        stabbed.put(target.getUUID(), player.level().getGameTime());
    }

    // ============================================================
    //  清理：不按了/挂了/下线了 ⇒ 别把状态留在表里
    // ============================================================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (RECENT_STABBED.isEmpty()) return;
        var server = event.getServer();
        RECENT_STABBED.keySet().removeIf(id -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            return player == null || !player.isUsingItem()
                    || !(player.getUseItem().getItem() instanceof SpearItem);
        });
    }
}
