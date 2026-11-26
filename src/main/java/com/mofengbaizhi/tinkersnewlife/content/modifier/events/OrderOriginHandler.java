package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.MagicConductionModifier;
import com.mofengbaizhi.tinkersnewlife.content.modifier.OrderOriginModifier;
import net.minecraft.core.Holder;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 特性「<b>秩序之初</b>」结算器（铁魔法联动，见 {@link OrderOriginModifier}）。
 *
 * <p>做法：在 {@code LivingHurtEvent}（{@link EventPriority#LOWEST}）里，如果受伤者身上带着秩序之初，
 * 且这次伤害<b>不是物理伤害</b>（魔法 / 真实 / 无视护甲那类）→
 * <b>取消原始伤害</b>，再用原版<b>物理</b>类型重新施加一次同等数值 ✓
 * （保留攻击者实体，所以击杀归属、击退方向都不丢 ✓）。
 * 这样这次伤害就会正常经过护甲值 / 韧性 / 保护附魔 / 饰品与属性的减免 ✓。
 *
 * <p>判定"不是物理"用的是现成的两把尺子：{@link DamageTypeTags#BYPASSES_ARMOR}（无视护甲）
 * 与 {@link MagicConductionModifier#isMagicDamage}（我们在「导魔」里维护的魔法判定，
 * 含原版女巫抗性标签、{@code forge:is_magic} 标签、名字含 magic、以及铁魔法/诡厄命名空间兜底 ✓）。
 * 两者都不命中 → 本来就是物理伤害 → <b>原样放行</b> ✓（这一步同时避免了"转换后的物理伤害又被转换一次"的循环 ✗）。
 *
 * <p>⚠ 口径（与用户确认）：只覆盖<b>走伤害事件的伤害</b> ✓；
 * "直接改血"（绕过伤害事件写血量）不拦 ✗ —— 那要 mixin {@code LivingEntity#setHealth}，
 * 会与死亡流程 / 治疗 / 指令纠缠，风险大于收益 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OrderOriginHandler {

    private OrderOriginHandler() {
    }

    /** 转换中标记（防止我们施加的物理伤害再被处理一遍） */
    private static final ThreadLocal<Boolean> CONVERTING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (Boolean.TRUE.equals(CONVERTING.get())) return;

        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        if (!OrderOriginModifier.wornBy(victim)) return;

        DamageSource source = event.getSource();
        if (!needsConversion(source)) return;

        float amount = event.getAmount();
        if (amount <= 0.0F) return;

        event.setCanceled(true);
        CONVERTING.set(Boolean.TRUE);
        try {
            victim.invulnerableTime = 0;
            victim.hurt(physicalSource(victim.level(), source), amount);
            victim.invulnerableTime = 20;                        // 原版命中后的无敌时间 ✓
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.debug("[秩序之初] 转换失败（已忽略）: {}", t.toString());
        } finally {
            CONVERTING.set(Boolean.FALSE);
        }
    }

    /** 这次伤害需不需要"重算成物理"（已经是物理的就不动 ✓） */
    private static boolean needsConversion(DamageSource source) {
        if (source == null) return false;
        try {
            if (source.is(DamageTypeTags.BYPASSES_ARMOR)) return true;
        } catch (Throwable ignored) {
        }
        return MagicConductionModifier.isMagicDamage(source);
    }

    /** 用原版的物理类型重建伤害源（尽量保留来源归属：玩家攻击 / 生物攻击 / 通用） */
    private static DamageSource physicalSource(Level level, DamageSource original) {
        Entity causing = original.getEntity();
        Entity direct = original.getDirectEntity();
        Holder<DamageType> holder;
        if (causing instanceof Player player) {
            holder = level.damageSources().playerAttack(player).typeHolder();
        } else if (causing instanceof LivingEntity living) {
            holder = level.damageSources().mobAttack(living).typeHolder();
        } else {
            holder = level.damageSources().generic().typeHolder();
        }
        return new DamageSource(holder, direct, causing);
    }
}
