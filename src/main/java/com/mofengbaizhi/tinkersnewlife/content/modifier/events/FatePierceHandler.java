package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.content.modifier.FatePierceModifier;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * 命运因果贯穿之物（远程特性）结算器：
 * <ul>
 *   <li>下蹲时自动锁定敌人：优先视线内目标（沿朝向 32 格内、对齐度最高的生物），无则最近目标；</li>
 *   <li>发射后弹射物自动追踪锁定目标 → 必中。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = com.mofengbaizhi.tinkersnewlife.TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FatePierceHandler {

    private static final double LOCK_RANGE = 32.0;
    private static final double LOCK_ANGLE_COS = 0.94; // ~20°
    /** 持械者 persistentData 键：锁定目标 UUID */
    private static final String KEY_LOCK = "tinkersnewlife.fate_lock";
    /** 弹射物 persistentData 键：标记为本特性所追踪的箭矢，命中后应被移除 */
    private static final String KEY_PIERCE_ARROW = "tinkersnewlife.fate_pierce_arrow";

    private FatePierceHandler() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (!player.isShiftKeyDown()) return;
        if (!hasFatePierce(player.getMainHandItem())) return;
        if (player.tickCount % 10 != 0) return;

        LivingEntity lock = findLock(player);
        if (lock != null) {
            player.getPersistentData().putString(KEY_LOCK, lock.getUUID().toString());
        } else {
            player.getPersistentData().remove(KEY_LOCK);
        }
    }

    /** 弹射物自动追踪锁定目标（保证命中） */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        // ⚠️ 每 tick 都执行：原版冈格尼尔是在弹射物自身 tick 内、super.tick()（含重力）之后立刻
        // 重瞄准，从而抵消下落。若隔 2 tick 才重瞄一次，箭头会因重力在空中拱起、错过锁定目标。
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            UUID lockId = readLock(player);
            if (lockId == null) continue;
            ServerLevel level = (ServerLevel) player.level();
            LivingEntity target = (LivingEntity) level.getEntity(lockId);
            if (target == null || !target.isAlive()) continue;
            List<AbstractArrow> arrows = level.getEntitiesOfClass(AbstractArrow.class,
                    player.getBoundingBox().inflate(96),
                    a -> a.getOwner() == player);
            for (AbstractArrow arrow : arrows) {
                // 与原版冈格尼尔之枪的追踪一致：
                //   命中点 = 目标位置 + 身高 60%；
                //   方向项 to = 命中点 - 箭头位置，取半向量 vecToTarget = to * 0.5；
                //   len=|motion|, vecLen=|vecToTarget|, mag=|(len,vecLen)|；
                //   steer = (motion + vecToTarget) * (len/mag)，再叠加 (0, 0.045, 0)。
                // ⚠️ 目标由玩家手动锁定（固定），因此 必中 应始终追击：原版用 cos>0.5 作为
                // “不在正前方就弃靶重新选”的自主索敌闸门；这里目标是固定的，若沿用该闸门，
                // 目标一旦跑到箭头侧后方就会丢锁 → 追踪失效。故保留原版公式，但 cos 仅决定
                // 是“向前混合转向”还是“直接掉头扑向目标”，绝不弃锁。
                Vec3 hitPos = target.position().add(0, target.getBbHeight() * 0.6, 0);
                Vec3 to = hitPos.subtract(arrow.position());
                Vec3 motion = arrow.getDeltaMovement();
                double len = motion.length();
                if (len < 1.0e-6) continue;
                Vec3 vecToTarget = to.scale(0.5);
                double vecLen = vecToTarget.length();
                if (vecLen < 1.0e-6) continue;
                double cos = motion.dot(vecToTarget) / (len * vecLen);
                // 本特性“必中”的箭矢，标记后命中时移除
                arrow.getPersistentData().putBoolean(KEY_PIERCE_ARROW, true);
                double mag = Math.sqrt(len * len + vecLen * vecLen);
                double scale = len / mag;
                if (cos > 0.5) {
                    // 目标在正前方：原版混合转向（正常冲刺）
                    Vec3 steer = motion.scale(scale).add(vecToTarget.scale(scale)).add(0, 0.045, 0);
                    arrow.setDeltaMovement(steer);
                } else {
                    // 目标在侧后方：直接以当前速度掉头扑向目标，保证必中
                    Vec3 steer = to.normalize().scale(Math.max(len, 0.5));
                    arrow.setDeltaMovement(steer);
                }
            }
        }
    }

    /** 弹射物命中活体目标后，删除（discard）被本特性追踪的箭矢，避免其继续飞行/滞留 */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getEntity().level().isClientSide) return;
        Projectile projectile = event.getProjectile();
        if (!(projectile instanceof AbstractArrow arrow)) return;
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) return;
        if (!(hit.getEntity() instanceof LivingEntity)) return;
        // 被追踪的箭矢会打上标记；但为覆盖“发射后 2 tick 内即命中（还没标记）”的窗口，
        // 同时校验射手的武器是否带本特性。
        if (boolTag(arrow, KEY_PIERCE_ARROW)) { arrow.discard(); return; }
        if (arrow.getOwner() instanceof LivingEntity shooter) {
            if (hasFatePierce(rangedWeapon(shooter))) arrow.discard();
        }
    }

    private static boolean boolTag(AbstractArrow arrow, String key) {
        return arrow.getPersistentData().getBoolean(key);
    }

    private static ItemStack rangedWeapon(LivingEntity shooter) {
        ItemStack main = shooter.getMainHandItem();
        if (hasFatePierce(main)) return main;
        ItemStack off = shooter.getOffhandItem();
        if (hasFatePierce(off)) return off;
        return ItemStack.EMPTY;
    }

    private static LivingEntity findLock(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        List<LivingEntity> nearby = player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(LOCK_RANGE),
                e -> e != player && e.isAlive());
        LivingEntity best = null;
        double bestScore = LOCK_ANGLE_COS;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : nearby) {
            Vec3 to = e.getEyePosition().subtract(eye);
            double dist = to.length();
            if (dist > LOCK_RANGE || dist < 0.5) continue;
            Vec3 dir = to.normalize();
            double dot = dir.dot(look);
            // 视线内：对齐度高优先；否则最近候选
            if (dot >= bestScore) {
                if (clearSight(player.level(), eye, e)) {
                    bestScore = dot;
                    best = e;
                }
            } else if (best == null && dist < bestDist) {
                // 无视线目标时的最近目标兜底（本简化先取最近）
                best = e;
                bestDist = dist;
            }
        }
        return best;
    }

    private static boolean clearSight(net.minecraft.world.level.Level level, Vec3 from, LivingEntity target) {
        net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(
                from, target.getEyePosition(),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, null));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return hit.getLocation().distanceToSqr(from) >= from.distanceToSqr(target.getEyePosition()) - 1.0;
        }
        return true;
    }

    private static UUID readLock(Player player) {
        String s = player.getPersistentData().getString(KEY_LOCK);
        if (s == null || s.isEmpty()) return null;
        try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }

    private static boolean hasFatePierce(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ToolStack tool = ToolHelper.getToolStack(stack);
        return tool != null && tool.getModifierLevel(FatePierceModifier.ID) > 0;
    }
}
