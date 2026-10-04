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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * <b>弹弓 · 动能坠落</b>（§983）：石弹命中**正在滞空**的目标后，强制把它拉向地面，
 * 并在它**撞到地面/落水/攀爬物**的那一刻结算一笔**动能伤害** ✓（用户口径 ✓）。
 *
 * <h2>为什么拆成"打标记 + tick 结算"</h2>
 * 命中那一刻玩家还悬在空中 ✗ —— 伤害要等它真砸到东西才发生 ✓，所以：
 * <ol>
 *   <li>命中 ⇒ {@link #pullDown}：写一个向下的速度 ✓（{@code hasImpulse} ＋ {@code hurtMarked} 让服务端速度同步到客户端 ✓）
 *       ＋ 把"这一击的动能"记在实体的 Forge 持久数据里 ✓（会随实体存档 ✓）；</li>
 *   <li>每 tick ⇒ 看它是否**落地/落水/攀爬/上载具** ⇒ 是就结算伤害并清标记 ✓；</li>
 *   <li>超时（{@value #MAX_TICKS} tick 还没落地，例如被别的效果托住）⇒ 只清标记、不结算 ✓。</li>
 * </ol>
 *
 * <h2>⭐ 对创造玩家也生效</h2>
 * 用的是本模组自己的伤害类型 {@code tinkersnewlife:kinetic}，它在数据包里被加进了
 * {@code #minecraft:bypasses_invulnerability} ✓ ⇒ 原版 {@code Player#isInvulnerableTo} 的
 * "创造模式无敌"那一支被绕过 ✓（与 §948 西洋剑穿甲用的是同一套标签机制 ✓）。
 * 另外**创造的飞行**会被强制关掉 ✓ —— 否则"向下拉"根本推不动飞行中的玩家 ✗。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KineticImpactHandler {

    private KineticImpactHandler() {}

    /** 本模组的动能伤害类型（数据包：{@code data/tinkersnewlife/damage_type/kinetic.json} ✓） */
    private static final ResourceKey<DamageType> KINETIC_KEY = ResourceKey.create(
            Registries.DAMAGE_TYPE, new ResourceLocation(TinkersNewlife.MOD_ID, "kinetic"));

    /** 强制下坠的竖直速度（格/tick ✓ 1.6 大约两秒内从高处砸到地面 ✓） */
    private static final double PULL_SPEED = 1.6D;
    /** 水平速度衰减（拉住目标，别让它横着飘走 ✓） */
    private static final double HORIZONTAL_DAMP = 0.3D;
    /** 标记存活上限（tick ✓ 5 秒还没落地就作废 ✓） */
    private static final int MAX_TICKS = 100;
    /** 单次动能伤害上限（防止从世界高度摔下来的极端数字 ✓） */
    private static final float MAX_KINETIC_DAMAGE = 60.0F;
    /** 每格下落折算的额外动能（叠在"这一发的动能"之上 ✓） */
    private static final float FALL_DAMAGE_PER_BLOCK = 0.5F;

    /** 持久数据键（打在被打中的实体身上 ✓） */
    private static final String TAG_DAMAGE = "tnl_kinetic_damage";
    private static final String TAG_UNTIL = "tnl_kinetic_until";

    /**
     * 给一个**正在滞空**的目标挂上"强制下坠"（由 {@code StoneShotEntity#onHitEntity} 调用 ✓）。
     *
     * @param target       被打中的活体
     * @param impactDamage 这一发的动能（＝弹弓面板投射物伤害 ✓）
     */
    public static void pullDown(LivingEntity target, float impactDamage) {
        if (target == null || target.level().isClientSide) return;

        // ① 强制向下拉 ✓（保留一点水平分量，观感是"被拽下去"而不是"原地掉" ✓）
        target.setDeltaMovement(target.getDeltaMovement().x * HORIZONTAL_DAMP, -PULL_SPEED,
                target.getDeltaMovement().z * HORIZONTAL_DAMP);
        target.hasImpulse = true;
        target.hurtMarked = true;      // 服务端速度 ⇒ 客户端（少了这一行客户端看不到下坠 ✗）

        // ② 创造飞行必须先关掉，否则飞行逻辑每 tick 覆盖速度 ⇒ 拉不动 ✗
        if (target instanceof Player player && player.getAbilities().flying) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }

        // ③ 记下动能与有效期 ✓（放在实体持久数据里 ⇒ 会存档 ✓ 重启也不丢 ✓）
        target.getPersistentData().putFloat(TAG_DAMAGE, Math.max(0.0F, impactDamage));
        target.getPersistentData().putLong(TAG_UNTIL, target.level().getGameTime() + MAX_TICKS);
    }

    /** 每 tick 检查"被拉下来的目标"是否已经砸到东西 ⇒ 结算动能伤害 ✓ */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        if (!entity.getPersistentData().contains(TAG_DAMAGE)) return;

        boolean landed = entity.onGround() || entity.isInWater() || entity.onClimbable() || entity.isPassenger();
        boolean expired = entity.level().getGameTime() > entity.getPersistentData().getLong(TAG_UNTIL);
        if (!landed && !expired) {
            return;   // 还在空中 ✓ 等它砸下来
        }

        float impact = entity.getPersistentData().getFloat(TAG_DAMAGE);
        entity.getPersistentData().remove(TAG_DAMAGE);
        entity.getPersistentData().remove(TAG_UNTIL);
        if (!landed) return;   // 超时 ⇒ 只清标记，不结算 ✓

        float damage = Math.min(MAX_KINETIC_DAMAGE,
                impact + Math.max(0.0F, entity.fallDistance) * FALL_DAMAGE_PER_BLOCK);
        if (damage <= 0.0F) return;
        // 清无敌帧：否则"落地摔伤"刚打过的无敌帧会把这一笔吃掉 ✗
        entity.invulnerableTime = 0;
        entity.hurt(kineticSource(entity.level()), damage);
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
