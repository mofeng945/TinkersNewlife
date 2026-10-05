package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.SoldierSlashEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 词条·<b>兵士佩刀</b>的实际逻辑（§808／§809／§810 · 唐横刀自带 · 无等级 ✓）。
 *
 * <h2>效果（用户口径 ✓ 逐字实现）</h2>
 * <ol>
 *   <li><b>4 格内按距离"附加多段伤害"</b> ✓（用户原话：「<b>不是越近伤害越高，而是越近能附加多段伤害</b>」✓）：
 *       4格 <b>0</b> 段 / 3格 <b>1</b> 段 / 2格 <b>2</b> 段 / 1格 <b>3</b> 段 / 不到 1 格 <b>4</b> 段 ✓；
 *       每段 = <b>工具攻击面板 × 50%</b> ✓（用户口径 ✓），而且每段是<b>各自独立的一次伤害结算</b> ✓
 *       ——<b>不是</b>把总加成塞进同一次伤害里 ✗（那是"越近伤害越高"✗ 正是用户否掉的口径 ✗）；</li>
 *   <li><b>每段一道灰色刀光</b> ✓（用户原话：「<b>我要的是刀光光效，不是横扫粒子</b>」✓
 *       ＋「<b>模仿拔刀剑刀光</b>」✓）⇒ 用 {@link SoldierSlashEntity}
 *       （弧形面片 ＋ 弧光贴图 ＋ 顶点色，实现见 {@code SoldierSlashRenderer}）✓
 *       <b>不用</b> {@code ParticleTypes.SWEEP_ATTACK} ✗。</li>
 * </ol>
 *
 * <h2>多段怎么结算（关键设计 ✓）</h2>
 * <ul>
 *   <li><b>不塞进同一次伤害</b> ✗：那样受击方只会掉一次血、只弹一个数字 ✗ 看不出"多段" ✗；</li>
 *   <li><b>而是在接下来的若干 tick 里逐段补刀</b> ✓：主伤害先照常结算 ✓，随后每 {@link #STAGE_INTERVAL_TICKS}
 *       tick 补一段 ✓（第 1 段在第 2 tick、第 2 段在第 4 tick…✓），每段各自
 *       {@code victim.hurt(同一伤害源, 面板 × 50%)} ✓ ⇒ 会分别弹伤害数字 ✓ 每段一道刀光 ✓
 *       正是"越近能附加多段伤害" ✓；</li>
 *   <li><b>排程用 {@code ServerTickEvent}</b> ✓（本仓既有的每 tick 写法 ✓）而不是依赖不确定的调度 API ✓；
 *       待结算列表在服务端主线程里读写 ✓ 无需加锁 ✓；</li>
 *   <li>⚠ <b>破无敌帧</b>：原版受击后有 10 tick 无敌 ✗ ⇒ 补刀前把 {@code victim.invulnerableTime} 清零 ✓，
 *       否则第 2 段之后全被吞掉 ✗（"多段"变"一段"✗）；</li>
 *   <li>⚠ <b>防滚雪球</b>：补刀自己也会触发 {@code LivingHurtEvent} ⇒ 若不拦，会无限套娃 ✗✗。
 *       用 {@link #resolvingStage} 标记"正在结算我们补的那一段" ✓（同一线程同步调用 ⇒ 普通 boolean 足够 ✓）。</li>
 * </ul>
 *
 * <h2>触发条件：认词条，不认这把刀 ✓</h2>
 * 读 {@code ToolStack.getModifiers().getLevel(soldiers_saber)} ✓ ⇒ 以后别的工具挂上同一个词条也能吃 ✓
 * （读不到工具数据 ⇒ 当作不带 ✓ 绝不抛错 ✗）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SoldiersSaberHandler {

    /** 词条 id（与 {@code Modifiers.SOLDIERS_SABER} 一致 ✓） */
    private static final ModifierId SABER =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "soldiers_saber"));

    /** 每段伤害 = 工具攻击面板 × 该比例 ✓（用户口径 50% ✓ 要改只改这里 ✓） */
    public static final float DAMAGE_PER_STAGE_RATIO = 0.5F;

    /** 生效半径：4 格 ✓（用户口径"4格范围内"✓） */
    public static final double MAX_RANGE = 4.0D;

    /** 相邻两段之间的间隔（tick ✓）：2 tick ⇒ 贴身 4 段约 8 tick 打完 ✓ 有"连续斩"的节奏 ✓ */
    private static final int STAGE_INTERVAL_TICKS = 2;

    /** 每道刀光的寿命（tick ✓）：5 tick 够看清又不会几道长时间糊在一起 ✓ */
    private static final int SLASH_LIFE_TICKS = 5;

    /** 刀光颜色：灰色 ✓（用户口径"灰色刀光"✓ 0xRRGGBB ✓） */
    private static final int SLASH_TINT = 0xC9CFD9;

    /**
     * §1043 每一段刀光的<b>角度抖动</b>（±该值 度 ✓）。
     * <p>⚠ 朝向本身<b>不再</b>由服务端决定 ✗：渲染器会按施法者位置把弧带的<b>圆心精确对准玩家</b> ✓
     * （用户口径：「我是想让你把圆心对着玩家」✓ 见 {@code SoldierSlashRenderer#aimRollAtCaster} ✓）。
     * 这里只加一点点抖动 ✓ ⇒ 4 段既都朝玩家、又不会像同一刀 ✓；
     * 段与段的区分主要靠<b>镜像</b>（{@code setMirrored} ✓）、<b>大小</b>（{@link #STAGE_SCALE} ✓）
     * 与<b>落点</b>（黄金角小圆 ✓）✓。
     * <p>这个值只在"渲染器算不出朝玩家方向"时当兜底角度用 ✓。
     */
    private static final float SLASH_ROLL_JITTER = 4.0F;

    /** 每一段刀光的大小 ✓（略有差别 ⇒ 即使角度接近也分得清 ✓） */
    private static final float[] STAGE_SCALE = { 1.16F, 0.98F, 1.30F, 1.04F };

    /** 正在结算"我们补的那一段" ⇒ 它自己触发的受击事件不再触发本词条 ✓（同线程同步 ⇒ boolean 足够 ✓） */
    private static boolean resolvingStage = false;

    /** 待结算的一段伤害 ✓（全部在服务端主线程读写 ✓） */
    private record PendingStage(long atTick, DamageSource source, LivingEntity victim,
                                float damage, int index, Vec3 casterPos) {}

    private static final List<PendingStage> PENDING = new ArrayList<>();

    private SoldiersSaberHandler() {
    }

    // ============================================================
    //  主伤害：只负责"排程"，不自己加伤害 ✓
    // ============================================================

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (resolvingStage) return;                    // 我们自己补的那一段 ⇒ 直接放行 ✓
        if (event.isCanceled()) return;
        // ⭐§833 匠魂的流血/穿刺这类"二次伤害"也会把玩家挂在伤害源上 ⇒ 若不拦，
        //    每一跳流血都会再排一次 N 段（用户实测的"超级大数字"就是这个 ✗）
        if (com.mofengbaizhi.tinkersnewlife.util.ToolHelper.isTinkersSecondaryDamage(event.getSource())) return;
        LivingEntity victim = event.getEntity();
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (attacker == victim) return;

        ItemStack held = attacker.getMainHandItem();
        if (held.isEmpty() || !hasSaber(held)) return;

        int stages = stagesFor(attacker.distanceTo(victim));
        if (stages <= 0) return;

        float perStage = damagePerStage(held);
        if (perStage <= 0.0F) return;

        scheduleStages(event.getSource(), victim, perStage, stages, attacker.position());
    }

    /** 把 N 段依次排进后续 tick ✓（排不进去也绝不影响主伤害 ✓） */
    private static void scheduleStages(DamageSource source, LivingEntity victim, float perStage, int stages,
                                       Vec3 casterPos) {
        try {
            if (!(victim.level() instanceof ServerLevel level)) return;
            if (level.getServer() == null) return;
            long now = level.getServer().getTickCount();
            for (int i = 1; i <= stages; i++) {
                PENDING.add(new PendingStage(now + (long) i * STAGE_INTERVAL_TICKS,
                        source, victim, perStage, i, casterPos));
            }
        } catch (Throwable ignored) {
            // 排程失败 = 没有追加段 ✓ 主伤害照常 ✓
        }
    }

    // ============================================================
    //  逐段结算 + 每段一道刀光
    // ============================================================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (PENDING.isEmpty()) return;
        long now = event.getServer().getTickCount();
        Iterator<PendingStage> it = PENDING.iterator();
        while (it.hasNext()) {
            PendingStage stage = it.next();
            if (stage.atTick() > now) continue;
            it.remove();                                  // 先摘掉 ⇒ 即使结算里抛错也不会反复重试 ✓
            resolveStage(stage);
        }
    }

    private static void resolveStage(PendingStage stage) {
        LivingEntity victim = stage.victim();
        if (victim == null || !victim.isAlive() || victim.isRemoved()) return;   // 目标没了 ⇒ 后面几段自然作废 ✓
        if (!(victim.level() instanceof ServerLevel level)) return;

        victim.invulnerableTime = 0;                     // ⚠ 破无敌帧 ⇒ 每段都真的结算 ✓
        resolvingStage = true;
        try {
            victim.hurt(stage.source(), stage.damage());
        } catch (Throwable ignored) {
            // 单段失败不影响后续段 ✓
        } finally {
            resolvingStage = false;
        }
        spawnSlash(level, victim, stage.index(), stage.casterPos());
    }

    /**
     * 距离 ⇒ 段数（用户口径 ✓）：不到 1 格 4 段 / 1 格 3 段 / 2 格 2 段 / 3 格 1 段 / 4 格 0 段 ✓
     * <p>用"实体中心距"（{@code distanceTo} ✓）—— 与玩家感知的"几格"一致 ✓。
     */
    public static int stagesFor(double distance) {
        if (distance < 1.0D) return 4;
        if (distance < 2.0D) return 3;
        if (distance < 3.0D) return 2;
        if (distance < MAX_RANGE) return 1;
        return 0;
    }

    /** 每段伤害 = 工具攻击面板 × 50% ✓（拿不到工具数据就当作 0 ✓ 绝不抛错 ✗） */
    private static float damagePerStage(ItemStack stack) {
        try {
            float panel = ToolStack.from(stack).getStats().get(ToolStats.ATTACK_DAMAGE);
            return panel * DAMAGE_PER_STAGE_RATIO;
        } catch (Throwable t) {
            return 0.0F;
        }
    }

    /** 手上这把工具带不带「兵士佩刀」✓（拿不到工具数据就当作不带 ✓ 绝不抛错 ✗） */
    private static boolean hasSaber(ItemStack stack) {
        try {
            return ToolStack.from(stack).getModifiers().getLevel(SABER) > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 灰色刀光 ✓：一道 = 一个 {@link SoldierSlashEntity}（弧形面片 ✓ 不是粒子 ✗）。
     *
     * <p>§811（用户口径「附加刀光别给我重叠了，每一段角度应该不太一样」✓）—— 每一段都把三样东西错开 ✓：
     * <ol>
     *   <li><b>角度</b>：取 {@link #STAGE_ROLL} 里预先排好的倾角 ✓（互不相同、左右交替 ✓）
     *       ＋ 只加一点点随机抖动（±4° ✓ 免得机械 ✓）；</li>
     *   <li><b>大小</b>：取 {@link #STAGE_SCALE} ✓（1.30 / 0.98 / 1.16 / 1.04 各不相同 ✓）；</li>
     *   <li><b>落点</b>：沿黄金角（137.5°）在小圆上错开 ✓ ＋ 高度递增 ✓
     *       ⇒ 几道刀光不会钉在同一个点上 ✗。</li>
     * </ol>
     * 奇数段还额外<b>左右镜像</b> ✓ ⇒ 与偶数段"从另一侧切进来" ✓ 更不像同一刀 ✓。
     * （朝向本身由客户端按相机算 ✓ 见 {@code SoldierSlashRenderer#applyBillboard} ✓，
     *   服务端<b>不再</b>给随机 yaw ✗ —— 随机 yaw 会让弧面侧对镜头、退化成细线并糊在一起 ✗，正是"重叠"的根因 ✗。）
     */
    private static void spawnSlash(ServerLevel level, LivingEntity victim, int index, Vec3 casterPos) {
        try {
            int i = Math.max(1, index);
            float roll = (level.random.nextFloat() * 2.0F - 1.0F) * SLASH_ROLL_JITTER;    // §1043 只留抖动 ✓
            float scale = STAGE_SCALE[(i - 1) % STAGE_SCALE.length];

            // 落点错开：黄金角小圆 ＋ 高度递增 ⇒ 不重叠 ✓
            double offsetAngle = Math.toRadians(i * 137.5D);
            Vec3 base = victim.position().add(0.0D, victim.getBbHeight() * 0.55D, 0.0D);
            Vec3 pos = base.add(
                    Math.cos(offsetAngle) * 0.22D,
                    -0.10D + i * 0.07D,
                    Math.sin(offsetAngle) * 0.22D);

            // §1042 带上施法者位置 ⇒ 弧面凹向玩家、凸面朝外 ✓
            SoldierSlashEntity slash = new SoldierSlashEntity(
                    level, pos, roll, scale, SLASH_LIFE_TICKS, 0, SLASH_TINT, casterPos);
            slash.setMirrored(i % 2 == 1);
            level.addFreshEntity(slash);
        } catch (Throwable ignored) {
            // 光效失败不影响伤害 ✓
        }
    }
}
