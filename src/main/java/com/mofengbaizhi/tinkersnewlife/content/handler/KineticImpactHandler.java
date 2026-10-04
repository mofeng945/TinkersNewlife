package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * <b>弹弓 · 动能坠落</b>（§983 起；§984 修飞行生物；§986/§987 处理"下方是虚空"）。
 *
 * <h2>做什么（§987 用户口径）</h2>
 * <ol>
 *   <li>石弹命中**正在滞空**的目标 ⇒ <b>先搜下方最近的陆地</b>（从脚下一直扫到世界最低高度 ✓ 只扫一次 ✓）；</li>
 *   <li><b>有陆地</b> ⇒ <b>砸向那块陆地</b> ✓ —— 强制下坠到位，撞到地面/水面/攀爬物那一刻结算**动能伤害**
 *       （＝这一发的动能 ＋ 自己累计的下坠格数 × 0.5 ✓ 摔得越远越疼 ✓ 上限 60 ✓）；</li>
 *   <li><b>没有陆地（虚空下方）</b> ⇒ <b>温和地往下拽一阵</b>（约 {@value #VOID_PULL_TICKS} tick ≈ 1 秒 / 十几格 ✓
 *       仍然用"直接位移"以确保对幻翼这类飞行 AI 有效 ✓）⇒ 然后 <b>放开、恢复正常</b> ✓
 *       —— ⚠ **不给动能伤害** ✗（没砸到任何东西 ✓ 也就不会把末影龙这类 Boss 送进虚空 ✗）。</li>
 * </ol>
 *
 * <h2>为什么不能一味往下砸</h2>
 * 下坠是**强制位移** ⇒ 若正下方是虚空（末地外圈、主岛边缘、深渊），硬拉会把目标送进虚空 ✗：
 * 末影龙会吃 {@code out_of_world} ⇒ 战利品丢进虚空 ✗、正常死亡流程与末地传送门生成也可能被打断 ✗。
 * ⇒ 由此末影龙**不需要单独豁免**：主岛上方照常砸 ✓，虚空上方只被拽一下就放回去 ✓。
 *
 * <h2>兜底</h2>
 * ①超时（远处陆地太久没到 ✓）②已到世界最低高度 ⇒ 都只**放开恢复**、不结算伤害 ✓
 * （宁可少打一下，也不把目标送走 ✓）。
 *
 * <h2>对创造玩家也生效</h2>
 * 伤害类型 {@code tinkersnewlife:kinetic} 带 {@code #minecraft:bypasses_invulnerability} ✓
 * ⇒ 创造模式无敌挡不住 ✓；创造飞行会被强制关掉（否则推不动 ✗）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KineticImpactHandler {

    private KineticImpactHandler() {}

    /** 本模组的动能伤害类型（数据包：{@code data/tinkersnewlife/damage_type/kinetic.json} ✓） */
    private static final ResourceKey<DamageType> KINETIC_KEY = ResourceKey.create(
            Registries.DAMAGE_TYPE, new ResourceLocation(TinkersNewlife.MOD_ID, "kinetic"));

    // ---- 有陆地时：全力砸下去 ----
    /** 下坠速度（格/tick ✓ 1.4 ≈ 28 格/秒 ✓） */
    private static final double PULL_SPEED = 1.4D;
    /** 非玩家额外"直接下移"步长（绕开飞行 AI ✓） */
    private static final double PULL_STEP = 0.5D;
    /** §992 加码后的**最大下坠速度**（格/tick ✓ 3.0 = 60 格/秒 ⇒ Boss 的爬升 AI 也追不回来 ✓） */
    private static final double PULL_SPEED_MAX = 3.0D;
    /** §992 加码后的**最大直接位移**（格/tick ✓ 2.0 ✓ 总位移 ≈5 格/tick ✓） */
    private static final double PULL_STEP_MAX = 2.0D;
    /** §992 加码到满所需的 tick 数（40 tick = 2 秒 ✓ 开头仍是"被拽"的手感，之后越来越猛 ✓） */
    private static final int RAMP_TICKS = 40;

    // ---- 虚空下方时：只温和地拽一阵 ----
    /** 温和下拽速度（格/tick ✓ 0.5 ≈ 10 格/秒 ✓） */
    private static final double VOID_PULL_SPEED = 0.5D;
    /** 温和下拽的直接位移步长 ✓ */
    private static final double VOID_PULL_STEP = 0.15D;
    /** 拽多久（tick ✓ 20 ≈ 1 秒 ⇒ 合计约 13 格 ✓ "拽一阵"的手感 ✓） */
    private static final int VOID_PULL_TICKS = 20;

    /** 水平速度衰减（拉住目标，别让它横着飘走 ✓） */
    private static final double HORIZONTAL_DAMP = 0.3D;
    /** 单次动能伤害上限 ✓ */
    private static final float MAX_KINETIC_DAMAGE = 60.0F;
    /** 每格下落折算的额外动能 ✓ */
    private static final float FALL_DAMAGE_PER_BLOCK = 0.5F;
    /** 安全超时上限（tick ✓ 远处陆地也够用 ✓） */
    private static final int MAX_TICKS = 600;

    /** 持久数据键（打在被打中的实体身上 ✓ 随实体存档 ✓） */
    private static final String TAG_DAMAGE = "tnl_kinetic_damage";
    private static final String TAG_UNTIL = "tnl_kinetic_until";
    private static final String TAG_FALL = "tnl_kinetic_fall";
    private static final String TAG_TICKS = "tnl_kinetic_ticks";
    private static final String TAG_GROUND = "tnl_kinetic_ground";

    // ============================================================
    //  入口：命中时先搜"下方最近的陆地"，再决定砸还是拽
    // ============================================================

    /** 由 {@code StoneShotEntity#onHitEntity} 调用：命中瞬间的第一下 ＋ 打标记 ✓ */
    public static void pullDown(LivingEntity target, float impactDamage) {
        if (target == null || target.level().isClientSide) return;

        // ⭐ 搜下方最近的陆地（从脚下扫到世界最低高度 ✓ 只扫这一次 ✓）
        double groundDistance = landingDistanceBelow(target.level(), target);
        boolean hasLand = groundDistance >= 0.0D;

        // 命中瞬间的"那一下"：有陆地就全力、虚空就温和（用户口径 ✓）
        double speed = hasLand ? PULL_SPEED : VOID_PULL_SPEED;
        target.setDeltaMovement(target.getDeltaMovement().x * HORIZONTAL_DAMP, -speed,
                target.getDeltaMovement().z * HORIZONTAL_DAMP);
        target.hasImpulse = true;
        target.hurtMarked = true;
        disableCreativeFlight(target);

        // 超时：有陆地 ⇒ 按距离算够用即可；虚空 ⇒ 拽满 VOID_PULL_TICKS 后由 release 收尾 ✓
        int ticks = hasLand
                ? Math.min(MAX_TICKS, (int) Math.ceil(groundDistance / (PULL_SPEED + PULL_STEP)) + 80)
                : VOID_PULL_TICKS + 10;

        target.getPersistentData().putFloat(TAG_DAMAGE, Math.max(0.0F, impactDamage));
        target.getPersistentData().putLong(TAG_UNTIL, target.level().getGameTime() + ticks);
        target.getPersistentData().putDouble(TAG_FALL, 0.0D);
        target.getPersistentData().putInt(TAG_TICKS, 0);
        target.getPersistentData().putDouble(TAG_GROUND, groundDistance);
    }

    // ============================================================
    //  每 tick：继续施力 / 到点收尾
    // ============================================================

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        if (!entity.getPersistentData().contains(TAG_DAMAGE)) return;

        boolean landed = entity.onGround() || entity.isInWater() || entity.onClimbable() || entity.isPassenger();
        long now = entity.level().getGameTime();
        boolean expired = now > entity.getPersistentData().getLong(TAG_UNTIL);
        int ticks = entity.getPersistentData().getInt(TAG_TICKS);
        double groundDistance = entity.getPersistentData().getDouble(TAG_GROUND);
        boolean hasLand = groundDistance >= 0.0D;
        // ⭐ 虚空下方：拽满 VOID_PULL_TICKS ⇒ 放开、恢复正常（不结算伤害 ✓）
        boolean voidDone = !hasLand && ticks >= VOID_PULL_TICKS;
        // 安全兜底：已经到了世界最低高度 ⇒ 同样放开（不结算 ✓）
        boolean atWorldBottom = entity.getY() <= entity.level().getMinBuildHeight() + 2.0D;

        if (landed) {
            settle(entity, true);          // 砸到东西了 ⇒ 结算动能伤害 ✓
            return;
        }
        if (voidDone || expired || atWorldBottom) {
            release(entity);               // 没砸到 / 太久 / 到底 ⇒ 放开、恢复正常 ✓ 不结算 ✗
            return;
        }
        keepPulling(entity, hasLand);
    }

    /** 结算：清标记 ＋ 打动能伤害（目标速度保持原样 ⇒ 它会自然落地 ✓） */
    private static void settle(LivingEntity entity, boolean dealDamage) {
        float impact = entity.getPersistentData().getFloat(TAG_DAMAGE);
        double pulled = entity.getPersistentData().getDouble(TAG_FALL);
        clearTags(entity);
        if (!dealDamage) return;

        float damage = Math.min(MAX_KINETIC_DAMAGE, impact + (float) pulled * FALL_DAMAGE_PER_BLOCK);
        if (damage <= 0.0F) return;
        entity.invulnerableTime = 0;   // 否则落地摔伤的无敌帧会把这一笔吃掉 ✗
        entity.hurt(kineticSource(entity.level()), damage);
    }

    /**
     * 放开并**恢复正常**（虚空下拽结束 / 超时 / 已到世界底）：
     * 清标记 ✓ ＋ 抹掉我们强加的向下速度（竖直归零、水平衰减 ✓）⇒ 目标立刻回到自己的 AI / 正常下落 ✓。
     */
    private static void release(LivingEntity entity) {
        clearTags(entity);
        Vec3 motion = entity.getDeltaMovement();
        entity.setDeltaMovement(motion.x * HORIZONTAL_DAMP, Math.max(motion.y, 0.0D), motion.z * HORIZONTAL_DAMP);
        entity.hasImpulse = true;
        entity.hurtMarked = true;      // 让客户端也"松手"✓
    }

    private static void clearTags(LivingEntity entity) {
        entity.getPersistentData().remove(TAG_DAMAGE);
        entity.getPersistentData().remove(TAG_UNTIL);
        entity.getPersistentData().remove(TAG_FALL);
        entity.getPersistentData().remove(TAG_TICKS);
        entity.getPersistentData().remove(TAG_GROUND);
    }

    /**
     * 持续施力（{@code hasLand} 决定用力道大小 ✓）＋ 累计下坠格数与 tick 数 ✓。
     *
     * <p>§992 ⭐ <b>力度随时间递增</b>：凋灵 / 末影龙这类 Boss 的 AI **每 tick 都主动爬升/维持高度** ✗，
     * 固定 0.5 格/tick 的直接位移会被它们"升回来" ⇒ 实测"拽不太动" ✗
     * ⇒ 现在按已拽 tick 数**线性加码**（速度 1.4 → {@value #PULL_SPEED_MAX}；
     * 位移 0.5 → {@value #PULL_STEP_MAX}）✓ ⇒ 几秒后每 tick 位移 2 格以上（≈40+ 格/秒 ✓）AI 追不回来 ✓。
     */
    private static void keepPulling(LivingEntity entity, boolean hasLand) {
        int ticks = entity.getPersistentData().getInt(TAG_TICKS);
        // ⭐ 加码曲线：每 tick 涨一点，封顶见常量 ✓（虚空分支保持温和，避免把人送进虚空 ✗）
        double ramp = Math.min(1.0D, ticks / (double) RAMP_TICKS);
        double speed = hasLand
                ? PULL_SPEED + (PULL_SPEED_MAX - PULL_SPEED) * ramp
                : VOID_PULL_SPEED;
        double step = hasLand
                ? PULL_STEP + (PULL_STEP_MAX - PULL_STEP) * ramp
                : VOID_PULL_STEP;

        entity.setDeltaMovement(entity.getDeltaMovement().x * HORIZONTAL_DAMP, -speed,
                entity.getDeltaMovement().z * HORIZONTAL_DAMP);
        entity.hasImpulse = true;
        entity.hurtMarked = true;
        disableCreativeFlight(entity);

        double gained = speed;
        if (!(entity instanceof Player)) {
            // ⭐ 非玩家：直接位移 —— 幻翼/恶魂这类 FlyingMob 的 AI 每 tick 重设速度，光靠速度顶不住 ✗
            entity.move(MoverType.SELF, new Vec3(0.0D, -step, 0.0D));
            gained += step;
        }
        entity.getPersistentData().putDouble(TAG_FALL, entity.getPersistentData().getDouble(TAG_FALL) + gained);
        entity.getPersistentData().putInt(TAG_TICKS, entity.getPersistentData().getInt(TAG_TICKS) + 1);
    }

    /** 创造飞行必须先关掉，否则飞行逻辑每 tick 覆盖速度 ⇒ 拉不动 ✗ */
    private static void disableCreativeFlight(LivingEntity target) {
        if (target instanceof Player player && player.getAbilities().flying) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
    }

    // ============================================================
    //  "下方最近的陆地"探测
    // ============================================================

    /**
     * 从目标脚下**一直扫到世界最低高度**，找最近的"能落上去的东西"，返回**距离（格）**；找不到返回 {@code -1}。
     *
     * <p>判据：方块有碰撞（{@code blocksMotion} ✓）**或**该位置有流体（水面/岩浆面也算落地 ✓）。
     * ⚠ 只扫**正下方那一列**（用户口径"下方最近的陆地" ✓）；一整列的循环很便宜，而且只扫一次 ✓。
     */
    private static double landingDistanceBelow(Level level, LivingEntity entity) {
        int startY = Mth.floor(entity.getY()) - 1;
        int minY = level.getMinBuildHeight();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int x = Mth.floor(entity.getX());
        int z = Mth.floor(entity.getZ());
        for (int y = startY; y >= minY; y--) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (state.blocksMotion() || !state.getFluidState().isEmpty()) {
                return Math.max(0.0D, entity.getY() - (y + 1.0D));
            }
        }
        return -1.0D;
    }

    // ============================================================
    //  伤害源
    // ============================================================

    /** 动能伤害源（带 {@code bypasses_invulnerability} 标签 ⇒ 创造玩家也吃 ✓） */
    public static DamageSource kineticSource(Level level) {
        Holder<DamageType> type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(KINETIC_KEY);
        return new DamageSource(type, null, null);
    }

    /** 带攻击者归属的版本（当前未用 ✓ 留给以后要算击杀归属时用 ✓） */
    public static DamageSource kineticSource(Level level, @Nullable Entity attacker) {
        Holder<DamageType> type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(KINETIC_KEY);
        return new DamageSource(type, attacker, attacker);
    }
}
