package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.VoidTouchModifier;
import com.mofengbaizhi.tinkersnewlife.integration.goety_ladder.GoetyLadderCompat;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * <b>虚空抚摸</b>（虚空金属工具自带，无等级）—— 命中给目标叠 <b>虚蚀</b>。
 *
 * <h2>规格（用户口径）</h2>
 * <ul>
 *   <li>用虚空金属工具<b>近战或远程</b>命中 ⇒ 目标获得 <b>虚蚀</b>（{@code goety_ladder:void_wane} ✓）<b>3 秒</b>（60 tick ✓）；</li>
 *   <li>目标<b>身上已有虚蚀</b> ⇒ 这次把等级 <b>+1</b>，<b>最高 5 级</b>（amplifier 0→4 ✓）。</li>
 * </ul>
 *
 * <h2>为什么用 {@link LivingHurtEvent} + {@code ToolHelper#getCombatToolWith}</h2>
 * <ul>
 *   <li>{@code LivingHurtEvent} 是"伤害已确定、还没结算完"的位置 ⇒ 近战与弹射物都会经过 ✓；</li>
 *   <li>"这一发到底是哪把工具打的"这件事本模组已经有现成解析 ✓
 *       ——{@code getCombatToolWith} 覆盖：主手近战 ✓、弹射武器（弓/弩）✓、<b>悠悠球</b>（手里已经没有工具，
 *       从球实体里取回工具栈 ✓）✓；它还会顺带校验"工具带指定强化" ✓；</li>
 *   <li>⚠ 它<b>可能</b>返回一把不带该强化的工具（实现里"两者皆无时返回主工具"✓）⇒ 这里必须再自查一次等级 ✓。</li>
 * </ul>
 *
 * <p>阶梯没装（拿不到 {@code void_wane}）⇒ 整段静默跳过 ✓，不留副作用 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VoidTouchHandler {

    /** 持续时间：3 秒 ✓（用户口径） */
    private static final int DURATION_TICKS = 60;
    /** 最高 5 级 ⇒ amplifier 上限 4 ✓ */
    private static final int MAX_AMPLIFIER = 4;

    private VoidTouchHandler() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;

        // 近战 / 远程两条路：弹射物走 projectile 版（能解析到弹射武器与悠悠球 ✓），其余走近战版 ✓
        var direct = event.getSource().getDirectEntity();
        var tool = direct instanceof Projectile projectile
                ? ToolHelper.getCombatToolWith(projectile, attacker, VoidTouchModifier.ID)
                : ToolHelper.getCombatToolWith(event.getSource(), attacker, VoidTouchModifier.ID);
        if (tool == null) return;
        if (ToolHelper.getActiveModifierLevel(tool, VoidTouchModifier.ID) <= 0) return;

        applyWane(target);
    }

    /**
     * 给目标挂/升虚蚀：第一次 = 1 级，之后每命中一次 +1 级，封顶 5 级 ✓。
     * <p>用 {@code addEffect} 覆盖旧实例 ⇒ 持续时间也被刷新成 3 秒 ✓（"持续 3 秒"按每次命中重算 ✓）。
     */
    private static void applyWane(LivingEntity target) {
        MobEffect wane = GoetyLadderCompat.effect(GoetyLadderCompat.VOID_WANE);
        if (wane == null) return;                        // 没装阶梯 ⇒ 静默跳过 ✓
        @Nullable MobEffectInstance old = target.getEffect(wane);
        int amplifier = old == null ? 0 : Math.min(MAX_AMPLIFIER, old.getAmplifier() + 1);
        target.addEffect(new MobEffectInstance(wane, DURATION_TICKS, amplifier, false, true, true));
    }
}
