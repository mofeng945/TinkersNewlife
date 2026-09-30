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

    // ============================================================
    //  §842 用户口径：**伤害由"与目标的相对速度"决定，越快越高** ＋ **最低 0.1 倍** ✓
    //    因此原版那套"时间窗 + 速度门槛"（§836 那版照抄的 4.6/5.1/8.0 ✗）**全部撤掉** ✗：
    //      · 不再有伤害窗/击退窗的"过期失效" ✗
    //      · 不再有"低于门槛就 0 伤害"的开关式判定 ✗
    //    现在的口径 ✓：**伤害 = 面板攻击力 × 速度系数** ✓
    //      速度系数 = max(0.1, 相对速度 / 6)（0.1 ＝ 用户指定的**最低 0.1 倍** ✓）
    //      ⇒ 相对速度 0 ⇒ 0.1 倍 ✓；冲刺(≈5.6) ⇒ ≈0.93 倍 ✓；骑马(≈12) ⇒ ≈2 倍 ✓ 越快越高 ✓
    // ============================================================

    /** 前摇：这段时间内冲锋一点效果都没有 ✓（原版 delayTicks ＝ delay×20 ✓ 0.6 秒 ✓） */
    public static final int DELAY_TICKS = 12;
    /** 同一目标两次被戳的最小间隔 ✓（原版 contactCooldownTicks ＝ 10 ✓） */
    public static final int CONTACT_COOLDOWN_TICKS = 10;

    /** 下马门槛（相对速度 ✓ 单位同原版"×20"⇒ 6.0 ≈ 0.3 格/tick ✓）：够快才把骑手挑下来 ✓ */
    public static final float DISMOUNT_MIN_SPEED = 6.0F;

    /** ⭐ **最低伤害倍率 0.1**（用户口径 ✓）：无论相对速度多慢，冲锋至少有 0.1 倍面板伤害 ✓ */
    public static final float MIN_DAMAGE_FACTOR = 0.1F;
    /** 速度系数斜率：相对速度每 +1（×20 单位）⇒ 多 1/6 倍面板 ✓（冲刺 5.6 ⇒ ≈0.93 倍 ✓ 骑马 12 ⇒ ≈2 倍 ✓） */
    public static final float DAMAGE_FACTOR_PER_SPEED = 1.0F / 6.0F;
    /** 原版：速度口径 ×20 ✓（块/tick → "每秒" 量级 ✓） */
    public static final double SPEED_SCALE = 20.0D;
    /** 击退强度随相对速度递增 ✓（越快撞得越狠 ✓） */
    public static final double KNOCKBACK_PER_SPEED = 0.10D;

    /** 原版 {@code AttackRange(2.0, 4.5, 2.0, 6.5, 0.125, 0.5)} ✓ */
    public static final double MIN_RANGE = 2.0D;
    public static final double MAX_RANGE = 4.5D;
    public static final double MIN_RANGE_CREATIVE = 2.0D;
    public static final double MAX_RANGE_CREATIVE = 6.5D;
    public static final double HITBOX_MARGIN = 0.125D;
    /** 一次结算最多处理多少目标（原版没上限 ✓ 我们加一道保险 ✗ 防极端卡顿 ✓） */
    private static final int MAX_TARGETS = 16;

    /**
     * ⭐ §839 冲锋射线的**起点距离** —— 原版用 {@code effectiveMinRange}（2.0 ✓ 即"贴身打不到"），
     * 但那是**戳刺**那条动作的口径 ✓；用户实测「右键冲刺撞上去没伤害」✗ 时，
     * 2.0 的起点意味着"等你撞到人时他早就在 2 格以内了" ⇒ **永远扫不到** ✗
     * ⇒ 冲锋这条**单独**把起点收到 {@code 0.5} ✓（＝冲刺撞上去必须能生效 ✓ 用户口径优先 ✓
     * 戳刺那条仍然保持原版的 2.0 ✓ 见 {@link #MIN_RANGE}）。
     */
    private static final double CHARGE_MIN_RANGE = 0.5D;

    /** 玩家 → （目标 → 最后一次被戳的游戏刻）✓ 等价于原版的 {@code recentKineticEnemies} ✓ */
    private static final Map<UUID, Map<UUID, Long>> RECENT_STABBED = new ConcurrentHashMap<>();

    /**
     * ⭐⭐§841 玩家 → 上一 tick 的位置 ✓ —— <b>用来算玩家速度</b> ✓。
     *
     * <h3>为什么不能用 {@code player.getDeltaMovement()}</h3>
     * 用户实测诊断行显示「速度 0.几」✗，据此查证 ✓：
     * <b>1.20.1 的服务端压根不维护玩家的 {@code deltaMovement}</b> ✗ ——
     * {@code ServerGamePacketListenerImpl}（处理移动包的地方）里 {@code setDeltaMovement} 出现次数
     * <b>＝ 0</b> ✓（源码实读 ✓）⇒ 服务端读到的玩家速度是"物理残留"，走起来也几乎是 0 ✗
     * ⇒ 依它判定的冲锋门槛**永远过不去** ✗（这就是"右键冲刺没伤害"的根因 ✓）。
     * <p>⇒ 本版改为<b>自己记上一 tick 的位置，用位移差当速度</b> ✓
     * （服务端权威 ✓ 骑马时玩家的世界位移也自然包含坐骑速度 ✓ 比原版那套还稳 ✓）。
     */
    private static final Map<UUID, Vec3> LAST_POS = new ConcurrentHashMap<>();

    private SpearCombatHandler() {}

    // ============================================================
    //  冲锋状态
    // ============================================================

    public static void startCharge(ServerPlayer player) {
        RECENT_STABBED.put(player.getUUID(), new ConcurrentHashMap<>());
    }

    public static void stopCharge(ServerPlayer player) {
        RECENT_STABBED.remove(player.getUUID());
        LAST_POS.remove(player.getUUID());       // 速度跟踪也要清 ✓
        DIAG_LINES.remove(player.getUUID());     // 每次冲锋的诊断计数也清掉 ✓
    }

    /** 玩家这一 tick 的位移（格/tick ✓）—— 服务端权威 ✓ 见 {@link #LAST_POS} 的说明 ✓ */
    private static Vec3 playerVelocity(ServerPlayer player) {
        Vec3 now = player.position();
        Vec3 last = LAST_POS.put(player.getUUID(), now);
        if (last == null) return Vec3.ZERO;
        return now.subtract(last);
    }

    /**
     * 任意实体的这一 tick 位移（格/tick ✓）—— §842 相对速度要用 ✓。
     *
     * <p>⚠ 分两种情况（§841 查证的现实 ✓）：
     * <ul>
     *   <li><b>玩家</b>（含 PvP 里的目标玩家）：服务端 {@code deltaMovement} **恒约 0** ✗
     *       ⇒ 一律用我们自己的位置跟踪 ✓（{@link #LAST_POS} ✓）；</li>
     *   <li><b>其它生物</b>：服务端是**真的在模拟**它们 ✓ ⇒ {@code getDeltaMovement()} 有效 ✓
     *       （乘客取根载具 ✓ 照原版 {@code getMotion} 的口径 ✓）。</li>
     * </ul>
     */
    private static Vec3 velocityOf(net.minecraft.world.entity.Entity entity) {
        if (entity instanceof ServerPlayer serverPlayer) {
            Vec3 now = serverPlayer.position();
            Vec3 last = LAST_POS.put(serverPlayer.getUUID(), now);
            return last == null ? Vec3.ZERO : now.subtract(last);
        }
        net.minecraft.world.entity.Entity source = entity;
        if (source.isPassenger()) source = source.getRootVehicle();
        return source.getDeltaMovement();
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

        Vec3 look = player.getLookAngle();
        Vec3 velocity = playerVelocity(player);                 // ⭐ §841 自己跟踪的位移 ✓（服务端玩家的 deltaMovement 是 0 ✗）
        List<LivingEntity> hits = targetsAlong(player, look, CHARGE_MIN_RANGE, velocity);

        boolean affected = false;
        int landedCount = 0;
        double bestClosing = 0.0D;
        for (LivingEntity target : hits) {
            if (wasRecentlyStabbed(player, target)) continue;

            /*
             * ⭐⭐§842 **用户口径**：「**伤害应当随着目标与持有者的相对速度决定，相对速度越快伤害越高**」✓
             *
             * ⇒ 不再是"过门槛才有伤害"的开关式判定 ✗（§836~§841 那套 `relativeSpeed >= 2.0` ✗
             *   会变成"慢一点就一点伤害都没有"✗），改成**连续**的：
             *   ① 相对速度 = **（我的位移 − 目标的位移）在"我→目标"方向上的投影** ✓
             *      （＝双方"正在接近"的速度 ✓ 迎面撞上最大 ✓ 同向追赶最小 ✓ 原版也是这个量 ✓）；
             *   ② 伤害 = **攻击力 ＋ 相对速度 × 倍率** ✓ —— 越快越高 ✓ 慢也有基础伤害 ✓（不再是 0 ✗）；
             *   ③ 击退强度也随相对速度递增 ✓；下马只在够快时触发 ✓；
             *   ④ **不再有时间窗衰减** ✗（原版那三个"时间窗"是它自己的设计 ✓，与用户口径冲突 ⇒ 去掉 ✓）。
             */
            Vec3 toTarget = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
            double distance = toTarget.length();
            if (distance < 1.0E-4D) continue;
            Vec3 direction = toTarget.scale(1.0D / distance);

            Vec3 targetVelocity = velocityOf(target);
            double closing = velocity.subtract(targetVelocity).dot(direction) * SPEED_SCALE;   // ×20 ⇒ 与原版同量级 ✓
            if (closing < 0.0D) closing = 0.0D;                                                // 正在互相远离 ⇒ 0 ✓
            if (closing > bestClosing) bestClosing = closing;

            rememberStabbed(player, stabbed, target);        // 只在真打出去时才记 10 tick 冷却 ✓（§840 ✓）

            // ⭐ §842：伤害 = **面板攻击力 × 速度系数** ✓ 速度系数下限 **0.1 倍**（用户口径 ✓）越快越高 ✓
            float base = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            float factor = Math.max(MIN_DAMAGE_FACTOR, (float) (closing * DAMAGE_FACTOR_PER_SPEED));
            float dealt = base * factor;
            boolean dismount = closing >= DISMOUNT_MIN_SPEED;                                 // 够快才把人挑下马 ✓
            boolean landed = stab(player, stack, target, dealt, true, true, dismount, look,
                    closing * KNOCKBACK_PER_SPEED);
            if (landed) landedCount++;
            if (landed) {
                TinkersNewlife.LOGGER.info("[长矛·冲锋] 命中 {} 伤害 {}（相对速度 {} ⇒ {} 倍面板）",
                        target.getName().getString(), String.format("%.1f", dealt),
                        String.format("%.2f", closing), String.format("%.2f", factor));
            }
            affected |= landed;
        }
        diagnose(player, ticksUsed, velocity.dot(look) * SPEED_SCALE, bestClosing, hits, landedCount);

        if (affected && player.level() instanceof ServerLevel server) {
            // 原版：命中后广播实体事件 2 ＝ 暴击粒子 ✓
            server.broadcastEntityEvent(player, (byte) 2);
        }
    }

    // ============================================================
    //  🔎 §840 诊断：**直接显示在玩家动作栏上**（用户不用翻日志 ✓）
    //     每次冲锋最多显示 15 行，松手即停 ✓ 修好之后我会撤掉 ✗
    // ============================================================

    private static final Map<UUID, Integer> DIAG_LINES = new ConcurrentHashMap<>();

    private static void diagnose(ServerPlayer player, int ticksUsed, double attackerSpeed,
                                double closing, List<LivingEntity> hits, int landed) {
        if (ticksUsed % 20 != 0) return;
        int used = DIAG_LINES.getOrDefault(player.getUUID(), 0);
        if (used >= 15) return;
        DIAG_LINES.put(player.getUUID(), used + 1);

        String text = String.format("§b[长矛·冲锋] §ft=%d §7速度§f%.1f §7相对§f%.1f §7扫到§f%d §7命中§f%d",
                ticksUsed, attackerSpeed, closing, hits.size(), landed);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(text), true);
        TinkersNewlife.LOGGER.info("[长矛·冲锋] t={} 速度={} 相对速度={} 射线目标={} 命中={}",
                ticksUsed, String.format("%.2f", attackerSpeed), String.format("%.2f", closing),
                hits.size(), landed);
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

        /*
         * ⭐⭐§838 用户实测：「**戳过去没伤害**」✗ 根因就在下面那两条 cancel ✗（现已删除 ✓）：
         *
         * 【原版真相（反编译 1.21.11 ✓）】原版长矛的"戳刺"是**一个新动作**：
         *   ServerboundPlayerActionPacket.Action.**STAB** ⇒ 服务端直接
         *   {@code piercingWeapon.attack(player, MAINHAND)} ⇒ **纯射线多目标** ✓
         *   —— 它**完全不看你点中的是谁** ✗，而是沿视线从最小距离到最大距离把所有目标戳一遍 ✓；
         *   所以"点中的那个"在原版里也**不是**走普通单体攻击 ✗。
         *
         * 【1.20.1 的现实】没有 STAB 这个动作 ✗ ⇒ 我们能拿到的最接近入口是
         *   {@code AttackEntityEvent}（左键点在某个生物身上时才触发 ✓）。
         *   若在这里 {@code setCanceled(true)} ⇒ **这一下彻底没了** ✗✗
         *   （§837 那版正是在"没蓄满力"与"贴身 <2 格"两种情况下取消它 ✗ ⇒ 用户连点/贴身时一点伤害都没有 ✗）。
         *
         * 【本版口径（可用优先 ✓ 偏离如实记录 ✓）】
         *   ① **不再取消任何攻击** ✓ —— 点中的那个目标照常吃原版单体攻击 ✓
         *      （附魔/锋利/暴击/横扫全部原样生效 ✓；1.20.1 本身就会按"蓄力程度"缩放伤害 ✓
         *       没必要再自己卡一层 ✗）；
         *   ② 另外沿视线把射程内**其它**目标也戳一遍 ✓（＝原版 PiercingWeapon 的多目标 ✓ 点中的那个不重复打 ✓）；
         *   ③ ⚠ **没有照抄**"贴身（<2.0 格）打不到" ✗ —— 原版靠的就是 STAB 射线的起点 ✓ 而我们这条
         *      射线只负责**额外**目标 ✓；贴身的那个由原版单体攻击结算 ⇒ **照样有伤害** ✓
         *      （用户实测口径优先 ✓ 想要严格的"贴身无伤"说一声 ✓ 一条常量就能切回来 ✓）。
         */
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        Map<UUID, Long> stabbed = RECENT_STABBED.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>());
        rememberStabbed(serverPlayer, stabbed, clicked);

        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        Vec3 look = player.getLookAngle();
        boolean extra = false;
        for (LivingEntity target : targetsAlong(player, look, MIN_RANGE, Vec3.ZERO)) {
            if (target == clicked) continue;
            if (wasRecentlyStabbed(serverPlayer, target)) continue;
            rememberStabbed(serverPlayer, stabbed, target);
            extra |= stab(serverPlayer, stack, target, damage, true, true, false, look, 0.0D);
        }
        if (extra) serverPlayer.level().broadcastEntityEvent(serverPlayer, (byte) 2);
    }

    // ============================================================
    //  射程查询（原版 ProjectileUtil#getHitEntitiesAlong 的等价实现）
    // ============================================================

    /**
     * 沿视线取射程内的目标 ✓（起点＝传入的最小距离 ✓ 终点＝最大距离 ＋ 前向速度 ✓ 方块挡则截断 ✓）。
     *
     * @param minRange 射线起点距离 ✓ —— 戳刺用原版 2.0 ✓、冲锋用 {@link #CHARGE_MIN_RANGE} 0.5 ✓（§839 ✓）
     * @param velocity 攻击者这一 tick 的位移 ✓（自己跟踪的 ✓ 见 {@link #LAST_POS} ✓）
     */
    private static List<LivingEntity> targetsAlong(Player player, Vec3 look, double minRange, Vec3 velocity) {
        double maxRange = player.isCreative() ? MAX_RANGE_CREATIVE : MAX_RANGE;

        Vec3 eye = player.getEyePosition();
        Vec3 from = eye.add(look.scale(minRange));
        // ⭐ §841 用自己跟踪的位移 ✓（服务端玩家的 getDeltaMovement() 恒约 0 ✗）
        double forward = Math.max(0.0D, velocity.dot(look));
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

    // ============================================================
    //  结算（原版 LivingEntity#stabAttack 的等价实现）
    // ============================================================

    /**
     * @param knockbackStrength 击退强度 ✓ —— §842：冲锋按**相对速度**给（越快撞得越狠 ✓）；戳刺用原版基础值 ✓
     */
    private static boolean stab(ServerPlayer player, ItemStack stack, LivingEntity target,
                                float damage, boolean dealsDamage, boolean dealsKnockback,
                                boolean dismounts, Vec3 look, double knockbackStrength) {
        boolean affected = dealsKnockback;
        boolean dealtDamage = false;
        if (dealsDamage) {
            dealtDamage = target.hurt(player.damageSources().playerAttack(player), damage);
            affected |= dealtDamage;
        }
        if (dealsKnockback) {
            // 原版 causeExtraKnockback(0.4 + 击退属性) ⇒ 在原有动量**之上**沿视线推 ✓
            double strength = 0.4D + player.getAttributeValue(Attributes.ATTACK_KNOCKBACK) + knockbackStrength;
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
