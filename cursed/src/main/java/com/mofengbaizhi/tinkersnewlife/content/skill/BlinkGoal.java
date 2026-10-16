package com.mofengbaizhi.tinkersnewlife.content.skill;

import java.util.concurrent.ConcurrentHashMap;

import java.util.UUID;

import java.util.Map;

import net.minecraft.core.BlockPos;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.LivingEntity;

/**
 * TNL1175 瞬身（法术编号 1）。
 *
 * <p>Boss 把视线锁在 20 格以外的目标身上，原地站定 1.5 秒，然后
 * 毫无预兆地消失：出现在目标周围三格内的随机位置（连线上撞到墙就停在墙前），
 * 身后留下一条残影，并再定身 0.2 秒。
 * 传送本身仍由内部类 {@code BlinkHandler} 完成，这个 Goal 只负责推进相位。
 */
public final class BlinkGoal extends MomoSpellGoal {

    private static final int LOCK_TICKS = 30;
    private static final int AFTERCAST_TICKS = 4;
    private static final int COOLDOWN_TICKS = 200;
    private static final double RANGE = 20.0D;
    private static final int TOTAL_TICKS = LOCK_TICKS + AFTERCAST_TICKS;

    private boolean jumped;

    public BlinkGoal(KtuluMomo momo) {
        super(momo, 1, COOLDOWN_TICKS, TOTAL_TICKS);
    }

    @Override
    protected int cooldownLeft() {
        return (int) Math.max(0L, this.momo.tnl$blinkCoolUntil() - this.momo.level().getGameTime());
    }

    @Override
    public boolean canUse() {
        // 触发判断发生在定身之前 ⇒ 这里读实体自己的目标
        if (!MomoSkills.BLINK) {
            return false;
        }
        LivingEntity target = this.momo.getTarget();
        if (target == null || !target.isAlive() || this.momo.distanceTo(target) < RANGE) {
            return false;
        }
        return this.ready();
    }

