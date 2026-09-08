package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.GoetyBridge;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.hook.build.ConditionalStatModifierHook;
import slimeknights.tconstruct.library.tools.capability.EntityModifierCapability;
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

        // 挂上弓的 modifiers → 命中时 PROJECTILE_HIT 词条（龙炎等）经 EntityModifierCapability 触发
        EntityModifierCapability.getCapability(arrow).addModifiers(tool.getModifiers());
    }

    /** 仆从主手/副手第一把可用匠魂弓 */
    private static ItemStack findTinkerBow(Mob mob) {
        if (isUsableTinkerBow(mob.getMainHandItem())) return mob.getMainHandItem();
        if (isUsableTinkerBow(mob.getOffhandItem())) return mob.getOffhandItem();
        return ItemStack.EMPTY;
    }

    /**
     * 仿 goety {@code CreatureBowAttackGoal} 的射击节奏，但以匠魂弓为触发条件：
     * 目标在射程内且看得见 → 站定（保留轻微走位）按节拍射击；太远则靠近。
     * 射击走 {@link RangedAttackMob#performRangedAttack}（动态分发到 goety 原生实现，public）。
     */
    public static class ServantBowRangedGoal extends Goal {
        private static final double SPEED_MODIFIER = 1.0D;
        private static final float ATTACK_RADIUS = 15.0F;
        private static final float ATTACK_RADIUS_SQR = ATTACK_RADIUS * ATTACK_RADIUS;
        private static final int ATTACK_INTERVAL = 25;

        private final Mob mob;
        private int attackTime = ATTACK_INTERVAL;
        private int seeTime;

        public ServantBowRangedGoal(Mob mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        private boolean isHoldingTinkerBow() {
            return hasUsableTinkerBow(mob);
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
            mob.stopUsingItem();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null) return;

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
                // 太远：靠近
                mob.getNavigation().moveTo(target, SPEED_MODIFIER);
                mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
                return;
            }

            // 射程内：站定瞄准，按节拍射击
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

            if (!canSee) {
                return; // 看不见不射
            }
            if (this.attackTime > 0) {
                --this.attackTime;
                return;
            }
            // 开火：走 RangedAttackMob 接口 → goety 原生射击（vanilla 箭 + goety 伤害公式）
            if (mob instanceof RangedAttackMob ranged) {
                ranged.performRangedAttack(target, 1.0F);
            }
            this.attackTime = ATTACK_INTERVAL + mob.getRandom().nextInt(10);
        }
    }
}
