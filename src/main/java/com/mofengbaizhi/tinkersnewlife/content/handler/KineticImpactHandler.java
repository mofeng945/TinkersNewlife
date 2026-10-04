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
 * <b>弹弓 · 动能坠落</b>（§983 起；§984 修飞行生物；§986 加<b>虚空保护</b>）。
 *
 * <h2>做什么</h2>
 * 石弹命中**正在滞空**的目标后强制把它拉向地面，撞到地面/落水/攀爬物时结算一笔动能伤害 ✓。
 *
 * <h2>⚠ §986：下方是虚空怎么办（用户提出）</h2>
 * 我们的下坠是**强制位移** ⇒ 如果正下方**没有可落地的方块**（末地外圈、主岛边缘、深渊），
 * 硬拉会把目标直接送进虚空 ✗ —— 末影龙这类 Boss 会因 {@code out_of_world} 死掉：
 * 战利品落入虚空 ✗、正常死亡流程/末地传送门生成也可能被打断 ✗。
 *
 * <p>对策（三层，逐层兜底 ✓）：
 * <ol>
 *   <li><b>落地探测</b>：命中瞬间从目标脚下向下扫 {@value #GROUND_SCAN_DEPTH} 格，
 *       找"能站住的东西"（有碰撞的方块 **或** 任何流体 ✓ 水面也算落地 ✓）；
 *       扫不到 ⇒ 判定为<b>虚空下方</b> ✓（把结果记进持久数据，之后每 tick 不再重复扫 ✗ 省性能 ✓）。</li>
 *   <li><b>虚空下方只"短促一拽"</b>：仍然给下坠手感 ✓ 但最多 {@value #VOID_PULL_LIMIT} 格 ⇒
 *       到量就**停手并立刻结算动能伤害** ✓ ⇒ 渲染上是"被砸了一下"，但**绝不把人送进虚空** ✓。</li>
 *   <li><b>世界底兜底</b>：任何情况下都不让目标被拉到 {@code level.getMinBuildHeight()} 及以下 ✓
 *       （到底就停手结算 ✓ 双保险 ✓）。</li>
 * </ol>
 * ⚠ 由此末影龙**不需要**单独豁免 ✓：它悬在主岛上方时脚下有方块 ⇒ 照常砸下去 ✓；
 * 飞到虚空之上时 ⇒ 只吃一击"滞空冲击"伤害、不会被送走 ✓。想彻底不打龙也可以另说 ✓。
 *
 * <h2>对创造玩家也生效</h2>
 * 伤害类型 {@code tinkersnewlife:kinetic} 带 {@code #minecraft:bypasses_invulnerability} ✓
 * ⇒ 创造模式的无敌挡不住 ✓；创造飞行会被强制关掉（否则推不动 ✗）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KineticImpactHandler {

    private KineticImpactHandler() {}

    /** 本模组的动能伤害类型（数据包：{@code data/tinkersnewlife/damage_type/kinetic.json} ✓） */
    private static final ResourceKey<DamageType> KINETIC_KEY = ResourceKey.create(
            Registries.DAMAGE_TYPE, new ResourceLocation(TinkersNewlife.MOD_ID, "kinetic"));

    /** 下坠速度（格/tick ✓ 1.4 ≈ 28 格/秒 ✓ 看得清是被拽下来 ✓） */
    private static final double PULL_SPEED = 1.4D;
    /** 非玩家额外"直接下移"的步长（绕开飞行 AI ✓） */
    private static final double PULL_STEP = 0.5D;
    /** 水平速度衰减（拉住目标，别让它横着飘走 ✓） */
    private static final double HORIZONTAL_DAMP = 0.3D;
    /** 标记存活上限（tick ✓ 10 秒 ✓） */
    private static final int MAX_TICKS = 200;
    /** 单次动能伤害上限 ✓ */
    private static final float MAX_KINETIC_DAMAGE = 60.0F;
    /** 每格下落折算的额外动能 ✓ */
    private static final float FALL_DAMAGE_PER_BLOCK = 0.5F;
    /** §986 落地探测深度（格 ✓ 直下方 48 格内都没有能站住的东西 ⇒ 按虚空处理 ✓） */
    private static final int GROUND_SCAN_DEPTH = 48;
    /** §986 虚空下方时最多下坠的格数（到量就停手结算 ✓） */
    private static final double VOID_PULL_LIMIT = 6.0D;

    /** 持久数据键（打在被打中的实体身上 ✓ 随实体存档 ✓） */
    private static final String TAG_DAMAGE = "tnl_kinetic_damage";
    private static final String TAG_UNTIL = "tnl_kinetic_until";
    private static final String TAG_FALL = "tnl_kinetic_fall";
    private static final String TAG_VOID = "tnl_kinetic_void";

    // ============================================================
    //  入口：命中时挂上"强制下坠"
    // ============================================================

    /** 由 {@code StoneShotEntity#onHitEntity} 调用：命中瞬间的第一下 ＋ 打标记 ✓ */
    public static void pullDown(LivingEntity target, float impactDamage) {
        if (target == null || target.level().isClientSide) return;

        target.setDeltaMovement(target.getDeltaMovement().x * HORIZONTAL_DAMP, -PULL_SPEED,
                target.getDeltaMovement().z * HORIZONTAL_DAMP);
        target.hasImpulse = true;
        target.hurtMarked = true;      // 服务端速度 ⇒ 客户端
        disableCreativeFlight(target);

        // §986：先探一次"脚下有没有地方落" ✓（只探这一次 ✓ 结果记进持久数据 ✓）
        boolean voidBelow = !hasLandingBelow(target.level(), target);
        target.getPersistentData().putBoolean(TAG_VOID, voidBelow);
        target.getPersistentData().putFloat(TAG_DAMAGE, Math.max(0.0F, impactDamage));
        target.getPersistentData().putLong(TAG_UNTIL, target.level().getGameTime() + MAX_TICKS);
        target.getPersistentData().putDouble(TAG_FALL, 0.0D);
    }

    // ============================================================
    //  每 tick：继续施力 / 到点结算
    // ============================================================

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        if (!entity.getPersistentData().contains(TAG_DAMAGE)) return;

        boolean landed = entity.onGround() || entity.isInWater() || entity.onClimbable() || entity.isPassenger();
        boolean expired = entity.level().getGameTime() > entity.getPersistentData().getLong(TAG_UNTIL);
        double pulled = entity.getPersistentData().getDouble(TAG_FALL);
        boolean voidBelow = entity.getPersistentData().getBoolean(TAG_VOID);
        // §986：虚空下方 ⇒ 拉够 VOID_PULL_LIMIT 格就停手（并不再继续往下送 ✓）
        boolean voidStop = voidBelow && pulled >= VOID_PULL_LIMIT;
        // §986：世界底兜底 ⇒ 已经贴到世界最低高度也停手 ✓
        boolean atWorldBottom = entity.getY() <= entity.level().getMinBuildHeight() + 1.0D;

        if (!landed && !expired && !voidStop && !atWorldBottom) {
            keepPulling(entity);
            return;
        }

        settle(entity, landed || voidStop || atWorldBottom);
    }

    /** 结算（并清标记）：{@code dealDamage=false} 表示只清标记（超时作废 ✓） */
    private static void settle(LivingEntity entity, boolean dealDamage) {
        float impact = entity.getPersistentData().getFloat(TAG_DAMAGE);
        double pulled = entity.getPersistentData().getDouble(TAG_FALL);
        entity.getPersistentData().remove(TAG_DAMAGE);
        entity.getPersistentData().remove(TAG_UNTIL);
        entity.getPersistentData().remove(TAG_FALL);
        entity.getPersistentData().remove(TAG_VOID);
        if (!dealDamage) return;

        float damage = Math.min(MAX_KINETIC_DAMAGE, impact + (float) pulled * FALL_DAMAGE_PER_BLOCK);
        if (damage <= 0.0F) return;
        entity.invulnerableTime = 0;   // 否则落地摔伤的无敌帧会把这一笔吃掉 ✗
        entity.hurt(kineticSource(entity.level()), damage);
    }

    /** 持续施力：重设向下速度 ＋（非玩家）直接下移一步 ＋ 自己累计下落格数 ✓ */
    private static void keepPulling(LivingEntity entity) {
        entity.setDeltaMovement(entity.getDeltaMovement().x * HORIZONTAL_DAMP, -PULL_SPEED,
                entity.getDeltaMovement().z * HORIZONTAL_DAMP);
        entity.hasImpulse = true;
        entity.hurtMarked = true;
        disableCreativeFlight(entity);

        if (!(entity instanceof Player)) {
            // ⭐ 非玩家：直接位移 —— 幻翼/恶魂这类 FlyingMob 的 AI 每 tick 重设速度，光靠速度顶不住 ✗
            entity.move(MoverType.SELF, new Vec3(0.0D, -PULL_STEP, 0.0D));
            entity.getPersistentData().putDouble(TAG_FALL,
                    entity.getPersistentData().getDouble(TAG_FALL) + PULL_SPEED + PULL_STEP);
        } else {
            // 玩家：不动位置（会跟客户端打架 ✗）⇒ 只靠速度 ✓
            entity.getPersistentData().putDouble(TAG_FALL,
                    entity.getPersistentData().getDouble(TAG_FALL) + PULL_SPEED);
        }
    }

    /** 创造飞行必须先关掉，否则飞行逻辑每 tick 覆盖速度 ⇒ 拉不动 ✗ */
    private static void disableCreativeFlight(LivingEntity target) {
        if (target instanceof Player player && player.getAbilities().flying) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
    }

    // ============================================================
    //  §986 落地探测：脚下有没有"能站住的东西"
    // ============================================================

    /**
     * 从目标脚下向下扫 {@value #GROUND_SCAN_DEPTH} 格，找有没有"能落上去的东西"。
     *
     * <p>判据：方块有碰撞（{@code blocksMotion} ✓）**或**该位置有流体（水面/岩浆面也算落地 ✓）；
     * 扫到世界最低高度为止都没找到 ⇒ 视为<b>虚空下方</b> ✓。
     */
    private static boolean hasLandingBelow(Level level, LivingEntity entity) {
        int startY = Mth.floor(entity.getY()) - 1;
        int minY = level.getMinBuildHeight();
        int endY = Math.max(minY, startY - GROUND_SCAN_DEPTH);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int x = Mth.floor(entity.getX());
        int z = Mth.floor(entity.getZ());
        for (int y = startY; y >= endY; y--) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (state.blocksMotion() || !state.getFluidState().isEmpty()) {
                return true;
            }
        }
        return false;
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