    @Override
    protected void onStart() {
        ServerLevel level = (ServerLevel) this.momo.level();
        this.jumped = false;
        LivingEntity target = this.stun.target();
        if (target != null) {
            this.momo.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
        TinkersNewlife.LOGGER.warn("TNL1175 Momo locks onto {} and starts the Blink wind up",
                target == null ? "?" : target.getName().getString());
    }

    @Override
    protected boolean onTick() {
        ServerLevel level = (ServerLevel) this.momo.level();
        LivingEntity target = this.stun.target();
        if (!this.jumped && this.momo.tnl$castTicks() <= AFTERCAST_TICKS) {
            this.jumped = true;
            if (target != null && target.isAlive()) {
                BlinkHandler.teleportBehind(level, this.momo, target);
            }
        }
        BlinkHandler.tickGhostLine(level, this.momo, level.getGameTime());   // TNL1211 技能自己驱动残影
        this.momo.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        this.momo.hurtMarked = true;
        boolean tnl$trailDone = !BlinkHandler.hasTrail(this.momo.getUUID());
        return this.momo.tnl$castTicks() <= 0 && tnl$trailDone;
    }

    @Override
    protected void onStop() {
        long now = this.momo.level().getGameTime();
        this.momo.tnl$setBlinkCoolUntil(now + COOLDOWN_TICKS);
        // TNL1227 用户："瞬身后后摇状态标志没有取消"。收尾时把本技能的残影队列也清掉，
        // 免得它成为"状态残留"：技能已经结束，队列不该还挂着这个实体。
        BlinkHandler.clearTrail(this.momo.getUUID());
        this.momo.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        if (this.momo.getNavigation() != null) {
            this.momo.getNavigation().stop();
        }
    }
    // ===== TNL1211 瞬身的实现整体收进技能类（★原 BlinkHandler 原样成为内部类 ✓）=====
static final class BlinkHandler {   // TNL1211 收编自原 BlinkHandler.java

    private static final double AROUND = 3.0D;       // TNL1121 a random spot within three blocks

    /** TNL1121 传送后的后摇窗口，客户端用它显示残影 */

    private BlinkHandler() {
    }



    /**
     * TNL1184 瞬身残影：每个服务端 tick 放四条。它已经从旧的
     * 全服扫描里搬出来，现在由瞬身技能自己驱动。
     */
    public static void tickGhostLine(ServerLevel level, KtuluMomo momo, long now) {
        // TNL1201 队列是静态的，所以加个上限：若墨默在残影没放完时被移除，
        // 它那条记录就会永远留在那里。
        if (LINE.size() > 8) {
            LINE.clear();
        }
        UUID id = momo.getUUID();
        Line line = LINE.get(id);
        if (line != null) {
            if (line.sent >= GHOSTS) {
                LINE.remove(id);
            } else if (now >= line.nextTick) {
                // TNL1134 用户："墨默没跟上残影速度" —— 改成每 tick 放四条
                for (int k = 0; k < 4 && line.sent < GHOSTS; k++) {
                double t = (double) line.sent / (GHOSTS - 1.0D);
                double f = 1.0D - Math.pow(1.0D - t, 2.2D);      // denser toward the arrival point
                Vec3 gp = line.origin.add(line.dest.subtract(line.origin).scale(f));
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                        gp.x, gp.y + 0.6D, gp.z, 1, 0.2D, 0.3D, 0.2D, 0.02D);
                TinkersNewlife.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> momo),
                        new com.mofengbaizhi.tinkersnewlife.network.tools.PacketThrustGhost(
                                momo.getId(), gp.x, gp.y, gp.z,
                                (float) Math.toDegrees(Math.atan2(
                                        line.dest.z - line.origin.z, line.dest.x - line.origin.x)), 0.0F));
                    line.sent++;
                }
                line.nextTick = now + 1;
            }
        }
    }

    /**
     * TNL1124 沿连线采样；若中途有方块阻挡，返回撞墙前的最后一个空点。
     * 整条线都通时返回 null。
     */
    private static Vec3 tnl$clipLine(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        double len = delta.length();
        if (len < 1.0E-4D) {
            return null;
        }
        int steps = (int) Math.ceil(len / 0.25D);
        Vec3 lastFree = null;
        for (int i = 1; i <= steps; i++) {
            Vec3 p = from.add(delta.scale((double) i / steps));
            BlockPos bp = BlockPos.containing(p);
            if (!level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()) {
                return lastFree;              // stop just before the wall
            }
            lastFree = p;
        }
        return null;
    }

    /** 出现在目标周围三格内，面朝它，毫无预兆。*/
    public static void teleportBehind(ServerLevel level, KtuluMomo momo, LivingEntity target) {
        long now = level.getGameTime();
        // TNL1121 目标三格内的随机一点，周围任意方向
        Vec3 origin = momo.position();
        Vec3 dest = null;
        for (int attempt = 0; attempt < 12 && dest == null; attempt++) {
            double ang = level.random.nextDouble() * Math.PI * 2.0D;
            double rad = 1.0D + level.random.nextDouble() * (AROUND - 1.0D);
            Vec3 cand = target.position().add(Math.cos(ang) * rad, 0.0D, Math.sin(ang) * rad);
            BlockPos bp = BlockPos.containing(cand);
            if (level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()
                    && level.getBlockState(bp.above()).getCollisionShape(level, bp.above()).isEmpty()) {
                dest = cand;
            }
        }
        if (dest == null) {
            dest = target.position();
        }
        // TNL1124 用户口径：不要取消，改成停在连线撞到的墙前
        Vec3 from = origin.add(0.0D, 1.0D, 0.0D);
        Vec3 to = dest.add(0.0D, 1.0D, 0.0D);
        Vec3 clipped = tnl$clipLine(level, from, to);
        if (clipped != null) {
            dest = clipped.subtract(0.0D, 1.0D, 0.0D);
            TinkersNewlife.LOGGER.warn("TNL1124 Blink stopped short at ({}, {}, {})",
                    String.format("%.1f", dest.x), String.format("%.1f", dest.y), String.format("%.1f", dest.z));
        }
        // TNL1123 用户口径：残影要铺满起点到落点这整条线
        // TNL1133 用户口径：24 条残影，**每 tick 放一条**，让线从起点往终点生长。
        // 早出生的就是靠起点那些，所以它们先淡出 ——
        // "起点最快消失"靠错开的出生时间自然就实现了。
        // TNL1135 用户："瞬移是瞬间的，但墨默还是会飞过去，残影比它快"。
        // 客户端会用几 tick 插值位置，所以等插值结束**之后**再开始放残影：
        // 这样残影永远在看得见的墨默身后，而不是跑到前面去。
        LINE.put(momo.getUUID(), new Line(origin, dest, 0, now + 4));
        momo.teleportTo(dest.x, dest.y, dest.z);
        momo.setDeltaMovement(Vec3.ZERO);
        momo.hurtMarked = true;
        // TNL1134 用户："瞬移之后掉 AI 卡住"。传送会让旧路径失效，
        // 所以清掉路径、重新锁定目标，然后再进短后摇。
        // TNL1164 用户口径：前摇/后摇**绝不清 AI** —— 会粘住
        // （传送到高处后它冻了两秒）。定身改成
        // 每 tick 清零速度，上面已经做了。
        if (momo.getNavigation() != null) {
            momo.getNavigation().stop();
        }
        momo.setTarget(target);
        momo.getLookControl().setLookAt(target, 30.0F, 30.0F);
        // TNL1164 用户口径：前摇/后摇**绝不清 AI** —— 会粘住
        // （传送到高处后它冻了两秒）。定身改成
        // 每 tick 清零速度，上面已经做了。
        TinkersNewlife.LOGGER.warn("TNL1120 Momo blinked behind {} to ({}, {}, {})",
                target.getName().getString(),
                String.format("%.1f", dest.x), String.format("%.1f", dest.y), String.format("%.1f", dest.z));
    }
    /** TNL1133 还在释放中的残影队列，每 tick 一条 */
    private static final class Line {
        final Vec3 origin;
        final Vec3 dest;
        int sent;
        long nextTick;

        Line(Vec3 origin, Vec3 dest, int sent, long nextTick) {
            this.origin = origin;
            this.dest = dest;
            this.sent = sent;
            this.nextTick = nextTick;
        }
    }

    private static final Map<UUID, Line> LINE = new ConcurrentHashMap<>();

        /** TNL1212 残影队列里还有没有这个实体（★技能靠它判断残影放完没有 ✓）*/
        /** TNL1227 收尾时清掉该实体的残影队列 */
        static void clearTrail(UUID id) {
            LINE.remove(id);
        }

        static boolean hasTrail(UUID id) {
            return LINE.containsKey(id);
        }

    /** TNL1137 硬兜底：不管怎么被冻住，都不允许超过这个时长。*/
    private static final int GHOSTS = 24;

}
}
