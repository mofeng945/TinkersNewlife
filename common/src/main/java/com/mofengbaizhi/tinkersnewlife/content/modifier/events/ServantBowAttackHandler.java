package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.GoetyBridge;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.hook.build.ConditionalStatModifierHook;
import slimeknights.tconstruct.library.tools.capability.EntityModifierCapability;
import slimeknights.tconstruct.library.tools.item.ranged.ModifiableCrossbowItem;
import slimeknights.tconstruct.library.tools.item.ranged.ModifiableLauncherItem;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

import java.util.EnumSet;

/**
 * 诡厄骷髅仆从使用匠魂远程发射器（弓/弩，阶段 A+B+C）。
 * <ul>
 *   <li><b>识别</b>：诡厄仆从（{@code Owned}，经 GoetyBridge）且是远程型
 *       （原版接口 {@link RangedAttackMob}——骷髅系仆从都实现它，僵尸仆从不是）</li>
 *   <li><b>goety 原生 AI 的盲区</b>：{@code AbstractSkeletonServant.reassessWeaponGoal()} 与
 *       {@code CreatureBowAttackGoal.isHoldingBow()} 都只认 {@code instanceof BowItem}；
 *       匠魂弓/弩（{@link ModifiableLauncherItem} 体系，弓 {@code ModifiableBowItem}、
 *       弩 {@code ModifiableCrossbowItem}）不是 BowItem → goety 会挂 meleeGoal（拿弓冲上去近战）。</li>
 *   <li><b>本 handler 接管</b>：每 tick 对「手持可用匠魂发射器的诡厄远程仆从」：
 *       移除 goety 的 meleeGoal（反射字段 {@code meleeGoal}，防止冲脸），
 *       并确保我们的 {@link ServantBowRangedGoal}（仿 CreatureBowAttackGoal 的射击节奏）存在。</li>
 *   <li><b>发射</b>：经由原版接口 {@link RangedAttackMob#performRangedAttack} 调用 goety 原生
 *       射击实现（public），产出 vanilla 箭——基础箭矢先行。</li>
 *   <li><b>箭矢词条/面板伤害（C）</b>：goety 射出的箭加入世界时（{@link EntityJoinLevelEvent}）：
 *       对 owner 是持匠魂发射器仆从的 {@link AbstractArrow}，照 tconstruct 自射箭的做法——
 *       用 {@link EntityModifierCapability} 挂上武器的 modifiers（PROJECTILE_HIT 词条随命中触发，
 *       如龙炎等），并把面板伤害 {@link ToolStats#PROJECTILE_DAMAGE}（经
 *       {@link ConditionalStatModifierHook} 修正）写进箭的 baseDamage。</li>
 * </ul>
 * 软依赖：goety 未装时 isGoetyServant 恒 false，本类自然失效；无任何 goety import。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServantBowAttackHandler {

    /** 主/副手任一是<b>可用</b>（未损坏）的匠魂发射器（弓/弩） */
    private static boolean hasUsableTinkerBow(Mob mob) {
        return isUsableTinkerBow(mob.getMainHandItem()) || isUsableTinkerBow(mob.getOffhandItem());
    }

    /** 主/副手任一是<b>可用</b>匠魂弩 */
    private static boolean isCrossbowStack(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof slimeknights.tconstruct.library.tools.item.ranged.ModifiableCrossbowItem;
    }

    private static boolean isUsableTinkerBow(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof ModifiableLauncherItem)) return false;
        ToolStack tool = ToolStack.from(stack);
        return tool != null && !tool.isBroken();
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide) return;
        if (!(entity instanceof Mob mob)) return;
        // 快筛：只有远程型仆从（骷髅系）才可能被我们装备弓（阶段 A 已限），此处兜底
        if (!(mob instanceof RangedAttackMob)) return;
        if (!hasUsableTinkerBow(mob)) return;
        if (!GoetyBridge.isGoetyServant(mob)) return;

        // goety 的 meleeGoal 会把它当近战（拿弓冲锋），移除它
        GoetyBridge.removeServantMeleeGoal(mob);

        // 确保我们的远程射击 goal 在（避免重复 addGoal 累积）
        GoalSelector selector = mob.goalSelector;
        for (WrappedGoal wrapped : selector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof ServantBowRangedGoal) return;
        }
        selector.addGoal(4, new ServantBowRangedGoal(mob));
    }

    /** 阶段 C：仆从射出的箭挂上弓的词条 + 面板伤害 */
    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        try {
            onJoinLevelInner(event);
        } catch (Throwable t) {
            // 事件总线日志在本环境有 log4j 冲突：任何监听器异常都会升级成 LinkageError 崩溃，
            // 这里兜底吞掉并打印，保证箭加入世界不炸
            TinkersNewlife.LOGGER.error("[仆从弓] 箭加入世界处理异常", t);
        }
    }

    private static void onJoinLevelInner(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        // 只处理诡厄远程仆从射出的箭（owner=仆从）
        Entity owner = arrow.getOwner();
        if (!(owner instanceof Mob mob)) return;
        if (!GoetyBridge.isGoetyServant(mob)) return;
        ItemStack bow = findTinkerBow(mob);
        if (bow.isEmpty()) return;
        ToolStack tool = ToolStack.from(bow);
        if (tool == null || tool.isBroken()) return;

        // 照 tconstruct 自射箭（ModifiableBowItem.releaseUsing）：箭的 baseDamage = 弓面板伤害（含词条修正）
        float raw = tool.getStats().get(ToolStats.PROJECTILE_DAMAGE);
        float modified = ConditionalStatModifierHook.getModifiedStat(tool, mob, ToolStats.PROJECTILE_DAMAGE,
                (float) (arrow.getBaseDamage() - 2.0D) + raw);
        arrow.setBaseDamage(modified);

        // ⚠️ 2026-09：不再给箭挂 EntityModifierCapability（tconstruct 词条能力）。
        // 崩因排查：仆从箭命中 → ProjectileImpactEvent → 某监听器抛异常 → EventBus 记日志触发
        // log4j MessageSupplier 加载冲突（revelationfix mixin 影响）→ LinkageError 崩溃。
        // 给箭挂词条会让 tconstruct 命中遍历箭上 modifiers 调 PROJECTILE_HIT hook，对 owner=仆从
        // 的箭（本环境）不稳定。本 mod 自己的命中词条（龙炎/龙霆/死钢等）走各自 ProjectileImpact
        // 监听器、看攻击者手上弓的词条，不依赖箭上挂载——去掉挂载仅损失 tconstruct 原版弹射词条
        // （如穿透），换取稳定。
        // EntityModifierCapability.getCapability(arrow).addModifiers(tool.getModifiers());
    }

    /** 仆从主手/副手第一把可用匠魂发射器（弓/弩） */
    private static ItemStack findTinkerBow(Mob mob) {
        if (isUsableTinkerBow(mob.getMainHandItem())) return mob.getMainHandItem();
        if (isUsableTinkerBow(mob.getOffhandItem())) return mob.getOffhandItem();
        return ItemStack.EMPTY;
    }

    /**
     * 仆从远程 AI（自研，绕开 goety 只认 vanilla 弓的限制）：
     * <ul>
     *   <li><b>弓</b>：仿原版骷髅 {@code RangedBowAttackGoal} —— 过远靠近、射程内横向走位
     *       （strafe）边移动边射；拉弓（{@code startUsingItem}，客户端 UseAnim.BOW 有拉弓姿势）
     *       蓄力满 20 tick 后松手发射。</li>
     *   <li><b>弩</b>：装填状态机（未装填→装填中→已装填→发射），装填有节奏延迟，
     *       发射速度更快、散布更小。</li>
     *   <li><b>发射</b>：自建 vanilla 箭（owner=仆从）+ 目标预测瞄准（箭速高、散布低），
     *       词条/面板伤害由 {@link #onJoinLevel} 统一挂载。</li>
     * </ul>
     */
    public static class ServantBowRangedGoal extends Goal {
        private static final double SPEED_MODIFIER = 1.0D;
        private static final float ATTACK_RADIUS = 15.0F;
        private static final float ATTACK_RADIUS_SQR = ATTACK_RADIUS * ATTACK_RADIUS;
        private static final int ATTACK_INTERVAL = 20;

        private final Mob mob;
        private int attackTime = ATTACK_INTERVAL;
        private int seeTime;
        private int strafingTime;
        private boolean strafingClockwise;
        private boolean strafingBackwards;

        // 弩装填状态
        private int chargeTicks;
        private boolean charging;      // true=装填中（startUsingItem 已调用，等待装填完成）

        public ServantBowRangedGoal(Mob mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        private boolean isHoldingTinkerBow() {
            return hasUsableTinkerBow(mob);
        }

        private boolean isCrossbow() {
            return isCrossbowStack(mob.getMainHandItem()) || isCrossbowStack(mob.getOffhandItem());
        }

        @Override
        public boolean canUse() {
            return mob.getTarget() != null && isHoldingTinkerBow();
        }

        @Override
        public boolean canContinueToUse() {
            return (canUse() || !mob.getNavigation().isDone()) && isHoldingTinkerBow();
        }

        @Override
        public void start() {
            super.start();
            mob.setAggressive(true);
        }

        @Override
        public void stop() {
            super.stop();
            mob.setAggressive(false);
            this.seeTime = 0;
            this.attackTime = ATTACK_INTERVAL;
            this.charging = false;
            this.chargeTicks = 0;
            if (mob.isUsingItem()) {
                mob.stopUsingItem();
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null) return;
            if (isCrossbow()) {
                tickCrossbow(target);
            } else {
                tickBow(target);
            }
        }

        // =================================================================
        //  弓：原版骷髅式移动 + 拉弓蓄力 + 自建箭发射
        // =================================================================
        private void tickBow(LivingEntity target) {
            double distSq = mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
            boolean canSee = mob.getSensing().hasLineOfSight(target);
            boolean wasSeeing = this.seeTime > 0;
            if (canSee != wasSeeing) {
                this.seeTime = 0;
            }
            if (canSee) {
                ++this.seeTime;
            } else {
                --this.seeTime;
            }

            // 移动：过远靠近；射程内且看清 → 站定横移（strafe）保持距离，边移动边射
            if (distSq > ATTACK_RADIUS_SQR || this.seeTime < 20) {
                mob.getNavigation().moveTo(target, SPEED_MODIFIER);
                this.strafingTime = -1;
            } else {
                mob.getNavigation().stop();
                ++this.strafingTime;
            }
            if (this.strafingTime >= 20) {
                if (mob.getRandom().nextFloat() < 0.3D) {
                    this.strafingClockwise = !this.strafingClockwise;
                }
                if (mob.getRandom().nextFloat() < 0.3D) {
                    this.strafingBackwards = !this.strafingBackwards;
                }
                this.strafingTime = 0;
            }
            if (this.strafingTime > -1) {
                if (distSq > ATTACK_RADIUS_SQR * 0.75F) {
                    this.strafingBackwards = false;
                } else if (distSq < ATTACK_RADIUS_SQR * 0.25F) {
                    this.strafingBackwards = true;
                }
                float forward = this.strafingBackwards ? -0.5F : 0.5F;
                float right = this.strafingClockwise ? 0.5F : -0.5F;
                mob.getMoveControl().strafe(forward, right);
            }

            // 瞄准目标
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

            // 射击节奏：拉弓蓄力 ≥20 tick → 松手发射
            if (mob.isUsingItem()) {
                if (!canSee && this.seeTime < -60) {
                    mob.stopUsingItem();
                } else if (canSee) {
                    int usingTicks = mob.getTicksUsingItem();
                    if (usingTicks >= 20) {
                        mob.stopUsingItem();
                        fireProjectile(target, false);
                        this.attackTime = ATTACK_INTERVAL + mob.getRandom().nextInt(10);
                    }
                }
                return;
            }
            if (--this.attackTime <= 0 && this.seeTime >= -60) {
                // 开始拉弓（客户端 UseAnim.BOW 呈现拉弓姿势）
                InteractionHand hand = ProjectileUtil.getWeaponHoldingHand(mob,
                        item -> item instanceof ModifiableLauncherItem);
                mob.startUsingItem(hand);
            }
        }

        // =================================================================
        //  弩：装填状态机 + 发射（箭速更高、散布更小）
        //  与弓一致：过远靠近、射程内横向走位（strafe），装填/发射中保持机动
        // =================================================================
        private void tickCrossbow(LivingEntity target) {
            double distSq = mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
            boolean canSee = mob.getSensing().hasLineOfSight(target);
            boolean wasSeeing = this.seeTime > 0;
            if (canSee != wasSeeing) {
                this.seeTime = 0;
            }
            if (canSee) {
                ++this.seeTime;
            } else {
                --this.seeTime;
            }

            boolean inRange = distSq <= ATTACK_RADIUS_SQR;
            if (!inRange) {
                mob.getNavigation().moveTo(target, SPEED_MODIFIER);
                this.strafingTime = -1;
                return;
            }

            // 射程内：站定横移走位（同弓），保持机动边装填边射
            mob.getNavigation().stop();
            ++this.strafingTime;
            if (this.strafingTime >= 20) {
                if (mob.getRandom().nextFloat() < 0.3D) {
                    this.strafingClockwise = !this.strafingClockwise;
                }
                if (mob.getRandom().nextFloat() < 0.3D) {
                    this.strafingBackwards = !this.strafingBackwards;
                }
                this.strafingTime = 0;
            }
            if (this.strafingTime > -1) {
                if (distSq > ATTACK_RADIUS_SQR * 0.75F) {
                    this.strafingBackwards = false;
                } else if (distSq < ATTACK_RADIUS_SQR * 0.25F) {
                    this.strafingBackwards = true;
                }
                float forward = this.strafingBackwards ? -0.5F : 0.5F;
                float right = this.strafingClockwise ? 0.5F : -0.5F;
                mob.getMoveControl().strafe(forward, right);
            }
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (!canSee) return;

            if (!this.charging) {
                // 未装填：冷却结束且看清目标 → 开始装填
                if (--this.attackTime <= 0 && this.seeTime >= 5) {
                    this.charging = true;
                    this.chargeTicks = 0;
                    InteractionHand hand = ProjectileUtil.getWeaponHoldingHand(mob,
                            item -> item instanceof ModifiableLauncherItem);
                    mob.startUsingItem(hand);   // 装填姿态（开始 use）
                }
                return;
            }
            // 装填中：走完装填时长（约 25 tick）→ 装填完成
            ++this.chargeTicks;
            if (this.chargeTicks < 25) return;
            this.charging = false;
            if (mob.isUsingItem()) {
                mob.stopUsingItem();
            }
            // 发射（弩：箭速高、散布小）
            fireProjectile(target, true);
            this.attackTime = ATTACK_INTERVAL + 10 + mob.getRandom().nextInt(10);
        }

        /**
         * 自建箭并发射：owner=仆从（后续 onJoinLevel 自动挂词条/面板伤害）。
         * 目标预测：瞄目标中心 + 简单提前量，箭速高 → 命中率高。
         */
        private void fireProjectile(LivingEntity target, boolean crossbow) {
            net.minecraft.world.entity.projectile.Arrow arrow =
                    new net.minecraft.world.entity.projectile.Arrow(mob.level(), mob);
            arrow.setPos(mob.getX(), mob.getEyeY() - 0.1D, mob.getZ());
            arrow.setOwner(mob);

            double dx = target.getX() - mob.getX();
            double dy = (target.getY() + target.getEyeHeight() * 0.5D) - mob.getEyeY();
            double dz = target.getZ() - mob.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            // 提前量：按箭飞行时间预测目标位移（半补偿，避免过调）
            float speed = crossbow ? 3.2F : 2.4F;
            double flight = dist / speed;
            double px = dx + target.getDeltaMovement().x * flight * 0.5D;
            double py = dy + target.getDeltaMovement().y * flight * 0.3D;
            double pz = dz + target.getDeltaMovement().z * flight * 0.5D;
            // 散布：弩更准
            float inaccuracy = crossbow ? 0.4F : 1.1F;
            arrow.shoot(px, py, pz, speed, inaccuracy);
            mob.level().addFreshEntity(arrow);

            mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                    crossbow ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.ARROW_SHOOT,
                    SoundSource.HOSTILE, 1.0F, 0.8F + mob.getRandom().nextFloat() * 0.4F);
        }
    }
}
