package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * <b>弹弓 · 动能坠落</b>（§983 起，§984 修）：石弹命中**正在滞空**的目标后，强制把它拉向地面，
 * 并在它**撞到地面/落水/攀爬物**的那一刻结算一笔**动能伤害**（用户口径）。
 *
 * <h2>⚠ §984 修的两个坑（用户实测"打幻翼没反应"）</h2>
 * <ol>
 *   <li><b>"滞空"判定不该看 {@code getOnPos()}</b> ✗ —— §983 我用
 *       {@code 离地高度 = getY() - getOnPos().getY() > 0.6}，而飞行生物（幻翼等）的
 *       {@code getOnPos()} 常常就返回它**脚下那一格**（支撑方块位置为空时回退到 {@code blockPosition()}）
 *       ⇒ 差值只剩小数部分（约 0.3）⇒ **判成"不滞空"** ⇒ 整条下坠逻辑根本没跑 ✗。
 *       ⇒ 现在只判 {@code !onGround && !isInWater && !onClimbable && !isPassenger} ✓（离地就是滞空 ✓）。</li>
 *   <li><b>只拉一次对 AI 飞行生物无效</b> ✗ —— 幻翼是 {@code FlyingMob}，它的 AI **每 tick 都会
 *       {@code setDeltaMovement} 朝目标飞** ⇒ §983 那一发 {@code -1.6} 下一 tick 就被覆盖 ✗。
 *       ⇒ 现在改成标记期间**每 tick 持续施加**：重设向下速度 ✓ ＋（非玩家）**直接 {@code move()} 下移一步**
 *       ✓ —— 直接位移绕开"飞行 AI 覆盖速度"这条路 ⇒ 真·强制下坠 ✓。</li>
 * </ol>
 *
 * <h2>⭐ 对创造玩家也生效</h2>
 * 用的是本模组自己的伤害类型 {@code tinkersnewlife:kinetic}（数据包里带
 * {@code #minecraft:bypasses_invulnerability} ✓）⇒ 原版 {@code Player#isInvulnerableTo} 的
 * "创造模式无敌"那一支被绕过 ✓；另外**创造飞行会被强制关掉** ✓，否则飞行逻辑推不动 ✗。
 *
 * <h2>伤害怎么算</h2>
 * 命中时记下"这一发的动能"（＝弹弓面板投射物伤害），下坠过程中**自己累计下落格数**
 * （飞行生物不会自己累计 {@code fallDistance} ✗ ⇒ 不能靠它 ✓），落地时
 * {@code 动能伤害 = 这一发的动能 + 自己累计的下落 × 0.5}（上限 60）✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KineticImpactHandler {

    private KineticImpactHandler() {}

    /** 本模组的动能伤害类型（数据包：{@code data/tinkersnewlife/damage_type/kinetic.json} ✓） */
    private static final ResourceKey<DamageType> KINETIC_KEY = ResourceKey.create(
            Registries.DAMAGE_TYPE, new ResourceLocation(TinkersNewlife.MOD_ID, "kinetic"));

    /** 每秒下坠速度（格/tick ✓ 1.4 ≈ 28 格/秒，肉眼能看清是"被拽下来"而不是瞬移 ✓） */
    private static final double PULL_SPEED = 1.4D;
    /** 非玩家额外"直接下移"的步长（绕开飞行 AI ✓ 0.5 + 上面的速度 ≈ 每 tick 1.9 格 ✓） */
    private static final double PULL_STEP = 0.5D;
    /** 水平速度衰减（拉住目标，别让它横着飘走 ✓） */
    private static final double HORIZONTAL_DAMP = 0.3D;
    /** 标记存活上限（tick ✓ 10 秒还没落地就作废：从世界高度摔下来也够了 ✓） */
    private static final int MAX_TICKS = 200;
    /** 单次动能伤害上限（防止极端数字 ✓） */
    private static final float MAX_KINETIC_DAMAGE = 60.0F;
    /** 每格下落折算的额外动能 ✓ */
    private static final float FALL_DAMAGE_PER_BLOCK = 0.5F;

    /** 持久数据键（打在被打中的实体身上 ✓ 随实体存档 ✓） */
    private static final String TAG_DAMAGE = "tnl_kinetic_damage";
    private static final String TAG_UNTIL = "tnl_kinetic_until";
    private static final String TAG_FALL = "tnl_kinetic_fall";

    /**
     * 给一个**正在滞空**的目标挂上"强制下坠"（由 {@code StoneShotEntity#onHitEntity} 调用）。
     *
     * <p>⚠ 这里只做**第一下**（命中瞬间的手感 ✓）；后续每 tick 的持续施力在
     * {@link #onLivingTick} 里 —— 因为 AI 飞行生物会立刻覆盖速度 ✗。
     */
    public static void pullDown(LivingEntity target, float impactDamage) {
        if (target == null || target.level().isClientSide) return;

        target.setDeltaMovement(target.getDeltaMovement().x * HORIZONTAL_DAMP, -PULL_SPEED,
                target.getDeltaMovement().z * HORIZONTAL_DAMP);
        target.hasImpulse = true;
        target.hurtMarked = true;      // 服务端速度 ⇒ 客户端（少了这一行客户端看不到下坠 ✗）
        disableCreativeFlight(target);

        target.getPersistentData().putFloat(TAG_DAMAGE, Math.max(0.0F, impactDamage));
        target.getPersistentData().putLong(TAG_UNTIL, target.level().getGameTime() + MAX_TICKS);
        target.getPersistentData().putDouble(TAG_FALL, 0.0D);
    }

    /** 每 tick：还在空中的被拉目标 ⇒ **继续施力**；已经砸到东西 ⇒ 结算动能伤害 ✓ */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        if (!entity.getPersistentData().contains(TAG_DAMAGE)) return;

        boolean landed = entity.onGround() || entity.isInWater() || entity.onClimbable() || entity.isPassenger();
        boolean expired = entity.level().getGameTime() > entity.getPersistentData().getLong(TAG_UNTIL);
        if (!landed && !expired) {
            keepPulling(entity);
            return;
        }

        float impact = entity.getPersistentData().getFloat(TAG_DAMAGE);
        double pulled = entity.getPersistentData().getDouble(TAG_FALL);
        entity.getPersistentData().remove(TAG_DAMAGE);
        entity.getPersistentData().remove(TAG_UNTIL);
        entity.getPersistentData().remove(TAG_FALL);
        if (!landed) return;   // 超时 ⇒ 只清标记，不结算 ✓

        float damage = Math.min(MAX_KINETIC_DAMAGE, impact + (float) pulled * FALL_DAMAGE_PER_BLOCK);
        if (damage <= 0.0F) return;
        // 清无敌帧：否则"落地摔伤"刚打过的无敌帧会把这一笔吃掉 ✗
        entity.invulnerableTime = 0;
        entity.hurt(kineticSource(entity.level()), damage);
    }

    /** 持续施力：重设向下速度 ✓ ＋（非玩家）直接下移一步 ✓ ＋ 自己累计下落格数 ✓ */
    private static void keepPulling(LivingEntity entity) {
        entity.setDeltaMovement(entity.getDeltaMovement().x * HORIZONTAL_DAMP, -PULL_SPEED,
                entity.getDeltaMovement().z * HORIZONTAL_DAMP);
        entity.hasImpulse = true;
        entity.hurtMarked = true;
        disableCreativeFlight(entity);

        // ⭐ 非玩家：直接位移 —— 幻翼/恶魂这类 FlyingMob 的 AI 每 tick 重设速度，
        //    光靠 setDeltaMovement 顶不住 ✗；直接 move() 才能真的把它按下去 ✓
        if (!(entity instanceof Player)) {
            entity.move(MoverType.SELF, new Vec3(0.0D, -PULL_STEP, 0.0D));
            entity.getPersistentData().putDouble(TAG_FALL,
                    entity.getPersistentData().getDouble(TAG_FALL) + PULL_SPEED + PULL_STEP);
        } else {
            // 玩家：不动位置（会跟客户端打架 ✗）⇒ 只靠速度，让客户端自己往下落 ✓
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
